package com.timelordtty.dca.service;

import com.timelordtty.dca.mapper.DraftLifecycleEventMapper;
import com.timelordtty.dca.model.DraftLifecycleEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 确认失败时独立提交审计事件，避免被业务事务回滚。 */
@Service
public class DraftFailureAuditService {
    private final DraftLifecycleEventMapper mapper;
    public DraftFailureAuditService(DraftLifecycleEventMapper mapper) { this.mapper = mapper; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(Long draftId, Long userId, String sourceType, String type) {
        DraftLifecycleEvent event = new DraftLifecycleEvent();
        event.setDraftId(draftId);
        event.setActorUserId(userId);
        event.setSourceType(sourceType);
        event.setEventType(type);
        event.setStatusBefore("DRAFT");
        event.setStatusAfter("DRAFT");
        event.setSummary("confirm_failed".equals(type)
                ? "确认失败；业务事务已回滚，请重新预览后检查原因"
                : "主人尝试确认；未记录输入原文");
        mapper.insert(event);
    }
}
