package com.qgyun.hltgq.hltgqsite.controller;

import com.qgyun.hltgq.hltgqsite.auth.RequireAdmin;
import com.qgyun.hltgq.hltgqsite.service.CanalService;
import com.qgyun.hltgq.hltgqsite.vo.CanalSortGroupVO;
import com.qgyun.hltgq.hltgqsite.vo.CanalSortSaveVO;
import com.qgyun.hltgq.hltgqsite.vo.CanalTreeNodeVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 渠系排序：「渠系排序」页面（canal-sort.html）的渠系树查询与顺序保存。
 * <p>渠系管理表没有独立排序字段，平台层级树接口（qxlkaRelLevelRelQuery）对同级节点按
 * created_at 倒序展示；保存排序即按目标顺序重排同级渠系的 created_at（时间值集合不变，只换渠系），
 * 五个监测页左侧「渠系结构」侧边栏与平台渠系管理页的顺序随之变化。
 */
@RestController
@RequestMapping("/canal")
public class CanalController {

    private static final Logger log = LoggerFactory.getLogger(CanalController.class);

    @Autowired
    private CanalService canalService;

    /**
     * 渠系树：同级按平台展示顺序（created_at 倒序）排列，供渠系排序页面生成分组清单
     */
    @GetMapping("/tree")
    public List<CanalTreeNodeVO> tree() {
        return canalService.tree();
    }

    /**
     * 保存渠系排序（仅系统管理员）：groups 按分组提交同级渠系顺序，组内从上到下即保存后的展示顺序
     *
     * @return success + count（改写的记录数）
     */
    @RequireAdmin
    @PostMapping("/sort")
    public Map<String, Object> save(@RequestBody CanalSortSaveVO body) {
        List<CanalSortGroupVO> groups = body == null ? null : body.getGroups();
        log.info("收到渠系排序保存请求：分组数={}", groups == null ? 0 : groups.size());
        int count = canalService.saveOrder(groups);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("count", count);
        return result;
    }
}
