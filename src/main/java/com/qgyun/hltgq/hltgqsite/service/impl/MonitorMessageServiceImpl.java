package com.qgyun.hltgq.hltgqsite.service.impl;

import com.qgyun.hltgq.hltgqsite.mapper.MonitorMessageMapper;
import com.qgyun.hltgq.hltgqsite.service.MonitorMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 监测页通用报文服务实现
 */
@Service
public class MonitorMessageServiceImpl implements MonitorMessageService {

    /** 查询窗口上限（分钟）：防止前端传入超大窗口扫全表 */
    private static final int MAX_WINDOW_MINUTES = 12 * 60;
    /** 单次返回条数上限：报文 JSON 较大，避免一次渲染过多 */
    private static final int MAX_LIMIT = 200;

    @Autowired
    private MonitorMessageMapper monitorMessageMapper;

    @Override
    public List<Map<String, Object>> messages(String key, LocalDateTime tm, int before, int after, int limit) {
        if (key == null || key.trim().isEmpty() || tm == null) {
            return Collections.emptyList();
        }
        int beforeMinutes = Math.max(0, Math.min(before, MAX_WINDOW_MINUTES));
        int afterMinutes = Math.max(0, Math.min(after, MAX_WINDOW_MINUTES));
        int rows = Math.max(1, Math.min(limit, MAX_LIMIT));

        List<Map<String, Object>> raw = monitorMessageMapper.selectByStationWindow(
                key.trim(), tm.minusMinutes(beforeMinutes), tm.plusMinutes(afterMinutes), rows);

        // 与闸门报文接口同口径输出（msg_text → msgText），前端组件可通用
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> r : raw) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", r.get("type"));
            m.put("tag", r.get("tag"));
            m.put("frame", r.get("frame"));
            m.put("tm", r.get("tm"));
            m.put("sendtm", r.get("sendtm"));
            m.put("afn", r.get("afn"));
            m.put("msgText", r.get("msg_text"));
            result.add(m);
        }
        return result;
    }
}
