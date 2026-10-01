package com.timelordtty.dca.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;

/** All access is constrained by authenticated owner AND current family. */
@Mapper
public interface RiskWatchMapper {
    @Insert("INSERT INTO risk_watch_rule(id,owner_user_id,owner_family_id,payload) VALUES(#{id},#{user},#{family},#{payload})")
    int insertRule(@Param("id") String id,@Param("user") Long user,@Param("family") Long family,@Param("payload") String payload);
    @Select("SELECT payload FROM risk_watch_rule WHERE id=#{id} AND owner_user_id=#{user} AND owner_family_id <=> #{family}")
    String rule(@Param("id") String id,@Param("user") Long user,@Param("family") Long family);
    @Select("SELECT payload FROM risk_watch_rule WHERE owner_user_id=#{user} AND owner_family_id <=> #{family} ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}")
    List<String> rules(@Param("user") Long user,@Param("family") Long family,@Param("offset") int offset,@Param("size") int size);
    @Update("UPDATE risk_watch_rule SET payload=#{payload} WHERE id=#{id} AND owner_user_id=#{user} AND owner_family_id <=> #{family}")
    int updateRule(@Param("id") String id,@Param("user") Long user,@Param("family") Long family,@Param("payload") String payload);
    @Insert("INSERT INTO risk_watch_snapshot(id,rule_id,owner_user_id,owner_family_id,payload) VALUES(#{id},#{rule},#{user},#{family},#{payload})")
    int insertSnapshot(@Param("id") String id,@Param("rule") String rule,@Param("user") Long user,@Param("family") Long family,@Param("payload") String payload);
    @Select("SELECT payload FROM risk_watch_snapshot WHERE id=#{id} AND owner_user_id=#{user} AND owner_family_id <=> #{family}")
    String snapshot(@Param("id") String id,@Param("user") Long user,@Param("family") Long family);
    @Select("SELECT payload FROM risk_watch_snapshot WHERE rule_id=#{rule} AND owner_user_id=#{user} AND owner_family_id <=> #{family} ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}")
    List<String> history(@Param("rule") String rule,@Param("user") Long user,@Param("family") Long family,@Param("offset") int offset,@Param("size") int size);

    @Select("SELECT payload FROM risk_watch_rule WHERE id=#{id} AND owner_user_id=#{user} AND owner_family_id <=> #{family} FOR UPDATE")
    String lockRule(@Param("id") String id,@Param("user") Long user,@Param("family") Long family);
    @Insert("INSERT INTO risk_watch_event(fingerprint,rule_id,owner_user_id,owner_family_id,payload) VALUES(#{id},#{rule},#{user},#{family},#{payload}) ON DUPLICATE KEY UPDATE fingerprint=fingerprint")
    int insertEvent(@Param("id") String id,@Param("rule") String rule,@Param("user") Long user,@Param("family") Long family,@Param("payload") String payload);
    @Select("SELECT payload FROM risk_watch_event WHERE rule_id=#{rule} AND owner_user_id=#{user} AND owner_family_id <=> #{family} AND (#{unresolved}=FALSE OR resolved_at IS NULL) ORDER BY created_at DESC,fingerprint DESC LIMIT #{size} OFFSET #{offset}")
    List<String> events(@Param("rule") String rule,@Param("user") Long user,@Param("family") Long family,@Param("unresolved") boolean unresolved,@Param("offset") int offset,@Param("size") int size);
    @Select("SELECT acknowledged_at FROM risk_watch_event WHERE fingerprint=#{id} AND rule_id=#{rule} AND owner_user_id=#{user} AND owner_family_id <=> #{family}")
    java.time.Instant acknowledgedAt(@Param("id") String id,@Param("rule") String rule,@Param("user") Long user,@Param("family") Long family);
    @Select("SELECT resolved_at FROM risk_watch_event WHERE fingerprint=#{id} AND rule_id=#{rule} AND owner_user_id=#{user} AND owner_family_id <=> #{family}")
    java.time.Instant resolvedAt(@Param("id") String id,@Param("rule") String rule,@Param("user") Long user,@Param("family") Long family);
    @Update("UPDATE risk_watch_event SET acknowledged_at=COALESCE(acknowledged_at,CURRENT_TIMESTAMP(6)) WHERE fingerprint=#{id} AND rule_id=#{rule} AND owner_user_id=#{user} AND owner_family_id <=> #{family}")
    int acknowledge(@Param("id") String id,@Param("rule") String rule,@Param("user") Long user,@Param("family") Long family);
    @Update("UPDATE risk_watch_event SET resolved_at=CURRENT_TIMESTAMP(6) WHERE rule_id=#{rule} AND owner_user_id=#{user} AND owner_family_id <=> #{family} AND resolved_at IS NULL")
    int resolve(@Param("rule") String rule,@Param("user") Long user,@Param("family") Long family);
    @Insert("INSERT INTO risk_watch_mute(rule_id,owner_user_id,owner_family_id,muted_until) VALUES(#{rule},#{user},#{family},#{until}) ON DUPLICATE KEY UPDATE muted_until=VALUES(muted_until)")
    int mute(@Param("rule") String rule,@Param("user") Long user,@Param("family") Long family,@Param("until") java.time.Instant until);
    @Select("SELECT muted_until FROM risk_watch_mute WHERE rule_id=#{rule} AND owner_user_id=#{user} AND owner_family_id <=> #{family}")
    java.time.Instant mutedUntil(@Param("rule") String rule,@Param("user") Long user,@Param("family") Long family);
}
