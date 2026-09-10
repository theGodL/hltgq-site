package com.qgyun.hltgq.hltgqsite.irrigation.controller;

import com.qgyun.hltgq.hltgqsite.irrigation.service.IrrigationWaterService;
import com.qgyun.hltgq.hltgqsite.irrigation.vo.IrrigationSchemeOptionVO;
import com.qgyun.hltgq.hltgqsite.irrigation.vo.IrrigationSummaryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 灌溉用水接口（/irrigation-water）。
 * <p>页面：static/irrigation-water.html（需水方案多选 → 四县需水/供水/保证率列表）。
 * <p>取数口径与配置见 {@link com.qgyun.hltgq.hltgqsite.irrigation.config.IrrigationStationConfig}。
 */
@RestController
@RequestMapping("/irrigation-water")
public class IrrigationWaterController {

    @Autowired
    private IrrigationWaterService irrigationWaterService;

    /**
     * 需水方案下拉选项（仅已完成方案，按创建时间倒序）。
     *
     * @param year 计算年（可选，缺省=方案创建年；用于生成需水时间起止日期）
     */
    @GetMapping("/schemes")
    public List<IrrigationSchemeOptionVO> schemes(@RequestParam(required = false) Integer year) {
        return irrigationWaterService.schemes(year);
    }

    /**
     * 灌溉用水汇总：选中方案 × 四县（宿松/怀宁/望江/太湖），含需水时间/需水量/实际供水量/设计保证率/实际保证率/灌溉进度。
     *
     * @param schemeIds 方案ID列表（逗号分隔或重复参数，单次最多 10 个）
     * @param year      计算年（可选，缺省=方案创建年）
     */
    @GetMapping("/summary")
    public List<IrrigationSummaryVO> summary(@RequestParam List<String> schemeIds,
                                             @RequestParam(required = false) Integer year) {
        return irrigationWaterService.summary(schemeIds, year);
    }
}
