package com.qgyun.hltgq.hltgqsite.archive.controller;

import com.qgyun.hltgq.hltgqsite.archive.service.ArchiveSyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * 档案系统同步接口：管理后台手动触发。
 * <p>鉴权：请求头 X-API-Key 与 auth.api.key 一致才允许触发。
 */
@RestController
@RequestMapping("/archive/sync")
public class ArchiveSyncController {

    private final ArchiveSyncService syncService;

    @Value("${auth.api.key}")
    private String validKey;

    public ArchiveSyncController(ArchiveSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * 手动触发同步：body {"full": true} 全量，{"full": false} 或不传为增量。
     * <p>同步顺序：先组织后用户；成功返回同步统计。
     */
    @PostMapping("/trigger")
    public Map<String, Object> trigger(@RequestBody(required = false) Map<String, Object> body,
                                       @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        if (validKey == null || !validKey.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "鉴权失败：X-API-Key 不正确");
        }
        boolean full = body != null && Boolean.TRUE.equals(body.get("full"));
        syncService.manualSync(full);
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("mode", full ? "full" : "incremental");
        return result;
    }
}
