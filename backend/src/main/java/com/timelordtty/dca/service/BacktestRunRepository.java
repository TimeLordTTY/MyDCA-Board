package com.timelordtty.dca.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.BacktestRunDTO;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** File backed, append-only history; no account database or migration is involved. */
@Repository
public class BacktestRunRepository {
    static final int MAX_RECORD_BYTES = 128_000;
    private final Path root;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public BacktestRunRepository() {
        this(Path.of(System.getProperty("backtest.history-root", "../data/backtest-history")));
    }

    BacktestRunRepository(Path root) { this.root = root.toAbsolutePath().normalize(); }

    public synchronized void save(BacktestRunDTO run) throws IOException {
        byte[] data = mapper.writeValueAsBytes(run);
        if (data.length > MAX_RECORD_BYTES) throw new IllegalArgumentException("回测历史结果超过保存上限");
        Files.createDirectories(root);
        Path target = path(run.historyRunId());
        if (Files.exists(target)) throw new IllegalStateException("回测历史 ID 已存在");
        Path temporary = Files.createTempFile(root, "backtest-", ".tmp");
        try {
            Files.write(temporary, data);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public BacktestRunDTO find(String id, Long userId, Long familyId) throws IOException {
        Path file = path(id);
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) return null;
        BacktestRunDTO run = read(file);
        return Objects.equals(run.ownerUserId(), userId) && Objects.equals(run.ownerFamilyId(), familyId) ? run : null;
    }

    public List<BacktestRunDTO> list(Long userId, Long familyId, int page, int size) throws IOException {
        return list(userId, familyId, page, size, false);
    }

    public List<BacktestRunDTO> recentSuccessful(Long userId, Long familyId) throws IOException {
        return list(userId, familyId, 0, 10, true);
    }

    private List<BacktestRunDTO> list(Long userId, Long familyId, int page, int size,
            boolean successfulOnly) throws IOException {
        if (page < 0 || size < 1 || size > 50 || (long) page * size > 100_000)
            throw new IllegalArgumentException("分页参数超出允许范围");
        if (!Files.isDirectory(root)) return List.of();
        List<BacktestRunDTO> found = new ArrayList<>();
        try (var files = Files.list(root)) {
            for (Path file : files.filter(p -> p.getFileName().toString().matches("[0-9a-f-]{36}\\.json"))
                    .filter(p -> Files.isRegularFile(p) && !Files.isSymbolicLink(p)).toList()) {
                BacktestRunDTO run = read(file);
                if (Objects.equals(run.ownerUserId(), userId) && Objects.equals(run.ownerFamilyId(), familyId)
                        && (!successfulOnly || "SUCCESS".equals(run.status()))) found.add(run);
            }
        }
        found.sort(Comparator.comparing(BacktestRunDTO::startedAt)
                .thenComparing(BacktestRunDTO::historyRunId).reversed());
        int start = (int) Math.min((long) found.size(), (long) page * size);
        return found.subList(start, Math.min(found.size(), start + size));
    }

    private BacktestRunDTO read(Path file) throws IOException {
        if (Files.size(file) > MAX_RECORD_BYTES) throw new IOException("回测历史记录超出大小限制");
        return mapper.readValue(Files.readAllBytes(file), BacktestRunDTO.class);
    }

    private Path path(String id) {
        if (id == null || !id.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("无效的回测历史 ID");
        return root.resolve(id + ".json");
    }
}
