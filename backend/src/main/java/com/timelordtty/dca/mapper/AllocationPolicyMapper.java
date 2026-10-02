package com.timelordtty.dca.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;

/** Authenticated owner AND current family constrain every policy access. */
@Mapper
public interface AllocationPolicyMapper {
    @Insert("INSERT INTO allocation_policy(id,owner_user_id,owner_family_id,payload) VALUES(#{id},#{user},#{family},#{payload})")
    int insert(@Param("id") String id, @Param("user") Long user, @Param("family") Long family, @Param("payload") String payload);
    @Select("SELECT payload FROM allocation_policy WHERE id=#{id} AND owner_user_id=#{user} AND owner_family_id <=> #{family}")
    String detail(@Param("id") String id, @Param("user") Long user, @Param("family") Long family);
    @Select("SELECT payload FROM allocation_policy WHERE owner_user_id=#{user} AND owner_family_id <=> #{family} ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}")
    List<String> list(@Param("user") Long user, @Param("family") Long family, @Param("offset") int offset, @Param("size") int size);
    @Update("UPDATE allocation_policy SET payload=#{payload} WHERE id=#{id} AND owner_user_id=#{user} AND owner_family_id <=> #{family}")
    int update(@Param("id") String id, @Param("user") Long user, @Param("family") Long family, @Param("payload") String payload);
}
