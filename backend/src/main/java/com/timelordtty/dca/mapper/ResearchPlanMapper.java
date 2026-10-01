package com.timelordtty.dca.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;

/** Research metadata only. Every read/write uses the authenticated user AND family. */
@Mapper
public interface ResearchPlanMapper {
    @Insert("INSERT INTO research_plan(id, owner_user_id, owner_family_id, payload) VALUES(#{id},#{user},#{family},#{payload})")
    int insert(@Param("id") String id, @Param("user") Long user, @Param("family") Long family, @Param("payload") String payload);

    @Select("SELECT payload FROM research_plan WHERE id=#{id} AND owner_user_id=#{user} AND owner_family_id <=> #{family}")
    String find(@Param("id") String id, @Param("user") Long user, @Param("family") Long family);

    @Select("SELECT payload FROM research_plan WHERE owner_user_id=#{user} AND owner_family_id <=> #{family} ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}")
    List<String> list(@Param("user") Long user, @Param("family") Long family, @Param("offset") int offset, @Param("size") int size);

    @Update("UPDATE research_plan SET payload=#{payload} WHERE id=#{id} AND owner_user_id=#{user} AND owner_family_id <=> #{family} AND BINARY payload=BINARY #{previous}")
    int update(@Param("id") String id, @Param("user") Long user, @Param("family") Long family,
               @Param("payload") String payload, @Param("previous") String previous);
}
