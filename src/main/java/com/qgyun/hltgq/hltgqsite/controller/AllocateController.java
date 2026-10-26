package com.qgyun.hltgq.hltgqsite.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qgyun.hltgq.hltgqsite.entity.AllocateRecord;
import com.qgyun.hltgq.hltgqsite.entity.AllocateTenday;
import com.qgyun.hltgq.hltgqsite.mapper.AllocateRecordMapper;
import com.qgyun.hltgq.hltgqsite.mapper.AllocateTendayMapper;
import com.qgyun.hltgq.hltgqsite.model.client.ModelClient;
import com.qgyun.hltgq.hltgqsite.model.service.AllocateService;
import com.qgyun.hltgq.hltgqsite.model.service.ModelRecordCommonService;
import com.qgyun.hltgq.hltgqsite.model.util.BoolTextUtils;
import com.qgyun.hltgq.hltgqsite.model.util.DownloadHeaderUtils;
import com.qgyun.hltgq.hltgqsite.model.vo.AllocateSubmitRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 水资源配置接口（/water-allocation）。
 * <p>2026-10-26 会议定稿：仅保留参数配置入口（manual 模式与大表上传已删除，upload/template 接口已下线），
 * 结果拆「灌区 / 水库」两个一级视图（详情返回 irrigation / reservoir）。
 */
@RestController
@RequestMapping("/water-allocation")
public class AllocateController {

    @Autowired
    private AllocateService allocateService;

    @Autowired
    private ModelRecordCommonService commonService;

    @Autowired
    private AllocateRecordMapper recordMapper;

    @Autowired
    private AllocateTendayMapper tendayMapper;

    @Autowired
    private ModelClient modelClient;

    /** 提交水资源配置（秒回 recordId），后台异步调模型计算 */
    @PostMapping
    public Map<String, Object> submit(@RequestBody AllocateSubmitRequest req) {
        Map<String, Object> result = new HashMap<>();
        result.put("recordId", allocateService.submit(req));
        return result;
    }

    /** 轮询配置方案执行状态：{id, status, errorMsg} */
    @GetMapping("/status/{id}")
    public Map<String, Object> status(@PathVariable String id) {
        return commonService.status(id, recordMapper);
    }

    /**
     * 下载水资源配置结果 Excel：优先取计算完成时存档的方案快照（按 recordId 隔离，防串档），
     * 快照缺失（历史方案/快照存档失败）时降级实时代理模型下载接口。
     * 已删除方案拒绝下载（快照已清理，降级代理会拿到其他方案文件）。
     */
    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@RequestParam String recordId) {
        AllocateRecord record = commonService.require(recordId, recordMapper);
        if (BoolTextUtils.TRUE.equals(record.getDelFlag())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "方案已删除");
        }
        byte[] bytes = allocateService.readExcelSnapshot(recordId);
        if (bytes == null) {
            bytes = modelClient.downloadBytes(ModelClient.PATH_ALLOCATE + "/download");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        DownloadHeaderUtils.attachment("水资源配置结果_" + recordId + ".xlsx"))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    /** 配置方案详情：主表 + 旬尺度明细（旧契约 tendays，过渡期保留）+ 灌区/水库两视图（新契约） */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        AllocateRecord record = commonService.require(id, recordMapper);
        QueryWrapper<AllocateTenday> wrapper = new QueryWrapper<>();
        wrapper.eq("\"record_id\"", id).orderByAsc("\"sort_order\"");
        List<AllocateTenday> tendays = tendayMapper.selectList(wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record);
        result.put("tendays", tendays);
        result.putAll(allocateService.buildViews(record));
        return result;
    }

    /** 配置历史方案列表 */
    @GetMapping("/list")
    public List<AllocateRecord> list() {
        return commonService.list(recordMapper);
    }

    /** 配置方案重命名：body {"name": "新名称"} */
    @PutMapping("/{id}/name")
    public Map<String, Object> rename(@PathVariable String id, @RequestBody Map<String, String> body) {
        commonService.rename(id, body == null ? null : body.get("name"), recordMapper);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }

    /** 配置方案逻辑删除（del_flag=#1#），同步清理方案 Excel 快照 */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        commonService.softDelete(id, recordMapper);
        allocateService.deleteExcelSnapshot(id);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }
}
