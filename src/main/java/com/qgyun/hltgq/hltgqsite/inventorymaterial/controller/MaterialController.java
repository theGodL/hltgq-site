package com.qgyun.hltgq.hltgqsite.inventorymaterial.controller;

import com.qgyun.hltgq.hltgqsite.inventorymaterial.service.MaterialService;
import com.qgyun.hltgq.hltgqsite.inventorymaterial.vo.MaterialPageItemVO;
import com.qgyun.hltgq.hltgqsite.inventorymaterial.vo.MaterialSaveVO;
import com.qgyun.hltgq.hltgqsite.model.util.ApiResultUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 物资目录（库存管理 / 应急物资管理共用页面）。
 * <p>同一页面双模式：不传 scope 或 scope=common 为库存管理（类别默认常用物资、可选择）；
 * scope=emergency 为应急物资管理（类别强制应急物资、前端隐藏）。
 * <p>权限：与库存管理同口径，所有登录用户可用（拦截器统一鉴权）。
 */
@RestController
@RequestMapping("/inventory-material")
public class MaterialController {

    private static final Logger log = LoggerFactory.getLogger(MaterialController.class);

    @Autowired
    private MaterialService materialService;

    /**
     * 表单元数据：启用仓库、类别选项、单位候选、当前视角应有类别。
     *
     * @param scope 页面视角（可选）：emergency 应急物资；不传/common = 常用物资
     */
    @GetMapping("/meta")
    public Map<String, Object> meta(@RequestParam(required = false) String scope) {
        return materialService.meta(scope);
    }

    /**
     * 目录分页（按页面 scope 隔离类别）。
     *
     * @param page        页码（默认 1）
     * @param size        每页条数（默认 20，上限 100）
     * @param keyword     物资编号/名称/品牌关键字（可选）
     * @param warehouseId 默认仓库 ID（可选）
     * @param scope       页面视角（可选）：emergency 应急物资；不传/common = 常用物资
     * @return total / page / size / pages / records
     */
    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String warehouseId,
                                    @RequestParam(required = false) String scope) {
        return materialService.page(page, size, keyword, warehouseId, scope);
    }

    /**
     * 单条详情（编辑回显）。
     *
     * @param id    物资 ID（必填）
     * @param scope 页面视角（可选）：emergency 应急物资；不传/common = 常用物资
     */
    @GetMapping("/detail")
    public MaterialPageItemVO detail(@RequestParam String id,
                                     @RequestParam(required = false) String scope) {
        return materialService.detail(id, scope);
    }

    /**
     * 导出（条件与列表一致，全量无分页，前端生成 Excel）。
     */
    @GetMapping("/export")
    public List<MaterialPageItemVO> export(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String warehouseId,
                                           @RequestParam(required = false) String scope) {
        return materialService.exportRows(keyword, warehouseId, scope);
    }

    /**
     * 新增（编号留空自动生成 WZ+时间戳；应急视角类别强制应急物资）。
     *
     * @return 保存后的物资行
     */
    @PostMapping("/save")
    public MaterialPageItemVO save(@RequestBody MaterialSaveVO body) {
        log.info("收到物资目录新增请求：scope={}，name={}，category={}",
                body == null ? null : body.getScope(),
                body == null ? null : body.getName(),
                body == null ? null : body.getCategory());
        return materialService.save(body);
    }

    /**
     * 编辑（类别按当前视角归一；跨视角按 id 拒绝）。
     *
     * @return 编辑后的物资行
     */
    @PostMapping("/update")
    public MaterialPageItemVO update(@RequestBody MaterialSaveVO body) {
        log.info("收到物资目录编辑请求：scope={}，id={}，name={}",
                body == null ? null : body.getScope(),
                body == null ? null : body.getId(),
                body == null ? null : body.getName());
        return materialService.update(body);
    }

    /**
     * 删除（有台账/流水引用时拒绝）。
     *
     * @param id    物资 ID（必填）
     * @param scope 页面视角（可选）：emergency 应急物资；不传/common = 常用物资
     */
    @PostMapping("/delete")
    public Map<String, Object> delete(@RequestParam String id,
                                      @RequestParam(required = false) String scope) {
        log.info("收到物资目录删除请求：scope={}，id={}", scope, id);
        materialService.delete(id, scope);
        return ApiResultUtils.ok();
    }
}
