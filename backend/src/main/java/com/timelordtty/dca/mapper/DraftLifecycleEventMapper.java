package com.timelordtty.dca.mapper;

import com.timelordtty.dca.model.DraftLifecycleEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DraftLifecycleEventMapper {
    int insert(DraftLifecycleEvent event);
    List<DraftLifecycleEvent> selectByDraftId(@Param("draftId") Long draftId);
}
