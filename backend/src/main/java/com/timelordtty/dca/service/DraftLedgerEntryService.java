package com.timelordtty.dca.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.timelordtty.dca.dto.CreateDraftRequest;
import com.timelordtty.dca.dto.DraftLedgerEntryDTO;
import com.timelordtty.dca.dto.DraftPreviewDTO;
import com.timelordtty.dca.dto.UpdateDraftRequest;
import com.timelordtty.dca.mapper.AccountMapper;
import com.timelordtty.dca.mapper.DraftLedgerEntryMapper;
import com.timelordtty.dca.mapper.ProductMasterMapper;
import com.timelordtty.dca.model.Account;
import com.timelordtty.dca.model.DraftLedgerEntry;
import com.timelordtty.dca.model.LedgerTxn;
import com.timelordtty.dca.model.Order;
import com.timelordtty.dca.model.ProductMaster;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 草稿流水服务，负责 Phase3 自动记账候选记录的保存、预览、确认和忽略。
 *
 * <p>除 confirmDraft 明确调用 QuickEntryService（EXPENSE / INCOME / TRANSFER）或 OrderService
 * （BUY / SUBSCRIPTION 生成付款账本；SELL / REDEMPTION 只登记份额占用、不生成账本）外，
 * 本服务不会写入正式账本、账户余额、持仓成本或订单数据；previewDraft 始终是只读的。</p>
 */
@Service
public class DraftLedgerEntryService {
    /** 草稿流水持久化入口，只操作 draft_ledger_entry 表。 */
    private final DraftLedgerEntryMapper draftLedgerEntryMapper;
    /** 账户只读查询入口，用于预览阶段补充当前用户/家庭可见账户信息。 */
    private final AccountMapper accountMapper;
    /** 统一快速记账入口，确认 EXPENSE/INCOME 草稿时必须通过它写正式流水。 */
    private final QuickEntryService quickEntryService;
    /** JSON 编解码器，用于解析候选载荷并生成前端可展示的预览信息。 */
    private final ObjectMapper objectMapper;
    /** 产品主数据只读入口，用于投资草稿校验真实 productId、启用状态和币种。 */
    private final ProductMasterMapper productMasterMapper;
    /** 订单服务入口，只有主人二次确认投资 / 卖出赎回草稿后才创建 PENDING 订单。 */
    private final OrderService orderService;
    /** 持仓只读入口，用于 SELL / REDEMPTION 校验真实持仓来源与当前可用份额。 */
    private final HoldingService holdingService;

    /**
     * 装配草稿 Mapper、快速记账服务、产品只读入口、订单服务、持仓只读入口和 JSON 编解码器。
     */
    public DraftLedgerEntryService(DraftLedgerEntryMapper draftLedgerEntryMapper,
                                   AccountMapper accountMapper,
                                   QuickEntryService quickEntryService,
                                   ObjectMapper objectMapper,
                                   ProductMasterMapper productMasterMapper,
                                   OrderService orderService,
                                   HoldingService holdingService) {
        this.draftLedgerEntryMapper = draftLedgerEntryMapper;
        this.accountMapper = accountMapper;
        this.quickEntryService = quickEntryService;
        this.objectMapper = objectMapper;
        this.productMasterMapper = productMasterMapper;
        this.orderService = orderService;
        this.holdingService = holdingService;
    }

    /**
     * 创建草稿流水候选记录，初始状态为 DRAFT，不会触发正式账本入账。
     *
     * <p>当 sourceRef 非空时按幂等语义重放：同一可见归属作用域对相同 sourceType + sourceRef 的重复请求直接返回既有草稿，
     * 无论它仍是 DRAFT，还是已经 CONFIRMED / IGNORED，都不再插入第二条，让客户端据草稿状态决定下一步。
     * sourceRef 为空时保持原有非幂等行为，不按金额、备注等弱条件做模糊去重。</p>
     *
     * <p>应用层“先查后插”之外还有数据库唯一键兜底：两个请求同时查不到、随后同时插入时，后写的一方会收到唯一键冲突，
     * 本方法不在这种情况下抛错，而是按同一可见作用域重新查询并返回先写入的那条草稿，因此并发重放最多只落一条草稿。</p>
     */
    public DraftLedgerEntryDTO createDraft(Long userId, Long familyId, CreateDraftRequest request) {
        String sourceType = defaultIfBlank(request.getSourceType(), "manual");
        String sourceRef = normalizeSourceRef(request.getSourceRef());

        DraftLedgerEntry replay = findReplayableDraft(userId, familyId, sourceType, sourceRef);
        if (replay != null) {
            return DraftLedgerEntryDTO.fromModel(replay);
        }

        DraftLedgerEntry draft = new DraftLedgerEntry();
        draft.setOwnerUserId(userId);
        draft.setOwnerFamilyId(familyId);
        draft.setSourceType(sourceType);
        draft.setSourceRef(sourceRef);
        draft.setRawInput(request.getRawInput());
        draft.setParsedPayloadJson(request.getParsedPayloadJson());
        draft.setConfidence(request.getConfidence());
        draft.setMissingFieldsJson(request.getMissingFieldsJson());
        try {
            draftLedgerEntryMapper.insert(draft);
        } catch (DuplicateKeyException duplicateKey) {
            return DraftLedgerEntryDTO.fromModel(replayExistingDraftAfterDuplicateKey(
                    userId, familyId, sourceType, sourceRef, duplicateKey));
        }
        return DraftLedgerEntryDTO.fromModel(getVisibleDraft(userId, familyId, draft.getId()));
    }

    /**
     * 按当前用户/家庭可见性和 sourceType + sourceRef 查找可重放的既有草稿。
     *
     * <p>只有非空 sourceRef 才参与幂等匹配，避免把没有来源标识的草稿错误合并；匹配不限制草稿状态，
     * 保证已确认或已忽略的来源重试也不会再建第二条。查询始终带调用者 user/family 边界，不能命中他人不可见草稿。</p>
     */
    private DraftLedgerEntry findReplayableDraft(Long userId, Long familyId, String sourceType, String sourceRef) {
        if (sourceRef == null || sourceRef.isBlank()) {
            return null;
        }
        return draftLedgerEntryMapper.selectVisibleBySource(userId, familyId, sourceType, sourceRef);
    }

    /**
     * 并发重放竞态的唯一恢复路径：数据库唯一键冲突说明同一可见作用域内已有同来源草稿。
     *
     * <p>这里按同一 source scope 重新查询并返回既有草稿（DRAFT / CONFIRMED / IGNORED 均可）。
     * 只有明确的唯一键冲突（{@link DuplicateKeyException}）才会走到这里；重查不到既有草稿时原样抛出该冲突，
     * 保证其他数据库异常与不可见冲突不会被吞掉并伪装成成功。</p>
     */
    private DraftLedgerEntry replayExistingDraftAfterDuplicateKey(Long userId, Long familyId, String sourceType,
                                                                 String sourceRef, DuplicateKeyException duplicateKey) {
        DraftLedgerEntry existing = findReplayableDraft(userId, familyId, sourceType, sourceRef);
        if (existing == null) {
            throw duplicateKey;
        }
        return existing;
    }

    /**
     * 空 sourceRef 统一按 NULL 落库，与数据库唯一键语义保持一致。
     *
     * <p>唯一键只对非空 sourceRef 生效（MySQL 唯一索引不约束多行 NULL），因此“空来源不强制幂等”的规则必须先把
     * 空串、空白串归一化为 NULL，否则空串会被唯一键当成有效来源参与去重。</p>
     */
    private String normalizeSourceRef(String sourceRef) {
        return sourceRef == null || sourceRef.isBlank() ? null : sourceRef;
    }

    /**
     * 查询当前用户或家庭可见草稿列表，可按 DRAFT/CONFIRMED/IGNORED 状态过滤。
     */
    public List<DraftLedgerEntryDTO> listDrafts(Long userId, Long familyId, String status, Integer page, Integer pageSize) {
        int safePage = page != null && page > 0 ? page : 1;
        int safePageSize = pageSize != null && pageSize > 0 ? Math.min(pageSize, 100) : 20;
        int offset = (safePage - 1) * safePageSize;
        return draftLedgerEntryMapper.selectVisibleList(userId, familyId, normalizeStatusOrNull(status), offset, safePageSize)
                .stream()
                .map(DraftLedgerEntryDTO::fromModel)
                .toList();
    }

    /**
     * 查询当前用户或家庭可见草稿详情。
     */
    public DraftLedgerEntryDTO getDraft(Long userId, Long familyId, Long draftId) {
        return DraftLedgerEntryDTO.fromModel(getVisibleDraft(userId, familyId, draftId));
    }

    /**
     * 更新 DRAFT 状态草稿候选内容；已确认或已忽略草稿不允许再编辑。
     */
    @Transactional
    public DraftLedgerEntryDTO updateDraft(Long userId, Long familyId, Long draftId, UpdateDraftRequest request) {
        DraftLedgerEntry existing = getVisibleDraft(userId, familyId, draftId);
        requireDraftStatus(existing, "只有 DRAFT 状态草稿允许编辑");

        DraftLedgerEntry draft = new DraftLedgerEntry();
        draft.setId(draftId);
        draft.setSourceType(defaultIfBlank(request.getSourceType(), existing.getSourceType()));
        draft.setSourceRef(normalizeSourceRef(request.getSourceRef()));
        draft.setRawInput(request.getRawInput());
        draft.setParsedPayloadJson(request.getParsedPayloadJson());
        draft.setConfidence(request.getConfidence());
        draft.setMissingFieldsJson(request.getMissingFieldsJson());
        int updated;
        try {
            updated = draftLedgerEntryMapper.updateDraftContent(draft);
        } catch (DuplicateKeyException duplicateKey) {
            throw new RuntimeException("该来源已被同一可见范围内的另一条草稿占用，请更换来源标识或刷新后重试");
        }
        if (updated != 1) {
            throw new RuntimeException("草稿更新失败，请刷新后重试");
        }
        return DraftLedgerEntryDTO.fromModel(getVisibleDraft(userId, familyId, draftId));
    }

    /**
     * 生成草稿确认预览，只更新 preview_payload_json，不写入正式 ledger_txn 或 ledger_posting。
     */
    @Transactional
    public DraftPreviewDTO previewDraft(Long userId, Long familyId, Long draftId) {
        DraftLedgerEntry draft = getVisibleDraft(userId, familyId, draftId);
        requireDraftStatus(draft, "只有 DRAFT 状态草稿允许生成预览");
        DraftPreviewDTO preview = buildPreview(draft);
        draftLedgerEntryMapper.updatePreview(draftId, toJson(preview));
        return preview;
    }

    /**
     * 确认草稿并转为正式流水；支持 EXPENSE / INCOME / TRANSFER（QuickEntryService）、BUY / SUBSCRIPTION（OrderService 生成付款账本）
     * 与 SELL / REDEMPTION（OrderService 只创建 PENDING 订单并登记份额占用）。
     *
     * <p>确认前会重新生成 fresh preview 并复用同一套校验规则；confirmSupported=false 时直接拒绝。
     * 投资草稿确认只创建 PENDING 订单并生成付款账本，不结算、不生成最终持仓。</p>
     */
    @Transactional
    public DraftLedgerEntryDTO confirmDraft(Long userId, Long familyId, Long draftId) {
        DraftLedgerEntry draft = draftLedgerEntryMapper.selectVisibleByIdForUpdate(draftId, userId, familyId);
        if (draft == null) {
            throw new RuntimeException("草稿不存在或无权限访问");
        }
        if ("CONFIRMED".equals(draft.getStatus())) {
            return DraftLedgerEntryDTO.fromModel(draft);
        }
        if ("IGNORED".equals(draft.getStatus())) {
            throw new RuntimeException("已忽略草稿不能确认");
        }

        DraftPreviewDTO preview = buildPreview(draft);
        draftLedgerEntryMapper.updatePreview(draftId, toJson(preview));
        if (!Boolean.TRUE.equals(preview.getConfirmSupported())) {
            throw new RuntimeException(preview.getMessage());
        }

        String confirmedTxnId = null;
        String confirmedOrderId = null;
        if ("EXPENSE".equals(preview.getTxnType())) {
            LedgerTxn txn = quickEntryService.quickExpense(userId, preview.getAccountId(), preview.getAmount(), preview.getNote());
            confirmedTxnId = txn.getTxnId();
        } else if ("INCOME".equals(preview.getTxnType())) {
            LedgerTxn txn = quickEntryService.quickIncome(userId, preview.getAccountId(), preview.getAmount(), preview.getNote());
            confirmedTxnId = txn.getTxnId();
        } else if ("TRANSFER".equals(preview.getTxnType())) {
            LedgerTxn txn = quickEntryService.quickTransfer(userId, draft.getOwnerFamilyId(), preview.getAccountId(),
                    preview.getTargetAccountId(), preview.getAmount(), preview.getNote());
            confirmedTxnId = txn.getTxnId();
        } else if (isInvestmentType(preview.getTxnType())) {
            // 只有这里会在主人二次确认后创建 PENDING 订单，并复用 OrderService 既有的付款账本语义；
            // 本服务绝不调用 SettlementService，也不生成最终持仓。
            Order order = orderService.createInvestmentDraftOrder(userId, draft.getOwnerFamilyId(),
                    preview.getProductId(), preview.getTxnType(), preview.getAmount(), preview.getAccountId(),
                    preview.getExpectedNavDate(), preview.getExpectedConfirmDate(), preview.getNote());
            confirmedOrderId = order.getOrderId();
        } else if (isSellRedeemType(preview.getTxnType())) {
            // 只有这里会在主人二次确认后创建 PENDING 卖出 / 赎回订单，并且只登记 SOURCE / TARGET 份额占用；
            // 本服务绝不调用 SettlementService，也绝不生成任何 CASH / POSITION 流水或持仓变化。
            Order order = orderService.createSellRedeemDraftOrder(userId, draft.getOwnerFamilyId(),
                    preview.getProductId(), preview.getTxnType(), preview.getShares(),
                    preview.getAccountId(), preview.getTargetAccountId(),
                    preview.getExpectedNavDate(), preview.getExpectedConfirmDate(), preview.getNote());
            confirmedOrderId = order.getOrderId();
        } else {
            throw new RuntimeException("草稿确认仅支持 EXPENSE/INCOME/TRANSFER/BUY/SUBSCRIPTION/SELL/REDEMPTION");
        }

        int updated = draftLedgerEntryMapper.markConfirmed(draftId, confirmedTxnId, confirmedOrderId);
        if (updated != 1) {
            throw new RuntimeException("草稿确认状态更新失败，事务已回滚");
        }
        return DraftLedgerEntryDTO.fromModel(getVisibleDraft(userId, familyId, draftId));
    }

    /**
     * 忽略 DRAFT 状态草稿，记录原因后不再允许编辑或确认。
     */
    @Transactional
    public DraftLedgerEntryDTO ignoreDraft(Long userId, Long familyId, Long draftId, String ignoreReason) {
        DraftLedgerEntry draft = getVisibleDraft(userId, familyId, draftId);
        requireDraftStatus(draft, "只有 DRAFT 状态草稿允许忽略");
        int updated = draftLedgerEntryMapper.markIgnored(draftId, ignoreReason);
        if (updated != 1) {
            throw new RuntimeException("草稿忽略失败，请刷新后重试");
        }
        return DraftLedgerEntryDTO.fromModel(getVisibleDraft(userId, familyId, draftId));
    }

    /**
     * 查询可见草稿，不存在或越权时统一返回业务错误。
     */
    private DraftLedgerEntry getVisibleDraft(Long userId, Long familyId, Long draftId) {
        DraftLedgerEntry draft = draftLedgerEntryMapper.selectVisibleById(draftId, userId, familyId);
        if (draft == null) {
            throw new RuntimeException("草稿不存在或无权限访问");
        }
        return draft;
    }

    /**
     * 校验草稿仍处于 DRAFT 状态，防止已确认或已忽略草稿被重复修改。
     */
    private void requireDraftStatus(DraftLedgerEntry draft, String message) {
        if (!"DRAFT".equals(draft.getStatus())) {
            throw new RuntimeException(message);
        }
    }

    /**
     * 基于 parsed_payload_json 生成确认预览，支持 EXPENSE / INCOME / TRANSFER / BUY / SUBSCRIPTION 的必要字段校验。
     *
     * <p>本方法只读：不创建订单、不写正式账本、不结算。preview 与 confirm 复用同一套规则，避免规则漂移。</p>
     */
    private DraftPreviewDTO buildPreview(DraftLedgerEntry draft) {
        Map<String, Object> payload = parsePayload(draft.getParsedPayloadJson());
        DraftPreviewDTO preview = new DraftPreviewDTO();
        preview.setDraftId(draft.getId());
        preview.setTxnType(normalizeTxnType(firstString(payload, "txnType", "transactionType", "type")));
        preview.setAccountId(firstLong(payload, "accountId", "cashAccountId", "sourceAccountId"));
        preview.setTargetAccountId(firstLong(payload, "targetAccountId", "toAccountId", "destinationAccountId"));
        preview.setAmount(firstBigDecimal(payload, "amount"));
        preview.setNote(defaultIfBlank(firstString(payload, "note", "remark", "description"), draft.getRawInput()));
        preview.setWillCreateOrder(false);
        preview.setWillCreateSettlement(false);
        preview.setWillAffectHolding(false);

        if (isInvestmentType(preview.getTxnType())) {
            return buildInvestmentPreview(preview, payload, draft);
        }

        if (isSellRedeemType(preview.getTxnType())) {
            return buildSellRedeemPreview(preview, payload, draft);
        }

        boolean transfer = "TRANSFER".equals(preview.getTxnType());

        List<String> missing = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean accountUnavailable = false;
        boolean targetAccountUnavailable = false;
        String accountRuleViolation = null;
        String targetAccountRuleViolation = null;
        if (preview.getTxnType() == null) {
            missing.add("txnType");
        }
        if (preview.getAccountId() == null) {
            missing.add("accountId");
        }
        if (transfer && preview.getTargetAccountId() == null) {
            missing.add("targetAccountId");
        }
        if (preview.getAmount() == null || preview.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            missing.add("amount");
        }

        Account account = null;
        if (preview.getAccountId() != null) {
            account = accountMapper.selectVisibleRealById(preview.getAccountId(), draft.getOwnerUserId(), draft.getOwnerFamilyId());
            if (account == null) {
                accountUnavailable = true;
                addIfAbsent(missing, "accountId");
                warnings.add("账户不存在、已停用或当前用户/家庭不可见，请重新选择可用账户。");
            } else {
                preview.setAccountName(account.getAccountName());
                preview.setAccountType(account.getAccountType());
                preview.setFundUsage(account.getFundUsage());
                if (!accountMapper.selectChildren(account.getId()).isEmpty()) {
                    accountRuleViolation = "父账户仅用于聚合展示，不能作为记账账户；请选择其下可用的叶子账户。";
                } else if (!transfer && "EXPENSE".equals(preview.getTxnType())
                        && !"SPENDABLE".equals(account.getFundUsage())) {
                    accountRuleViolation = expenseAccountRuleMessage(account.getFundUsage());
                }
                if (accountRuleViolation != null) {
                    addIfAbsent(missing, "accountId");
                    warnings.add(accountRuleViolation);
                }
            }
        }

        Account targetAccount = null;
        if (transfer && preview.getTargetAccountId() != null) {
            targetAccount = accountMapper.selectVisibleRealById(preview.getTargetAccountId(), draft.getOwnerUserId(), draft.getOwnerFamilyId());
            if (targetAccount == null) {
                targetAccountUnavailable = true;
                addIfAbsent(missing, "targetAccountId");
                warnings.add("转入账户不存在、已停用或当前用户/家庭不可见，请重新选择可用账户。");
            } else {
                preview.setTargetAccountName(targetAccount.getAccountName());
                preview.setTargetAccountType(targetAccount.getAccountType());
                preview.setTargetFundUsage(targetAccount.getFundUsage());
                if (!accountMapper.selectChildren(targetAccount.getId()).isEmpty()) {
                    targetAccountRuleViolation = "转入账户是父账户，仅用于聚合展示；请选择其下的叶子账户。";
                }
                if (targetAccountRuleViolation != null) {
                    addIfAbsent(missing, "targetAccountId");
                    warnings.add(targetAccountRuleViolation);
                }
            }
        }

        if (transfer && account != null && targetAccount != null) {
            if (account.getId() != null && account.getId().equals(targetAccount.getId())) {
                targetAccountRuleViolation = "转出账户与转入账户不能相同；请重新选择转入账户。";
                addIfAbsent(missing, "targetAccountId");
                warnings.add(targetAccountRuleViolation);
            } else if (!currencyEquals(account.getCurrency(), targetAccount.getCurrency())) {
                targetAccountRuleViolation = "转出账户与转入账户币种必须一致；跨币种转账暂不支持。";
                addIfAbsent(missing, "targetAccountId");
                warnings.add(targetAccountRuleViolation);
            } else if (!fundUsageEquals(account.getFundUsage(), targetAccount.getFundUsage())) {
                warnings.add("本次会把资金从 " + displayFundUsage(account.getFundUsage())
                        + " 转到 " + displayFundUsage(targetAccount.getFundUsage()) + "，请确认这是主动调整资金分区。");
            }
        }
        preview.setMissingFields(missing);

        boolean supportedType = "EXPENSE".equals(preview.getTxnType())
                || "INCOME".equals(preview.getTxnType())
                || transfer;
        applyAccountImpact(preview);
        boolean ready = supportedType && missing.isEmpty();
        preview.setConfirmSupported(ready);
        preview.setWillCreateLedgerTxn(ready);
        if (ready && transfer) {
            preview.setMessage("可确认：将通过 QuickEntryService 生成一笔从转出账户到转入账户的正式转账流水");
            warnings.add("预览阶段不会写入正式账本；只有点击确认后才会生成正式转账流水。");
        } else if (ready) {
            preview.setMessage("可确认：将通过 QuickEntryService 生成正式" + preview.getTxnType() + "流水");
            warnings.add("预览阶段不会写入正式账本；只有点击确认后才会生成正式流水。");
        } else if (!supportedType) {
            preview.setMessage("草稿确认仅支持 EXPENSE/INCOME/TRANSFER/BUY/SUBSCRIPTION/SELL/REDEMPTION 记账");
        } else if (accountUnavailable) {
            preview.setMessage("草稿账户不存在、已停用或当前用户/家庭不可见，请重新选择账户。");
        } else if (targetAccountUnavailable) {
            preview.setMessage("转入账户不存在、已停用或当前用户/家庭不可见，请重新选择转入账户。");
        } else if (accountRuleViolation != null) {
            preview.setMessage(accountRuleViolation);
        } else if (targetAccountRuleViolation != null) {
            preview.setMessage(targetAccountRuleViolation);
        } else {
            preview.setMessage("草稿缺少必要字段：" + String.join(", ", missing));
        }
        preview.setWarnings(warnings);
        return preview;
    }

    /** 判断草稿类型是否为投资买入 / 申购；BUY 与 SUBSCRIPTION 确认后会生成付款账本。 */
    private boolean isInvestmentType(String txnType) {
        return "BUY".equals(txnType) || "SUBSCRIPTION".equals(txnType);
    }

    /** 判断草稿类型是否为卖出 / 赎回；SELL 与 REDEMPTION 确认后只创建 PENDING 订单，不生成账本。 */
    private boolean isSellRedeemType(String txnType) {
        return "SELL".equals(txnType) || "REDEMPTION".equals(txnType);
    }
    /**
     * 只读生成 SELL / REDEMPTION 确认预览，与 confirm 复用同一套安全规则。
     *
     * <p>本方法绝不调用 OrderService.createOrder / createSellRedeemDraftOrder、LedgerService 或
     * SettlementService；不创建订单、不写正式账本、不改现金余额、不改持仓。</p>
     *
     * <p>可用份额 = 该产品在持仓来源账户下的真实持仓份额 - 同产品 / 来源账户下仍为 PENDING 的
     * SELL / REDEMPTION 占用份额；到账账户必须是当前 user/family 可见、启用的 REAL 叶子账户，
     * 币种与产品一致，且不能是 POSITION 持仓账户或父账户。</p>
     */
    private DraftPreviewDTO buildSellRedeemPreview(DraftPreviewDTO preview, Map<String, Object> payload,
                                                   DraftLedgerEntry draft) {
        boolean sell = "SELL".equals(preview.getTxnType());
        preview.setOrderType(preview.getTxnType());
        preview.setProductId(firstLong(payload, "productId"));
        String productNameHint = firstString(payload, "productNameHint");
        preview.setExpectedNavDate(firstString(payload, "expectedNavDate"));
        preview.setExpectedConfirmDate(firstString(payload, "expectedConfirmDate"));
        BigDecimal shares = firstBigDecimal(payload, "shares");
        preview.setShares(shares);

        List<String> missing = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean sharesInvalid = shares == null || shares.compareTo(BigDecimal.ZERO) <= 0;
        if (sharesInvalid) {
            missing.add("shares");
            warnings.add("份额 shares 必须大于 0，才能确认卖出 / 赎回订单。");
        }
        if (preview.getProductId() == null) {
            missing.add("productId");
        }
        if (preview.getAccountId() == null) {
            missing.add("sourceAccountId");
        }
        if (preview.getTargetAccountId() == null) {
            missing.add("targetAccountId");
        }

        ProductMaster product = null;
        if (preview.getProductId() != null) {
            product = productMasterMapper.selectById(preview.getProductId());
            if (product == null) {
                addIfAbsent(missing, "productId");
                warnings.add("产品不存在或已被删除，请重新选择启用的产品。");
            } else if (!Boolean.TRUE.equals(product.getIsActive())) {
                addIfAbsent(missing, "productId");
                warnings.add("产品已停用，不能再用于卖出 / 赎回草稿，请重新选择启用产品。");
            } else {
                preview.setProductName(product.getProductName());
                preview.setProductCode(product.getProductCode());
                preview.setProductAssetType(product.getAssetType());
                preview.setProductCurrency(product.getCurrency());
            }
        } else {
            addIfAbsent(missing, "productId");
            String hintSuffix = productNameHint == null ? "" :
                    "当前产品名称提示 " + productNameHint + " 只作参考。";
            warnings.add("请在 App / PC 明确选择真实产品；系统不会按产品名称自动匹配 productId。" + hintSuffix);
        }

        BigDecimal availableShares = null;
        if (preview.getAccountId() == null) {
            warnings.add("请选择该产品当前真实的持仓来源账户；系统不会自动匹配持仓来源。");
        } else if (product != null) {
            HoldingService.AccountHoldingInfo source =
                    findHoldingSource(draft, preview.getProductId(), preview.getAccountId());
            if (source == null) {
                addIfAbsent(missing, "sourceAccountId");
                warnings.add("持仓来源账户不是该产品当前的真实持仓来源，或当前用户/家庭不可见，请重新选择。");
            } else {
                preview.setAccountName(source.getAccountName());
                BigDecimal held = source.getShares() != null ? source.getShares() : BigDecimal.ZERO;
                BigDecimal occupied = orderService.sumPendingSellSharesByAccount(
                        preview.getProductId(), draft.getOwnerUserId(), source.getAccountId());
                availableShares = held.subtract(occupied);
                if (availableShares.compareTo(BigDecimal.ZERO) < 0) {
                    availableShares = BigDecimal.ZERO;
                }
                preview.setAvailableShares(availableShares);
                if (availableShares.compareTo(BigDecimal.ZERO) <= 0) {
                    addIfAbsent(missing, "sourceAccountId");
                    warnings.add("该持仓来源当前可用份额为 0（真实持仓 "
                            + displayShares(held) + "，已被 PENDING 卖出 / 赎回占用 "
                            + displayShares(occupied) + "），不能再卖出 / 赎回。");
                } else if (!sharesInvalid && shares.compareTo(availableShares) > 0) {
                    addIfAbsent(missing, "shares");
                    warnings.add("本次份额超过可用份额（可用 " + displayShares(availableShares)
                            + "），请减少份额，或先等待 PENDING 卖出 / 赎回订单结算 / 取消。");
                }
            }
        }

        if (preview.getTargetAccountId() == null) {
            warnings.add("请选择本次资金的到账账户；系统不会自动匹配到账账户。");
        } else {
            Account target = accountMapper.selectVisibleRealById(preview.getTargetAccountId(),
                    draft.getOwnerUserId(), draft.getOwnerFamilyId());
            if (target == null) {
                addIfAbsent(missing, "targetAccountId");
                warnings.add("到账账户不存在、已停用或当前用户/家庭不可见，请重新选择可用账户。");
            } else {
                preview.setTargetAccountName(target.getAccountName());
                preview.setTargetAccountType(target.getAccountType());
                preview.setTargetFundUsage(target.getFundUsage());
                if (!accountMapper.selectChildren(target.getId()).isEmpty()) {
                    addIfAbsent(missing, "targetAccountId");
                    warnings.add("到账账户是父账户，仅用于聚合展示；请选择其下的叶子账户。");
                }
                if ("POSITION".equalsIgnoreCase(target.getAccountType())) {
                    addIfAbsent(missing, "targetAccountId");
                    warnings.add("到账账户不能是 POSITION 持仓账户；请选择现金类 REAL 叶子账户。");
                }
                if (product != null && !currencyEquals(target.getCurrency(), product.getCurrency())) {
                    addIfAbsent(missing, "targetAccountId");
                    warnings.add("到账账户币种与产品币种不一致，请选择币种一致的到账账户。");
                }
            }
        }

        preview.setMissingFields(missing);
        // SELL / REDEMPTION 确认只登记份额占用：不生成流水、不改现金余额、不改持仓。
        preview.setImpactDirection("NONE");
        preview.setAccountDelta(BigDecimal.ZERO);
        preview.setTargetAccountDelta(BigDecimal.ZERO);
        preview.setReceivableDelta(BigDecimal.ZERO);
        preview.setWillCreateLedgerTxn(false);
        preview.setWillCreateSettlement(false);
        preview.setWillAffectHolding(false);

        boolean ready = missing.isEmpty();
        preview.setConfirmSupported(ready);
        preview.setWillCreateOrder(ready);
        if (availableShares != null && !sharesInvalid) {
            BigDecimal remaining = availableShares.subtract(shares);
            preview.setRemainingShares(remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining);
        }

        String action = sell ? "卖出" : "赎回";
        String productLabel = preview.getProductName() != null ? preview.getProductName()
                : (productNameHint != null ? productNameHint : "所选产品");
        String sourceLabel = preview.getAccountName() != null ? preview.getAccountName() : "所选持仓来源";
        String targetLabel = preview.getTargetAccountName() != null ? preview.getTargetAccountName() : "所选到账账户";
        if (ready) {
            String sharesText = displayShares(shares);
            preview.setSharesMessage("确认后只创建内部 PENDING " + action + "记录并占用 " + sourceLabel + " 的 "
                    + sharesText + " 份；不会立即减少持仓，也不会立即增加 " + targetLabel
                    + " 的到账余额。");
            preview.setMessage("可确认：将创建 PENDING " + action + "订单（产品 " + productLabel
                    + "），仅登记内部待处理份额占用；确认后持仓与现金余额保持不变，"
                    + "真正的资金与持仓变化只在后续人工结算时产生。");
            warnings.add("预览阶段不会创建订单、不会写入账本、不会结算；只有主人再次确认后才会创建 PENDING 订单。");
        } else if (!warnings.isEmpty()) {
            preview.setMessage(warnings.get(0));
        } else {
            preview.setMessage("草稿缺少必要字段：" + String.join(", ", missing));
        }
        preview.setWarnings(warnings);
        return preview;
    }

    /**
     * 在指定产品的持仓来源中查找该账户；只使用 HoldingService 按当前 user/family 计算的真实持仓，
     * 找不到时返回 null，绝不按账户名称或历史订单猜测持仓来源。
     */
    private HoldingService.AccountHoldingInfo findHoldingSource(DraftLedgerEntry draft, Long productId, Long accountId) {
        if (productId == null || accountId == null) {
            return null;
        }
        List<HoldingService.AccountHoldingInfo> holdings = holdingService.getProductHoldingsByAccount(
                productId, draft.getOwnerUserId(), draft.getOwnerFamilyId());
        if (holdings == null) {
            return null;
        }
        for (HoldingService.AccountHoldingInfo holding : holdings) {
            if (holding != null && accountId.equals(holding.getAccountId())) {
                return holding;
            }
        }
        return null;
    }

    /** 份额展示文案：去掉无意义尾随零，空值按 0 处理。 */
    private String displayShares(BigDecimal shares) {
        if (shares == null) {
            return "0";
        }
        return shares.stripTrailingZeros().toPlainString();
    }

    /**
     * 只读生成 BUY / SUBSCRIPTION 确认预览，与 confirm 复用同一套安全规则。
     *
     * <p>本方法绝不调用 OrderService.createOrder、LedgerService.createTransaction 或 SettlementService，
     * 不创建订单、不写正式账本、不结算、不影响持仓。</p>
     */
    private DraftPreviewDTO buildInvestmentPreview(DraftPreviewDTO preview, Map<String, Object> payload,
                                                   DraftLedgerEntry draft) {
        boolean buy = "BUY".equals(preview.getTxnType());
        preview.setOrderType(preview.getTxnType());
        preview.setProductId(firstLong(payload, "productId"));
        String productNameHint = firstString(payload, "productNameHint");
        preview.setExpectedNavDate(firstString(payload, "expectedNavDate"));
        preview.setExpectedConfirmDate(firstString(payload, "expectedConfirmDate"));

        List<String> missing = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean amountInvalid = preview.getAmount() == null || preview.getAmount().compareTo(BigDecimal.ZERO) <= 0;
        if (amountInvalid) {
            missing.add("amount");
        }
        if (preview.getAccountId() == null) {
            missing.add("accountId");
        }
        if (preview.getProductId() == null) {
            missing.add("productId");
        }

        ProductMaster product = null;
        if (preview.getProductId() != null) {
            product = productMasterMapper.selectById(preview.getProductId());
            if (product == null) {
                addIfAbsent(missing, "productId");
                warnings.add("产品不存在或已被删除，请重新选择启用的产品。");
            } else if (!Boolean.TRUE.equals(product.getIsActive())) {
                addIfAbsent(missing, "productId");
                warnings.add("产品已停用，不能再用于买入 / 申购草稿，请重新选择启用产品。");
            } else {
                preview.setProductName(product.getProductName());
                preview.setProductCode(product.getProductCode());
                preview.setProductAssetType(product.getAssetType());
                preview.setProductCurrency(product.getCurrency());
            }
        } else {
            addIfAbsent(missing, "productId");
            String hintSuffix = productNameHint == null ? "" :
                    "当前产品名称提示“" + productNameHint + "”只作参考。";
            warnings.add("请在 App / PC 明确选择真实产品；系统不会按产品名称自动匹配 productId。" + hintSuffix);
        }

        Account account = null;
        if (preview.getAccountId() != null) {
            account = accountMapper.selectVisibleRealById(preview.getAccountId(), draft.getOwnerUserId(), draft.getOwnerFamilyId());
            if (account == null) {
                addIfAbsent(missing, "accountId");
                warnings.add("付款资金账户不存在、已停用或当前用户/家庭不可见，请重新选择可用账户。");
            } else {
                preview.setAccountName(account.getAccountName());
                preview.setAccountType(account.getAccountType());
                preview.setFundUsage(account.getFundUsage());
                String ruleViolation = investmentAccountRuleViolation(product, account);
                if (ruleViolation != null) {
                    addIfAbsent(missing, "accountId");
                    warnings.add(ruleViolation);
                }
                if (product != null && !currencyEquals(account.getCurrency(), product.getCurrency())) {
                    addIfAbsent(missing, "accountId");
                    warnings.add("资金账户币种与产品币种不一致，请选择币种一致的资金账户。");
                }
                BigDecimal available = availableAmount(account);
                preview.setAvailableBefore(available);
                if (!amountInvalid && available.compareTo(preview.getAmount()) < 0) {
                    addIfAbsent(missing, "accountId");
                    warnings.add("资金账户可用余额不足（可用 " + available.stripTrailingZeros().toPlainString()
                            + "）；请先通过 TRANSFER 调整资金到该账户。");
                }
            }
        }
        if (amountInvalid) {
            warnings.add("金额 amount 必须大于 0，才能确认买入 / 申购订单。");
        }

        preview.setMissingFields(missing);
        if (amountInvalid) {
            preview.setImpactDirection("NONE");
            preview.setAccountDelta(BigDecimal.ZERO);
            preview.setReceivableDelta(BigDecimal.ZERO);
        } else {
            preview.setImpactDirection("DECREASE");
            preview.setAccountDelta(preview.getAmount().negate());
            preview.setReceivableDelta(preview.getAmount());
        }

        boolean ready = missing.isEmpty();
        preview.setConfirmSupported(ready);
        preview.setWillCreateLedgerTxn(ready);
        preview.setWillCreateOrder(ready);
        preview.setWillCreateSettlement(false);
        preview.setWillAffectHolding(false);

        String action = buy ? "买入" : "申购";
        String productLabel = preview.getProductName() != null ? preview.getProductName()
                : (productNameHint != null ? productNameHint : "所选产品");
        String accountLabel = preview.getAccountName() != null ? preview.getAccountName() : "所选资金账户";
        if (ready) {
            String amountText = preview.getAmount().stripTrailingZeros().toPlainString();
            preview.setFundingMessage("确认后将立即从【" + accountLabel + "】扣除 ¥" + amountText
                    + "，并增加同额待结算应收；订单仍需后续结算，不会自动成交。");
            preview.setMessage("可确认：将创建 PENDING " + action + "订单，并立即生成下单付款账本；资金账户减少 ¥"
                    + amountText + "，待结算应收增加 ¥" + amountText + "。此时尚未结算，也不会生成最终持仓。");
            warnings.add("预览阶段不会创建订单、写入账本或结算；只有主人再次确认后才会创建 PENDING 订单并生成付款账本。");
        } else if (!warnings.isEmpty()) {
            preview.setMessage(warnings.get(0));
        } else {
            preview.setMessage("草稿缺少必要字段：" + String.join(", ", missing));
        }
        preview.setWarnings(warnings);
        return preview;
    }

    /**
     * 资金用途安全规则：一般投资只允许 INVESTABLE；BOND_REPO 额外允许 RESERVED；SPENDABLE 与父账户一律阻断。
     *
     * <p>与 Account 设计保持一致：SPENDABLE 需先通过 TRANSFER 调整到 INVESTABLE；RESERVED 除逆回购外不允许普通投资。</p>
     */
    private String investmentAccountRuleViolation(ProductMaster product, Account account) {
        if (!accountMapper.selectChildren(account.getId()).isEmpty()) {
            return "父账户仅用于聚合展示，不能作为投资资金账户；请选择其下可用的叶子账户。";
        }
        String fundUsage = account.getFundUsage();
        boolean bondRepo = product != null && "BOND_REPO".equalsIgnoreCase(product.getAssetType());
        if ("INVESTABLE".equals(fundUsage)) {
            return null;
        }
        if ("RESERVED".equals(fundUsage) && bondRepo) {
            return null;
        }
        if ("SPENDABLE".equals(fundUsage)) {
            return "SPENDABLE 账户不直接用于投资；请先通过 TRANSFER 调整到 INVESTABLE 叶子账户。";
        }
        if ("RESERVED".equals(fundUsage)) {
            return "专款 RESERVED 账户除国债逆回购外不得用于普通投资；请选择 INVESTABLE 叶子账户。";
        }
        return "该账户未完成资金分区，不得直接用于投资；请先选择 INVESTABLE 叶子账户。";
    }

    /** 可用余额 = balance - reserved_amount，空值按 0 处理，与后端真实下单校验保持一致。 */
    private BigDecimal availableAmount(Account account) {
        BigDecimal balance = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
        BigDecimal reserved = account.getReservedAmount() != null ? account.getReservedAmount() : BigDecimal.ZERO;
        return balance.subtract(reserved);
    }

    private String expenseAccountRuleMessage(String fundUsage) {
        if ("RESERVED".equals(fundUsage)) {
            return "专款 RESERVED 账户不得用于日常消费；请选择 SPENDABLE 叶子账户。";
        }
        if ("INVESTABLE".equals(fundUsage)) {
            return "可投资 INVESTABLE 账户不得用于日常消费；请选择 SPENDABLE 叶子账户。";
        }
        return "待分配账户不应直接用于日常消费；请先完成资金分区并选择 SPENDABLE 叶子账户。";
    }

    /** 比较两个账户币种是否一致；双方都为空时视为一致，避免因历史空值误阻断。 */
    private boolean currencyEquals(String left, String right) {
        if (left == null || left.isBlank()) {
            return right == null || right.isBlank();
        }
        return left.equalsIgnoreCase(right);
    }

    /** 比较两个账户的资金用途是否一致，用于生成跨用途转账的中文风险提示。 */
    private boolean fundUsageEquals(String left, String right) {
        if (left == null || left.isBlank()) {
            return right == null || right.isBlank();
        }
        return left.equalsIgnoreCase(right);
    }

    /** 资金用途展示文案，空值统一显示为“待分配”。 */
    private String displayFundUsage(String fundUsage) {
        return fundUsage == null || fundUsage.isBlank() ? "待分配" : fundUsage;
    }

    /**
     * 计算 EXPENSE/INCOME/TRANSFER 对账户的方向和金额影响；其他类型不提供可确认影响。
     *
     * <p>TRANSFER 对转出账户为负数（accountDelta），对转入账户为正数（targetAccountDelta）。</p>
     */
    private void applyAccountImpact(DraftPreviewDTO preview) {
        if (preview.getAmount() == null || preview.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            preview.setImpactDirection("NONE");
            preview.setAccountDelta(BigDecimal.ZERO);
            return;
        }
        if ("EXPENSE".equals(preview.getTxnType())) {
            preview.setImpactDirection("DECREASE");
            preview.setAccountDelta(preview.getAmount().negate());
        } else if ("INCOME".equals(preview.getTxnType())) {
            preview.setImpactDirection("INCREASE");
            preview.setAccountDelta(preview.getAmount());
        } else if ("TRANSFER".equals(preview.getTxnType())) {
            preview.setImpactDirection("DECREASE");
            preview.setAccountDelta(preview.getAmount().negate());
            preview.setTargetAccountDelta(preview.getAmount());
        } else {
            preview.setImpactDirection("NONE");
            preview.setAccountDelta(BigDecimal.ZERO);
        }
    }

    /**
     * 缺失字段列表同时承载“缺失”和“不可用”两类阻断原因，避免重复追加同一字段。
     */
    private void addIfAbsent(List<String> values, String value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    /**
     * 将候选载荷解析为 Map；空载荷按空对象处理，非法 JSON 视为不可确认草稿。
     */
    private Map<String, Object> parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("parsed_payload_json 不是有效 JSON，无法生成草稿预览");
        }
    }

    /**
     * 将预览对象写成 JSON 字符串，保存给前端展示和排查使用。
     */
    private String toJson(DraftPreviewDTO preview) {
        try {
            return objectMapper.writeValueAsString(preview);
        } catch (Exception e) {
            throw new RuntimeException("草稿预览 JSON 生成失败");
        }
    }

    /**
     * 从多个候选字段中取第一个非空字符串，用于兼容不同解析器输出。
     */
    private String firstString(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    /**
     * 从多个候选字段中取第一个可转为 Long 的账户 ID。
     */
    private Long firstLong(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value != null && !value.toString().isBlank()) {
                try {
                    return Long.parseLong(value.toString());
                } catch (NumberFormatException e) {
                    throw new RuntimeException(key + " 必须是数字");
                }
            }
        }
        return null;
    }

    /**
     * 从候选字段中读取金额并转为 BigDecimal，避免 double 直接参与正式记账。
     */
    private BigDecimal firstBigDecimal(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value != null && !value.toString().isBlank()) {
            try {
                return new BigDecimal(value.toString());
            } catch (NumberFormatException e) {
                throw new RuntimeException(key + " 必须是数字");
            }
        }
        return null;
    }

    /**
     * 统一草稿流水类型大小写，允许 EXPENSE/INCOME/TRANSFER 进入确认路径。
     */
    private String normalizeTxnType(String txnType) {
        return txnType == null ? null : txnType.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 统一列表筛选状态大小写，空值表示不过滤。
     */
    private String normalizeStatusOrNull(String status) {
        return status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 为空字符串提供默认值，避免草稿来源或备注出现无意义空白。
     */
    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
