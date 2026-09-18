package com.qgyun.hltgq.hltgqsite.service.impl;

import com.qgyun.hltgq.hltgqsite.entity.Canal;
import com.qgyun.hltgq.hltgqsite.mapper.CanalMapper;
import com.qgyun.hltgq.hltgqsite.service.CanalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 渠系服务实现：渠系树 BFS 子孙收集（渠系表全量几十行，内存组树无性能问题）
 */
@Service
public class CanalServiceImpl implements CanalService {

    @Autowired
    private CanalMapper canalMapper;

    @Override
    public List<String> collectDescendantCanalIds(String canalId) {
        if (canalId == null || canalId.trim().isEmpty()) {
            return null;
        }
        List<Canal> canals = canalMapper.selectAll();
        Map<String, List<Canal>> childrenMap = new LinkedHashMap<>();
        for (Canal c : canals) {
            childrenMap.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(canalId);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (cur == null || !ids.add(cur)) {
                continue;
            }
            List<Canal> children = childrenMap.get(cur);
            if (children != null) {
                for (Canal child : children) {
                    if (child.getId() != null) {
                        queue.add(child.getId());
                    }
                }
            }
        }
        return new ArrayList<>(ids);
    }
}
