package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.DraftLedgerEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 草稿流水 Mapper，只读写 draft_ledger_entry，不触碰正式 ledger_txn 或 ledger_posting。
 */
@Mapper
public interface DraftLedgerEntryMapper {
    /**
     * 插入一条草稿流水候选记录，初始状态固定为 DRAFT。
     */
    int insert(DraftLedgerEntry draft);

    /**
     * 按当前用户或家庭权限查询可见草稿详情。
     */
    DraftLedgerEntry selectVisibleById(@Param("id") Long id, @Param("userId") Long userId, @Param("familyId") Long familyId);

    /**
     * 按当前用户或家庭权限查询草稿详情并加行锁，用于确认时防止重复入账。
     */
    DraftLedgerEntry selectVisibleByIdForUpdate(@Param("id") Long id, @Param("userId") Long userId, @Param("familyId") Long familyId);

    /**
     * 查询当前用户或家庭可见的草稿列表，可按状态筛选。
     */
    List<DraftLedgerEntry> selectVisibleList(@Param("userId") Long userId,
                                             @Param("familyId") Long familyId,
                                             @Param("status") String status,
                                             @Param("offset") Integer offset,
                                             @Param("limit") Integer limit);

    /**
     * 统计当前用户或家庭可见的指定状态草稿数量，用于只读待办聚合。
     */
    int countVisibleByStatus(@Param("userId") Long userId,
                             @Param("familyId") Long familyId,
                             @Param("status") String status);

    /**
     * 更新 DRAFT 状态草稿的候选内容，不修改确认或忽略追踪字段。
     */
    int updateDraftContent(DraftLedgerEntry draft);

    /**
     * 写入服务端预览 JSON，便于前端直接展示确认摘要。
     */
    int updatePreview(@Param("id") Long id, @Param("previewPayloadJson") String previewPayloadJson);

    /**
     * 将 DRAFT 草稿标记为 CONFIRMED，并记录正式流水或订单关联。
     */
    int markConfirmed(@Param("id") Long id, @Param("confirmTxnId") String confirmTxnId, @Param("confirmOrderId") String confirmOrderId);

    /**
     * 将 DRAFT 草稿标记为 IGNORED，并记录人工忽略原因。
     */
    int markIgnored(@Param("id") Long id, @Param("ignoreReason") String ignoreReason);
}
