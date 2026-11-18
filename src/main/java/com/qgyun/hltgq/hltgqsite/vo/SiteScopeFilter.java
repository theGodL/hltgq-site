package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 站点归属范围过滤条件：按站点档案「渠系 ywvyds」所属顶级灌区拆分页面 Tab（花凉亭灌区 / 麻塘湖灌区）。
 * <p>归属口径（业务确认）：站点档案渠系属于麻塘湖灌区子树的站点归麻塘湖，其余站点与空渠系的站点归花凉亭；
 * 麻塘湖水库 / 麻塘湖水库自计井等档案未挂渠系的兜底站点通过 {@link #includeStcds} 显式归入麻塘湖，
 * 并在花凉亭范围通过 {@link #excludeStcds} 排除。
 * <p>匹配顺序（见 {@code IrrigationScopeService#matches}）：显式包含 → 显式排除 → 渠系命中 → 空渠系归属，
 * 保证兜底站不会因「空渠系归花凉亭」再次出现在花凉亭 Tab。
 */
@Data
public class SiteScopeFilter {

    /** 范围标识：hlt=花凉亭灌区 / mth=麻塘湖灌区 */
    private String zone;

    /** 归属渠系 id 集合（含子孙渠系）：站点档案 ywvyds 命中即属于该范围 */
    private List<String> canalIds = new ArrayList<>();

    /** 是否包含档案未挂渠系（ywvyds 为空）的站点：花凉亭为 true，麻塘湖为 false */
    private boolean includeUnassigned;

    /** 额外包含的站点编号（档案 iofhpi）：麻塘湖兜底站（麻塘湖水库、麻塘湖水库自计井） */
    private List<String> includeStcds = new ArrayList<>();

    /** 额外排除的站点编号（档案 iofhpi）：花凉亭范围排除麻塘湖兜底站 */
    private List<String> excludeStcds = new ArrayList<>();
}
