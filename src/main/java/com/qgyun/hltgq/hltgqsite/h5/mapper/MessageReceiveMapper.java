package com.qgyun.hltgq.hltgqsite.h5.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsite.h5.entity.MessageReceive;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * H5 消息中心：消息接收表 Mapper（接收记录按需同步建立 + 已读状态维护）。
 */
public interface MessageReceiveMapper extends BaseMapper<MessageReceive> {

    /**
     * 某类型未读数（当前登录人未读接收记录数，不含告警状态过滤，告警未读单独查询）。
     */
    @Select("SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" " +
            "WHERE user_id = #{userId} AND message_type = #{messageType} AND is_read = '#1#'")
    long countUnread(@Param("userId") String userId, @Param("messageType") String messageType);

    /**
     * 未读告警数：接收记录 JOIN 告警表，仅统计仍为已确认/处理中的告警
     * （已关闭的告警不再计入未读，与列表口径一致）。
     */
    @Select("SELECT COUNT(*) " +
            "FROM \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" r " +
            "JOIN \"qixiao-apaas\".\"t_auto_hltgq_water_alert\" a ON r.message_id = a.id " +
            "WHERE r.user_id = #{userId} AND r.message_type = '#1#' AND r.is_read = '#1#' " +
            "AND a.status IN ('#2#', '#3#')")
    long countAlertUnread(@Param("userId") String userId);

    /**
     * 批量标记已读（幂等：重复标记仅更新同一记录）。
     * <p>不更新 read_time：前端仅消费 isRead，且线上接收表可能缺 read_time 列，避免兼容性风险。
     */
    @Update("<script>" +
            "UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" " +
            "SET is_read = '#2#' " +
            "WHERE user_id = #{userId} AND message_type = #{messageType} AND is_read = '#1#' " +
            "AND message_id IN " +
            "<foreach collection='messageIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    int markRead(@Param("userId") String userId,
                 @Param("messageType") String messageType,
                 @Param("messageIds") List<String> messageIds);

    /**
     * 某类型全部标记已读（当前登录人未读记录，不更新 read_time，理由同 markRead）。
     */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_water_message_receive\" " +
            "SET is_read = '#2#' " +
            "WHERE user_id = #{userId} AND message_type = #{messageType} AND is_read = '#1#'")
    int markReadAll(@Param("userId") String userId, @Param("messageType") String messageType);
}
