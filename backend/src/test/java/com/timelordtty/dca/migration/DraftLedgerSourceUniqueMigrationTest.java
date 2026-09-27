package com.timelordtty.dca.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * v0.8 草稿强幂等迁移脚本的可验证证据（不连接任何数据库）。
 *
 * <p>本机单元测试环境没有可用的 MySQL 实例，也不允许连接数据库，因此这里采用“等价可验证方案”：</p>
 * <ol>
 *   <li>直接校验迁移脚本文件的静态内容：唯一键定义、回退语句、只读预检、无静默删除；</li>
 *   <li>从脚本里解析出两个唯一键的真实列定义，用它们驱动一个内存唯一性判定模型，
 *       再断言“同一可见作用域去重、不同作用域互不冲突、NULL 不参与唯一性”等验收语义确实成立。
 *       这样一来，脚本一旦被改成缺少某一列或缺少某个唯一键，测试就会失败。</li>
 * </ol>
 */
class DraftLedgerSourceUniqueMigrationTest {

    private static final String MIGRATION_DIR = "sql/updatesql/20260927";
    private static final String PRECHECK_SCRIPT = MIGRATION_DIR + "/01_precheck_draft_ledger_source_duplicates.sql";
    private static final String NORMALIZE_SCRIPT = MIGRATION_DIR + "/02_normalize_blank_draft_ledger_source_ref.sql";
    private static final String UNIQUE_KEY_SCRIPT = MIGRATION_DIR + "/03_add_draft_ledger_source_unique_keys.sql";

    private static final String USER_SCOPE_KEY = "uk_draft_ledger_user_source";
    private static final String FAMILY_SCOPE_KEY = "uk_draft_ledger_family_source";
    private static final List<String> USER_SCOPE_COLUMNS = List.of("owner_user_id", "source_type", "source_ref");
    private static final List<String> FAMILY_SCOPE_COLUMNS = List.of("owner_family_id", "source_type", "source_ref");

    @Test
    void uniqueKeyScriptDeclaresBothScopeKeysOnTheExactColumns() throws IOException {
        String sql = readScript(UNIQUE_KEY_SCRIPT);

        Map<String, List<String>> uniqueKeys = parseUniqueKeys(sql);

        assertEquals(2, uniqueKeys.size(), "唯一键脚本必须只声明用户作用域与家庭作用域两个唯一键");
        assertEquals(USER_SCOPE_COLUMNS, uniqueKeys.get(USER_SCOPE_KEY));
        assertEquals(FAMILY_SCOPE_COLUMNS, uniqueKeys.get(FAMILY_SCOPE_KEY));
    }

    @Test
    void uniqueKeyScriptStaysReviewableAndRollbackable() throws IOException {
        String sql = readScript(UNIQUE_KEY_SCRIPT);
        String statements = withoutCommentLines(sql);

        assertTrue(statements.contains("ALTER TABLE draft_ledger_entry"),
                "唯一键必须通过增量 ALTER TABLE 迁移添加，不得改写已发布的历史建表脚本");
        // 回退说明以 SQL 注释形式给出，因此这里校验原始脚本而不是去掉注释后的语句。
        assertTrue(sql.contains("DROP INDEX " + FAMILY_SCOPE_KEY), "必须提供家庭作用域唯一键的回退语句");
        assertTrue(sql.contains("DROP INDEX " + USER_SCOPE_KEY), "必须提供用户作用域唯一键的回退语句");
        assertTrue(statements.contains("information_schema.statistics"), "必须提供索引是否生效的核对查询");
        assertFalse(statements.toUpperCase(Locale.ROOT).contains("DELETE"), "迁移不得删除任何草稿数据");
        assertFalse(statements.toUpperCase(Locale.ROOT).contains("DROP TABLE"), "迁移不得删除草稿表");
        assertFalse(statements.toUpperCase(Locale.ROOT).contains("TRUNCATE"), "迁移不得清空草稿表");
    }

    @Test
    void precheckScriptIsReadOnlyAndBlocksOnHistoricalDuplicates() throws IOException {
        String sql = readScript(PRECHECK_SCRIPT);
        String statements = withoutCommentLines(sql).toUpperCase(Locale.ROOT);

        assertTrue(statements.contains("HAVING COUNT(*) > 1"),
                "预检必须按作用域 + source_type + source_ref 找出历史重复草稿");
        assertTrue(statements.contains("GROUP BY OWNER_USER_ID, SOURCE_TYPE, SOURCE_REF"),
                "预检必须覆盖 owner_user_id + source_type + source_ref 分组");
        assertTrue(statements.contains("GROUP BY OWNER_FAMILY_ID, SOURCE_TYPE, SOURCE_REF"),
                "预检必须覆盖 owner_family_id + source_type + source_ref 分组");
        assertFalse(statements.contains("DELETE"), "预检必须是只读脚本，不得删除历史数据");
        assertFalse(statements.contains("UPDATE"), "预检必须是只读脚本，不得静默改写历史数据");
        assertFalse(statements.contains("DROP "), "预检必须是只读脚本，不得删除数据库对象");
    }

    @Test
    void normalizeScriptOnlyMapsBlankSourceRefToNull() throws IOException {
        String sql = readScript(NORMALIZE_SCRIPT);
        String statements = withoutCommentLines(sql);

        assertTrue(statements.contains("SET source_ref = NULL"), "空来源脚本只允许把 source_ref 归一化为 NULL");
        assertTrue(statements.contains("TRIM(source_ref) = ''"), "空来源脚本必须只命中空串或纯空白串");
        assertTrue(statements.contains("WHERE source_ref IS NOT NULL"),
                "空来源脚本不得触碰已经为 NULL 的行");
        assertFalse(statements.toUpperCase(Locale.ROOT).contains("DELETE"), "空来源脚本不得删除行");
        assertFalse(statements.contains("status ="), "空来源脚本不得改写草稿状态");
    }

    @Test
    void parsedUniqueKeysEnforceVisibleScopeIdempotencyWithoutMergingOtherOwners() throws IOException {
        Map<String, List<String>> uniqueKeys = parseUniqueKeys(readScript(UNIQUE_KEY_SCRIPT));

        // 同一个人 + 同来源：必须冲突（应用层 selectVisibleBySource 会对同一个人重放）。
        assertTrue(conflicts(uniqueKeys, row("10", "20", "HERMES_TEXT", "ref-1"), row("10", "20", "HERMES_TEXT", "ref-1")));
        // 同一个人、家庭为空 + 同来源：唯一键 1 仍然必须冲突，个人作用域不能因为家庭为 NULL 而失效。
        assertTrue(conflicts(uniqueKeys, row("10", null, "manual", "ref-1"), row("10", null, "manual", "ref-1")));
        // 同一个家庭的不同成员 + 同来源：必须冲突，与家庭可见性语义一致。
        assertTrue(conflicts(uniqueKeys, row("11", "20", "PAYMENT_NOTIFICATION", "fp-1"), row("12", "20", "PAYMENT_NOTIFICATION", "fp-1")));
        // 不同的人、不同的家庭 + 同来源：必须允许，不得误判为同一条草稿。
        assertFalse(conflicts(uniqueKeys, row("10", "20", "HERMES_TEXT", "ref-1"), row("11", "21", "HERMES_TEXT", "ref-1")));
        // 不同的人、都无家庭 + 同来源：必须允许。
        assertFalse(conflicts(uniqueKeys, row("10", null, "HERMES_TEXT", "ref-1"), row("11", null, "HERMES_TEXT", "ref-1")));
        // source_ref 为 NULL：保持旧的非幂等行为，允许存在多条。
        assertFalse(conflicts(uniqueKeys, row("10", "20", "HERMES_TEXT", null), row("10", "20", "HERMES_TEXT", null)));
        // 不同 source_type 但同 source_ref：必须允许。
        assertFalse(conflicts(uniqueKeys, row("10", "20", "HERMES_TEXT", "ref-1"), row("10", "20", "APP_FORM", "ref-1")));
    }

    /**
     * 解析脚本里的 ADD UNIQUE KEY 定义，得到“唯一键名 -> 列顺序”的映射。
     */
    private Map<String, List<String>> parseUniqueKeys(String sql) {
        Matcher matcher = Pattern
                .compile("ADD\\s+UNIQUE\\s+KEY\\s+(\\w+)\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE)
                .matcher(sql);
        Map<String, List<String>> uniqueKeys = new LinkedHashMap<>();
        while (matcher.find()) {
            List<String> columns = new ArrayList<>();
            for (String column : matcher.group(2).split(",")) {
                columns.add(column.trim().toLowerCase(Locale.ROOT));
            }
            uniqueKeys.put(matcher.group(1).toLowerCase(Locale.ROOT), columns);
        }
        return uniqueKeys;
    }

    /**
     * 内存唯一性判定模型：只要某个唯一键的所有列都非 NULL 且两行取值完全相同，判定为冲突。
     * 这与 MySQL 唯一索引语义一致（唯一键任一列为 NULL 时不参与唯一性判定）。
     */
    private boolean conflicts(Map<String, List<String>> uniqueKeys, Map<String, String> existing, Map<String, String> candidate) {
        for (List<String> keyColumns : uniqueKeys.values()) {
            boolean sameOnEveryColumn = true;
            for (String column : keyColumns) {
                String existingValue = existing.get(column);
                String candidateValue = candidate.get(column);
                if (existingValue == null || candidateValue == null || !existingValue.equals(candidateValue)) {
                    sameOnEveryColumn = false;
                    break;
                }
            }
            if (sameOnEveryColumn) {
                return true;
            }
        }
        return false;
    }

    private Map<String, String> row(String ownerUserId, String ownerFamilyId, String sourceType, String sourceRef) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("owner_user_id", ownerUserId);
        row.put("owner_family_id", ownerFamilyId);
        row.put("source_type", sourceType);
        row.put("source_ref", sourceRef);
        return row;
    }

    private String withoutCommentLines(String sql) {
        StringBuilder builder = new StringBuilder();
        for (String line : sql.split("\\R")) {
            if (!line.trim().startsWith("--")) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private String readScript(String relativePath) throws IOException {
        return Files.readString(locateScript(relativePath), StandardCharsets.UTF_8);
    }

    /**
     * 从测试工作目录向上定位迁移脚本文件本身。
     *
     * <p>以“文件存在”而不是“目录存在”作为判据，既兼容 mvn 在 backend/ 下执行与 IDE 以仓库根为工作目录，
     * 也不会被仓库里遗留的同名空目录误导。</p>
     */
    private Path locateScript(String relativePath) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new UncheckedIOException(new IOException("无法从测试工作目录定位迁移脚本: " + relativePath));
    }
}