package com.qgyun.hltgq.hltgqsite.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.qgyun.hltgq.hltgqsite.model.client.ModelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型一体化服务健康检查接口。
 * <p>透传模型 /health（status/service/time/files/endpoints），
 * files 任一为 false 表示对应模型/结果文件缺失，该链路可能无法运行，前端可据此展示告警。
 */
@RestController
@RequestMapping("/model")
public class ModelHealthController {

    @Autowired
    private ModelClient modelClient;

    /** 模型服务健康检查：status/service/time/files/endpoints 原样透传，ok 汇总文件是否齐全 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        JsonNode node = modelClient.health();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", "ok".equals(node.path("status").asText()));
        result.put("status", node.path("status").asText());
        result.put("service", node.path("service").asText());
        result.put("time", node.path("time").asText());
        result.put("files", node.path("files"));
        result.put("endpoints", node.path("endpoints"));
        return result;
    }
}
