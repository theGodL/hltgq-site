package com.qgyun.hltgq.hltgqsite.service.impl;

import com.qgyun.hltgq.hltgqsite.entity.Canal;
import com.qgyun.hltgq.hltgqsite.mapper.CanalMapper;
import com.qgyun.hltgq.hltgqsite.service.CanalService;
import com.qgyun.hltgq.hltgqsite.vo.CanalSortGroupVO;
import com.qgyun.hltgq.hltgqsite.vo.CanalTreeNodeVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 渠系服务实现：渠系树 BFS 子孙收集（渠系表全量几十行，内存组树无性能问题）+ 渠系排序落库
 */
@Service
public class CanalServiceImpl implements CanalService {

    private static final Logger log = LoggerFactory.getLogger(CanalServiceImpl.class);

    /** 排序分组上限：渠系表全量几十行，防止异常请求批量刷库 */
    private static final int MAX_GROUPS = 200;

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

    @Override
    public List<CanalTreeNodeVO> tree() {
        // 查询已按 created_at 倒序（与平台树接口展示顺序一致），建树时保持该顺序
        List<Canal> canals = canalMapper.selectAllWithTime();
        Map<String, CanalTreeNodeVO> voById = new LinkedHashMap<>();
        for (Canal c : canals) {
            String id = trim(c.getId());
            if (id == null) {
                continue;
            }
            CanalTreeNodeVO vo = new CanalTreeNodeVO();
            vo.setId(id);
            vo.setName(c.getName());
            vo.setParentId(trim(c.getParentId()));
            vo.setChildren(new ArrayList<>());
            voById.put(id, vo);
        }
        List<CanalTreeNodeVO> roots = new ArrayList<>();
        for (CanalTreeNodeVO vo : voById.values()) {
            // 上级不存在的脏数据挂到顶级，避免记录在页面上丢失
            CanalTreeNodeVO parent = vo.getParentId() == null ? null : voById.get(vo.getParentId());
            if (parent != null && parent != vo) {
                parent.getChildren().add(vo);
            } else {
                roots.add(vo);
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveOrder(List<CanalSortGroupVO> groups) {
        if (groups == null || groups.isEmpty()) {
            throw new IllegalArgumentException("未提交任何排序分组");
        }
        if (groups.size() > MAX_GROUPS) {
            throw new IllegalArgumentException("排序分组数量超出上限：" + groups.size());
        }
        List<Canal> canals = canalMapper.selectAllWithTime();
        Map<String, Canal> byId = new LinkedHashMap<>();
        Map<String, List<Canal>> childrenMap = new LinkedHashMap<>();
        for (Canal c : canals) {
            String id = trim(c.getId());
            if (id == null) {
                continue;
            }
            byId.put(id, c);
            // 父级为 null（顶级渠系）时使用同一个占位键，便于直接取值
            childrenMap.computeIfAbsent(parentKey(c.getParentId()), k -> new ArrayList<>()).add(c);
        }

        Set<String> seenParents = new HashSet<>();
        int updated = 0;
        for (CanalSortGroupVO group : groups) {
            if (group == null) {
                continue;
            }
            String parentId = trim(group.getParentId());
            String key = parentKey(parentId);
            if (!seenParents.add(key)) {
                throw new IllegalArgumentException("同一上级渠系重复提交：" + key);
            }
            if (parentId != null && !byId.containsKey(parentId)) {
                throw new IllegalArgumentException("上级渠系不存在：" + parentId);
            }
            List<Canal> children = childrenMap.get(key);
            // 同级不足 2 条时顺序无意义，跳过
            if (children == null || children.size() < 2) {
                continue;
            }

            // 目标顺序：提交清单中属于本组的渠系在前（去重，忽略不属于本组的 id），
            // 未提交的渠系按当前展示顺序追加在后
            Set<String> childIds = new LinkedHashSet<>();
            for (Canal c : children) {
                childIds.add(trim(c.getId()));
            }
            List<String> target = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            if (group.getCanalIds() != null) {
                for (String raw : group.getCanalIds()) {
                    String id = trim(raw);
                    if (id == null || !childIds.contains(id) || !seen.add(id)) {
                        continue;
                    }
                    target.add(id);
                }
            }
            for (Canal c : children) {
                String id = trim(c.getId());
                if (seen.add(id)) {
                    target.add(id);
                }
            }

            // 时间槽：本组现存创建时间升序集合（集合不变，只换渠系，不引入新时间）
            List<LocalDateTime> slots = new ArrayList<>();
            for (Canal c : children) {
                if (c.getCreatedAt() != null) {
                    slots.add(c.getCreatedAt());
                }
            }
            Collections.sort(slots);
            // 严格升序：毫秒精度下同值无法保证先后，重复值后移 1ms
            for (int i = 1; i < slots.size(); i++) {
                if (!slots.get(i).isAfter(slots.get(i - 1))) {
                    slots.set(i, slots.get(i - 1).plusNanos(1_000_000L));
                }
            }

            // 平台按 created_at 倒序展示：目标顺序第一项拿最大时间，依次向前分配
            int n = Math.min(target.size(), slots.size());
            for (int i = 0; i < n; i++) {
                String id = target.get(i);
                LocalDateTime newTime = slots.get(n - 1 - i);
                Canal old = byId.get(id);
                if (old != null && old.getCreatedAt() != null && old.getCreatedAt().compareTo(newTime) == 0) {
                    continue;
                }
                canalMapper.updateCreatedAt(id, newTime);
                updated++;
            }
        }
        log.info("渠系排序保存：提交分组 {} 个，改写 {} 条记录", groups.size(), updated);
        return updated;
    }

    /** 上级渠系归一：null 与空白统一为顶级占位键 */
    private static String parentKey(String parentId) {
        String id = trim(parentId);
        return id == null ? "" : id;
    }

    /** 标识归一：去首尾空白，空串按 null 处理 */
    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }
}
