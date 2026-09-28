package com.timelordtty.dca.model;

import java.time.LocalDateTime;

/** 不包含原始输入的草稿审计事实。 */
public class DraftLifecycleEvent {
    private Long id;
    private Long draftId;
    private String eventType;
    private Long actorUserId;
    private String sourceType;
    private String statusBefore;
    private String statusAfter;
    private String summary;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDraftId() { return draftId; }
    public void setDraftId(Long draftId) { this.draftId = draftId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getStatusBefore() { return statusBefore; }
    public void setStatusBefore(String statusBefore) { this.statusBefore = statusBefore; }
    public String getStatusAfter() { return statusAfter; }
    public void setStatusAfter(String statusAfter) { this.statusAfter = statusAfter; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
