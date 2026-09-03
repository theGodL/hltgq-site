package com.qgyun.hltgq.hltgqsite.decision.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qgyun.hltgq.hltgqsite.decision.vo.WaterBranchVO;
import com.qgyun.hltgq.hltgqsite.entity.DecisionBranchDetail;
import com.qgyun.hltgq.hltgqsite.mapper.DecisionBranchDetailMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配水决策拓扑按旬支渠查询（/water-decision/{id}/branches）。
 * <p>一次全量查方案支渠明细（18旬 × 支渠数，约 1500 行），内存按旬过滤 + 按「支渠名+分干渠」聚合。
 * <p>日期→旬映射仅覆盖 5~10 月（灌溉期 18 旬，与模型 TEN_DAY_MAP 一致），越界回退首旬。
 * <p>重名支渠（红旗/朝阳/杨树）按「支渠名+分干渠」映射拓扑节点 key，其余省略 key（前端按 name 匹配）。
 */
@Service
public class WaterBranchService {

    /**
     * 拓扑重名支渠 key 映射：key = 「支渠名|分干渠」→ 拓扑节点 key。
     * 依据线上真实明细数据推导：红旗(太宿干渠直灌面积/总干渠直灌面积)、朝阳(下仓分干渠)、杨树(下仓分干渠/北干渠直灌面积)。
     */
    private static final Map<String, String> DUPLICATE_BRANCH_KEYS;
    static {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("红旗支渠|太宿干渠直灌面积", "hq_ts");
        map.put("红旗支渠|总干渠直灌面积", "hq_zg");
        map.put("朝阳支渠|下仓分干渠", "zy_xc");
        map.put("杨树支渠|下仓分干渠", "ys_xc");
        map.put("杨树支渠|北干渠直灌面积", "ys_bg");
        DUPLICATE_BRANCH_KEYS = map;
    }

    @Autowired
    private DecisionBranchDetailMapper branchDetailMapper;

    /**
     * 查询方案某旬的支渠配水数据。
     *
     * @param recordId 方案 ID
     * @param startDate 起始日期（yyyy-MM-dd），映射到对应旬；不传 = 方案首旬（sort_order 最小）
     */
    public WaterBranchVO branches(String recordId, String startDate) {
        List<DecisionBranchDetail> details = branchDetailMapper.selectList(
                new QueryWrapper<DecisionBranchDetail>().eq("\"record_id\"", recordId));
        String targetLabel = resolveTendayLabel(details, startDate);
        WaterBranchVO vo = new WaterBranchVO();
        vo.setTendayLabel(targetLabel);
        vo.setBranches(aggregate(details, targetLabel));
        return vo;
    }

    /**
     * 确定目标旬：传入日期映射（5~10 月，越界回退首旬）；未传 = 明细最小 sort_order 的旬。
     */
    private String resolveTendayLabel(List<DecisionBranchDetail> details, String startDate) {
        if (startDate != null && !startDate.trim().isEmpty()) {
            String label = dateToTendayLabel(startDate.trim());
            if (label != null) {
                return label;
            }
        }
        return details.stream()
                .filter(d -> d.getTendayLabel() != null && d.getSortOrder() != null)
                .min(Comparator.comparing(DecisionBranchDetail::getSortOrder))
                .map(DecisionBranchDetail::getTendayLabel)
                .orElse(null);
    }

    /**
     * 日期 → 灌溉旬标签（5~10 月：1-10 上旬、11-20 中旬、21-末日 下旬）；非灌溉期返回 null（回退首旬）。
     */
    private String dateToTendayLabel(String date) {
        try {
            LocalDate d = LocalDate.parse(date);
            int month = d.getMonthValue();
            if (month < 5 || month > 10) {
                return null;
            }
            int day = d.getDayOfMonth();
            String phase = day <= 10 ? "上" : (day <= 20 ? "中" : "下");
            return month + "月" + phase + "旬";
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 按「支渠名+分干渠」聚合单旬数据：需水/供水量求和，组内全部满足才算满足。
     * 无 key 的支渠排在前面：前端按 name 兜底索引「先到先得」，保证拓扑同名无 key 节点命中无 key 数据。
     */
    private List<WaterBranchVO.Branch> aggregate(List<DecisionBranchDetail> details, String targetLabel) {
        Map<String, WaterBranchVO.Branch> groupMap = new LinkedHashMap<>();
        for (DecisionBranchDetail d : details) {
            if (targetLabel == null || !targetLabel.equals(d.getTendayLabel())) {
                continue;
            }
            String groupKey = d.getBranchName() + "|" + d.getSubDistrict();
            WaterBranchVO.Branch branch = groupMap.computeIfAbsent(groupKey, k -> {
                WaterBranchVO.Branch b = new WaterBranchVO.Branch();
                b.setBranchName(d.getBranchName());
                b.setDistrict(d.getDistrict());
                b.setSubDistrict(d.getSubDistrict());
                b.setKey(DUPLICATE_BRANCH_KEYS.get(groupKey));
                b.setDemand(0.0);
                b.setSupply(0.0);
                b.setIsSatisfied(Boolean.TRUE);
                return b;
            });
            branch.setDemand(branch.getDemand() + (d.getDemandVolume() == null ? 0 : d.getDemandVolume()));
            branch.setSupply(branch.getSupply() + (d.getSuggestedSupply() == null ? 0 : d.getSuggestedSupply()));
            if (!"#1#".equals(d.getIsSatisfied())) {
                branch.setIsSatisfied(Boolean.FALSE);
            }
        }
        List<WaterBranchVO.Branch> result = new ArrayList<>(groupMap.values());
        result.sort(Comparator
                .comparing((WaterBranchVO.Branch b) -> b.getKey() != null)
                .thenComparing(b -> b.getSubDistrict() == null ? "" : b.getSubDistrict()));
        return result;
    }
}
