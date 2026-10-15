package com.qgyun.hltgq.hltgqsite.duty.service;

import com.qgyun.hltgq.hltgqsite.duty.DutySupport;
import com.qgyun.hltgq.hltgqsite.duty.mapper.DutyRecordMapper;
import com.qgyun.hltgq.hltgqsite.duty.vo.DutyRecordVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 值班记录查询服务（状态口径见《数据表结构汇总.md》第 11 节；字典/日期/文本统一复用 DutySupport）。
 * <p>值班人员列：归属人 ahygpx（新记录 = 一个人的值班记录）；历史记录（ahygpx 为空）回退 rel 人员聚合。
 * <p>人员过滤四通道（关键词先按用户 id/姓名解析出 id，SQL 见 DutyRecordMapper）：
 * 归属人 ahygpx、值班人员关系表 rel_id、多选旧列双路 LIKE（原文 + 解析出的 id）、带班领导；
 * 班次时间/单位新列优先、旧列兜底（展示口径与排班一致）。
 */
@Service
public class DutyRecordService {

    @Autowired
    private DutyRecordMapper mapper;

    /**
     * 值班记录列表。
     *
     * @param date   值班日期（yyyy-MM-dd，含当日），可选
     * @param shift  班次时间模糊（新列格式化文本优先、旧列原文兜底），可选
     * @param unit   所在单位模糊（单位表名称优先、旧列文本兜底），可选
     * @param person 人员：姓名或用户 id（匹配值班人员/带班领导），可选
     */
    public List<DutyRecordVO> list(LocalDate date, String shift, String unit, String person) {
        String dayStart = date == null ? null : date.toString();
        String dayEnd = date == null ? null : date.plusDays(1).toString();
        String personKeyword = DutySupport.trim(person);
        String personId = personKeyword == null ? null : mapper.selectUserId(personKeyword);

        List<DutyRecordVO> rows = mapper.selectDutyList(dayStart, dayEnd, DutySupport.trim(shift),
                DutySupport.trim(unit), personKeyword, personId);
        for (DutyRecordVO row : rows) {
            row.setStatus(DutySupport.recordStatusLabel(row.getStatusCode()));
            row.setDutyDate(DutySupport.fmtDate(row.getDutyDateRaw()));
            row.setDutyDateRaw(null);
        }
        return rows;
    }
}
