package com.qgyun.hltgq.hltgqsite.inventorycheck.controller;

import com.qgyun.hltgq.hltgqsite.inventorycheck.service.StocktakeExportService;
import com.qgyun.hltgq.hltgqsite.inventorycheck.vo.InventoryCheckExportVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;

/**
 * 设备盘点导出数据源接口（/inventory-check）。
 * <p>后端仅返回 JSON 列表（裸 List，无分页），前端取全量后自行生成 Excel/CSV；
 * 一条盘点含多条明细（物资编号/名称/账面库存/盘点数量/盘盈/盘亏/单位）；
 * 附件经文件服务尽力解析文件名/签名地址；实时查询无缓存；走现有登录会话鉴权。
 */
@RestController
@RequestMapping("/inventory-check")
public class StocktakeController {

    @Autowired
    private StocktakeExportService stocktakeExportService;

    /**
     * 盘点记录列表（按盘点日期倒序，裸 List）。
     *
     * @param code      编号模糊（yogpxf），可选
     * @param startDate 盘点日期起 yyyy-MM-dd（含当日，khntiy），可选
     * @param endDate   盘点日期止 yyyy-MM-dd（含当日，khntiy），可选
     */
    @GetMapping("/export")
    public List<InventoryCheckExportVO> export(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            HttpServletRequest request) {
        return stocktakeExportService.list(code, startDate, endDate, request);
    }
}
