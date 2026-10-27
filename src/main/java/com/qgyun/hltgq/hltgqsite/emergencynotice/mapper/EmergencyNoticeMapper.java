package com.qgyun.hltgq.hltgqsite.emergencynotice.mapper;

import com.qgyun.hltgq.hltgqsite.emergencynotice.vo.EmergencyNoticeRowVO;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 应急通知（t_auto_hltgq_bcmxdv_lmqaew）只读查询。
 * <p>与低代码共用表：列名为平台拼音列，选项列存 {@code #选项 value#}，中文翻译由
 * {@code EmergencyNoticeDict} 完成。
 * <p>导航提示口径：只认「已发布」（cgkpil = {@code #ucpd#}），草稿与被终止的通知不参与判定；
 * 排序按上级发布时间倒序，空值回退录入时间（innfii 为 timestamp，统一 to_char 输出到分钟）。
 */
public interface EmergencyNoticeMapper {

    String T_NOTICE = "\"qixiao-apaas\".\"t_auto_hltgq_bcmxdv_lmqaew\"";

    /**
     * 全部已发布通知（发布时间倒序）。
     * <p>LIMIT 200 防御无界增长：导航提示只需要每个响应类别最近一条，
     * 正常业务下 200 条足以覆盖全部类别与等级回溯。
     */
    @Select("SELECT id AS noticeId, ypdfei AS title, vqpwue AS categoryCode, pxkzdz AS itemCode, " +
            "sycosi AS levelCode, ioiwki AS publishUnit, " +
            "to_char(COALESCE(innfii, created_at), 'YYYY-MM-DD HH24:MI') AS publishTime " +
            "FROM " + T_NOTICE + " " +
            "WHERE corp_code = 'hltgq' AND cgkpil = '#ucpd#' " +
            "ORDER BY COALESCE(innfii, created_at) DESC NULLS LAST, created_at DESC, id DESC LIMIT 200")
    List<EmergencyNoticeRowVO> selectPublished();
}
