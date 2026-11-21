package com.qgyun.hltgq.hltgqsite.sms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qgyun.hltgq.hltgqsite.sms.entity.SmsReceiver;
import com.qgyun.hltgq.hltgqsite.sms.vo.SmsReceiverVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 短信接收人表 Mapper（短信管理 Tab）。
 * <p>列别名与 VO 属性同名（全局 map-underscore-to-camel-case=false，自定义查询不做下划线自动映射）。
 * <p>表可能尚未创建（用户未执行 sms-receiver-ddl.sql）：调用方 Service 捕获异常后本次不发送，本 Mapper 不做兜底。
 */
public interface SmsReceiverMapper extends BaseMapper<SmsReceiver> {

    String T_RECEIVER = "\"qixiao-apaas\".\"t_auto_hltgq_water_sms_receiver\"";

    /**
     * 接收人分页：bizType 可选过滤；keyword 可选（已含 % 的 LIKE 条件，姓名 / 号码模糊匹配）；
     * 排序 = 类型（每日8点报 → 晚8点报 → 报警短信）→ 更新时间倒序 → 号码。
     */
    @Select("<script>" +
            "SELECT r.id AS id, r.biz_type AS bizType, r.name AS name, r.phone AS phone, " +
            "r.updated_at AS updatedAt " +
            "FROM " + T_RECEIVER + " r " +
            "<where>" +
            "<if test='bizType != null'>AND r.biz_type = #{bizType} </if>" +
            "<if test='keyword != null'>AND (r.name LIKE #{keyword} ESCAPE '!' " +
            "OR r.phone LIKE #{keyword} ESCAPE '!') </if>" +
            "</where>" +
            "ORDER BY CASE r.biz_type WHEN 'daily' THEN 0 WHEN 'evening' THEN 1 WHEN 'alarm' THEN 2 ELSE 9 END, " +
            "r.updated_at DESC NULLS LAST, r.phone ASC" +
            "</script>")
    IPage<SmsReceiverVO> selectReceiverPage(IPage<SmsReceiverVO> page,
                                            @Param("bizType") String bizType,
                                            @Param("keyword") String keyword);

    /** 表内记录总数（0 = 无接收人，页面提示新增；发送时为空即不发） */
    @Select("SELECT COUNT(*) FROM " + T_RECEIVER)
    long countAll();

    /**
     * 某类型的手机号文本清单（发送链路取人；每条记录可能是英文逗号分隔的多个号码，
     * 由 Service 拆分后逐号发送；按创建时间稳定排序）
     */
    @Select("SELECT r.phone FROM " + T_RECEIVER + " r WHERE r.biz_type = #{bizType} " +
            "ORDER BY r.created_at ASC NULLS FIRST, r.phone ASC")
    List<String> selectPhonesByType(@Param("bizType") String bizType);
}
