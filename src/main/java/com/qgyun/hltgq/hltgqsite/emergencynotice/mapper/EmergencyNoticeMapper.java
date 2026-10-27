package com.qgyun.hltgq.hltgqsite.emergencynotice.mapper;

import com.qgyun.hltgq.hltgqsite.emergencynotice.vo.EmergencyNoticeRowVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.sql.Timestamp;
import java.util.List;

/**
 * 应急通知（t_auto_hltgq_bcmxdv_lmqaew）只读查询。
 * <p>与低代码共用表：列名为平台拼音列，选项列存 {@code #选项 value#}，中文翻译由
 * {@code EmergencyNoticeDict} 完成。
 * <p>导航提示口径：返回「已发布」与「已终止」的通知（草稿不返回），每条通知独立返回、不按响应类别合并。
 * <p>终止类（状态「已终止」或事项「终止响应」）只保留发布（或录入）时间在 {@code terminatedCutoff} 之后的，
 * 避免终止提示永久残留；其余按发布时间正常返回。
 * <p>排序：上级发布时间倒序（空值回退录入时间），同时间再按录入时间倒序。
 */
public interface EmergencyNoticeMapper {

    String T_NOTICE = "\"qixiao-apaas\".\"t_auto_hltgq_bcmxdv_lmqaew\"";

    /**
     * 已发布 + 已终止通知（发布时间倒序）。
     * <p>终止类按发布时间或录入时间任一在 cutoff 之后即返回：上级发布时间非必填、可能被补录成历史日期，
     * 只用发布时间过滤会漏掉刚终止的通知。
     * <p>LIMIT 200 防御无界增长：导航提示只需要近期通知，正常业务下足够覆盖。
     */
    @Select("SELECT id AS noticeId, ypdfei AS title, vqpwue AS categoryCode, pxkzdz AS itemCode, " +
            "sycosi AS levelCode, ioiwki AS publishUnit, cgkpil AS noticeStatus, " +
            "to_char(COALESCE(innfii, created_at), 'YYYY-MM-DD HH24:MI') AS publishTime, " +
            "tbznmf AS requirementText " +
            "FROM " + T_NOTICE + " " +
            "WHERE corp_code = 'hltgq' AND cgkpil IN ('#ucpd#', '#mevt#') " +
            "AND ((cgkpil = '#ucpd#' AND COALESCE(pxkzdz, '') <> '#doga#') " +
            "OR COALESCE(innfii, created_at) >= #{terminatedCutoff} " +
            "OR created_at >= #{terminatedCutoff}) " +
            "ORDER BY COALESCE(innfii, created_at) DESC NULLS LAST, created_at DESC, id DESC LIMIT 200")
    List<EmergencyNoticeRowVO> selectForNavTip(@Param("terminatedCutoff") Timestamp terminatedCutoff);
}
