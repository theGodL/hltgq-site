package com.qgyun.hltgq.hltgqsite.emergencynotice.service;

import com.qgyun.hltgq.hltgqsite.emergencynotice.EmergencyNoticeDict;
import com.qgyun.hltgq.hltgqsite.emergencynotice.mapper.EmergencyNoticeMapper;
import com.qgyun.hltgq.hltgqsite.emergencynotice.vo.EmergencyNoticeNavTipVO;
import com.qgyun.hltgq.hltgqsite.emergencynotice.vo.EmergencyNoticeRowVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导航页「应急响应 / 响应解除」提示。
 * <p>判定口径（按响应类别分组，各类别相互独立，可同时存在）：
 * <ol>
 *   <li>只看「已发布」通知（草稿、已终止不参与）；</li>
 *   <li>每个类别取上级发布时间（空值回退录入时间）最近的一条作为当前状态；</li>
 *   <li>该条事项为「终止响应」→ 该类别「响应已解除」，否则「应急响应中」；</li>
 *   <li>响应中时等级取最近一次非空等级（「补充工作要求」通常不填等级，需回溯）；</li>
 *   <li>解除时等级取终止通知自身等级（可能为空，不影响提示文案）。</li>
 * </ol>
 * <p>展示时长属前端展示口径：后端只返回事实与建议文案，不做过期过滤。
 */
@Service
public class EmergencyNoticeService {

    private static final Logger log = LoggerFactory.getLogger(EmergencyNoticeService.class);

    @Autowired
    private EmergencyNoticeMapper noticeMapper;

    /**
     * 导航提示。
     * <p>查询异常时降级为「无提示」：导航提示是辅助信息，不应影响导航页其它内容。
     */
    public EmergencyNoticeNavTipVO navTip() {
        EmergencyNoticeNavTipVO tip = new EmergencyNoticeNavTipVO();
        tip.setAppCode(EmergencyNoticeDict.APP_CODE);
        tip.setPageCode(EmergencyNoticeDict.PAGE_CODE);

        List<EmergencyNoticeNavTipVO.Item> items = buildItems(listPublished());
        tip.setItems(items);
        tip.setHasTip(!items.isEmpty());
        tip.setTipType(resolveTipType(items));
        return tip;
    }

    private List<EmergencyNoticeRowVO> listPublished() {
        try {
            List<EmergencyNoticeRowVO> rows = noticeMapper.selectPublished();
            return rows == null ? Collections.<EmergencyNoticeRowVO>emptyList() : rows;
        } catch (Exception e) {
            log.warn("应急通知导航提示查询失败，按无提示返回：{}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 按响应类别分组（保持发布时间倒序），每组取最新一条推导状态 */
    private List<EmergencyNoticeNavTipVO.Item> buildItems(List<EmergencyNoticeRowVO> rows) {
        Map<String, List<EmergencyNoticeRowVO>> byCategory = new LinkedHashMap<>();
        for (EmergencyNoticeRowVO row : rows) {
            if (row == null) {
                continue;
            }
            String category = EmergencyNoticeDict.trimToNull(row.getCategoryCode());
            if (category == null) {
                // 历史脏数据未填类别，无法归属到任一响应类别
                continue;
            }
            List<EmergencyNoticeRowVO> group = byCategory.get(category);
            if (group == null) {
                group = new ArrayList<>();
                byCategory.put(category, group);
            }
            group.add(row);
        }

        List<EmergencyNoticeNavTipVO.Item> items = new ArrayList<>(byCategory.size());
        for (List<EmergencyNoticeRowVO> group : byCategory.values()) {
            items.add(buildItem(group));
        }
        // 响应中在前、已解除在后；同状态保持各类别最新发布时间的先后
        items.sort(Comparator.comparingInt(EmergencyNoticeService::statusRank));
        return items;
    }

    private EmergencyNoticeNavTipVO.Item buildItem(List<EmergencyNoticeRowVO> group) {
        EmergencyNoticeRowVO latest = group.get(0);
        boolean released = EmergencyNoticeDict.isTerminate(latest.getItemCode());

        EmergencyNoticeNavTipVO.Item item = new EmergencyNoticeNavTipVO.Item();
        item.setNoticeId(EmergencyNoticeDict.trimToNull(latest.getNoticeId()));
        item.setTitle(EmergencyNoticeDict.trimToNull(latest.getTitle()));
        item.setCategoryCode(EmergencyNoticeDict.trimToNull(latest.getCategoryCode()));
        item.setCategoryName(EmergencyNoticeDict.categoryLabel(latest.getCategoryCode()));
        item.setItemCode(EmergencyNoticeDict.trimToNull(latest.getItemCode()));
        item.setItemName(EmergencyNoticeDict.itemLabel(latest.getItemCode()));
        item.setPublishUnit(EmergencyNoticeDict.trimToNull(latest.getPublishUnit()));
        item.setPublishTime(EmergencyNoticeDict.trimToNull(latest.getPublishTime()));
        item.setStatus(released ? EmergencyNoticeDict.TIP_RELEASE : EmergencyNoticeDict.TIP_RESPONSE);
        item.setStatusName(released
                ? EmergencyNoticeDict.STATUS_NAME_RELEASE
                : EmergencyNoticeDict.STATUS_NAME_RESPONSE);

        // 解除只认终止通知自身等级；响应中回溯最近一次非空等级（补充工作要求不带等级）
        String levelCode = released
                ? EmergencyNoticeDict.trimToNull(latest.getLevelCode())
                : firstNonBlankLevel(group);
        item.setLevelCode(levelCode);
        item.setLevelName(EmergencyNoticeDict.levelLabel(levelCode));
        item.setTipText(buildTipText(item, released));
        return item;
    }

    private String firstNonBlankLevel(List<EmergencyNoticeRowVO> group) {
        for (EmergencyNoticeRowVO row : group) {
            String level = EmergencyNoticeDict.trimToNull(row.getLevelCode());
            if (level != null) {
                return level;
            }
        }
        return null;
    }

    /** 建议提示文案；等级缺失时退化为不带等级的表述 */
    private String buildTipText(EmergencyNoticeNavTipVO.Item item, boolean released) {
        String category = item.getCategoryName();
        String level = item.getLevelName() == null ? "" : item.getLevelName();
        if (released) {
            return level.isEmpty() ? category + "应急响应已解除" : category + level + "应急响应已解除";
        }
        String itemCode = item.getItemCode();
        if (EmergencyNoticeDict.ITEM_START.equals(itemCode)) {
            return level.isEmpty() ? category + "应急响应启动" : category + level + "应急响应启动";
        }
        if (EmergencyNoticeDict.ITEM_UPGRADE.equals(itemCode)) {
            return level.isEmpty() ? category + "应急响应升级" : category + "应急响应升级至" + level;
        }
        if (EmergencyNoticeDict.ITEM_DOWNGRADE.equals(itemCode)) {
            return level.isEmpty() ? category + "应急响应降级" : category + "应急响应降级至" + level;
        }
        if (EmergencyNoticeDict.ITEM_SUPPLEMENT.equals(itemCode)) {
            return level.isEmpty() ? category + "应急响应持续中" : category + level + "应急响应持续中";
        }
        // 未知事项：通用表述，避免出现空提示
        return level.isEmpty() ? category + "应急响应中" : category + level + "应急响应中";
    }

    /** 排序权重：响应中 0 / 已解除 1 */
    private static int statusRank(EmergencyNoticeNavTipVO.Item item) {
        return EmergencyNoticeDict.TIP_RESPONSE.equals(item.getStatus()) ? 0 : 1;
    }

    /** 汇总提示类型：存在响应中即 response，否则有解除即 release，都没有则 none */
    private String resolveTipType(List<EmergencyNoticeNavTipVO.Item> items) {
        boolean hasRelease = false;
        for (EmergencyNoticeNavTipVO.Item item : items) {
            if (EmergencyNoticeDict.TIP_RESPONSE.equals(item.getStatus())) {
                return EmergencyNoticeDict.TIP_RESPONSE;
            }
            hasRelease = true;
        }
        return hasRelease ? EmergencyNoticeDict.TIP_RELEASE : EmergencyNoticeDict.TIP_NONE;
    }
}
