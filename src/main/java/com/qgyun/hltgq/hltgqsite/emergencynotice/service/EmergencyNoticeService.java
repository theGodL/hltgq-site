package com.qgyun.hltgq.hltgqsite.emergencynotice.service;

import com.qgyun.hltgq.hltgqsite.emergencynotice.EmergencyNoticeDict;
import com.qgyun.hltgq.hltgqsite.emergencynotice.mapper.EmergencyNoticeMapper;
import com.qgyun.hltgq.hltgqsite.emergencynotice.vo.EmergencyNoticeNavTipVO;
import com.qgyun.hltgq.hltgqsite.emergencynotice.vo.EmergencyNoticeRowVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 导航页「应急响应 / 响应解除」提示。
 * <p>判定口径（每条通知独立成一条提示，不按响应类别合并）：
 * <ol>
 *   <li>返回「已发布」和「已终止」的通知，草稿不返回；</li>
 *   <li>`status` 直接反映通知状态：已发布 → `published`，已终止 → `terminated`；
 *       事项为「终止响应」的同属终止类；</li>
 *   <li>终止类只保留发布时间或录入时间在近 {@code RELEASE_WINDOW_DAYS} 天内的，其余按发布时间正常返回；</li>
 *   <li>等级取该条自身等级；为空且非终止时，向更早的同类别通知回溯（「补充工作要求」通常不填等级）；</li>
 *   <li>排序：上级发布时间倒序（空值回退录入时间），同时间再按录入时间倒序。</li>
 * </ol>
 * <p>提示文案 {@code tipText} 直接取该条通知录入的「灌区落实要求」原文，不做字段拼接。
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
            Timestamp cutoff = Timestamp.valueOf(
                    LocalDateTime.now().minusDays(EmergencyNoticeDict.RELEASE_WINDOW_DAYS));
            List<EmergencyNoticeRowVO> rows = noticeMapper.selectForNavTip(cutoff);
            return rows == null ? Collections.<EmergencyNoticeRowVO>emptyList() : rows;
        } catch (Exception e) {
            log.warn("应急通知导航提示查询失败，按无提示返回：{}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 每条已发布通知一条提示，保持查询给出的发布时间倒序 */
    private List<EmergencyNoticeNavTipVO.Item> buildItems(List<EmergencyNoticeRowVO> rows) {
        List<EmergencyNoticeNavTipVO.Item> items = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            EmergencyNoticeRowVO row = rows.get(i);
            if (row == null) {
                continue;
            }
            items.add(buildItem(row, rows, i));
        }
        return items;
    }

    private EmergencyNoticeNavTipVO.Item buildItem(EmergencyNoticeRowVO row,
                                                   List<EmergencyNoticeRowVO> rows,
                                                   int index) {
        boolean terminated = EmergencyNoticeDict.isTerminated(row.getNoticeStatus(), row.getItemCode());

        EmergencyNoticeNavTipVO.Item item = new EmergencyNoticeNavTipVO.Item();
        item.setNoticeId(EmergencyNoticeDict.trimToNull(row.getNoticeId()));
        item.setTitle(EmergencyNoticeDict.trimToNull(row.getTitle()));
        item.setCategoryCode(EmergencyNoticeDict.trimToNull(row.getCategoryCode()));
        item.setCategoryName(EmergencyNoticeDict.categoryLabel(row.getCategoryCode()));
        item.setItemCode(EmergencyNoticeDict.trimToNull(row.getItemCode()));
        item.setItemName(EmergencyNoticeDict.itemLabel(row.getItemCode()));
        item.setPublishUnit(EmergencyNoticeDict.trimToNull(row.getPublishUnit()));
        item.setPublishTime(EmergencyNoticeDict.trimToNull(row.getPublishTime()));
        item.setStatus(terminated ? EmergencyNoticeDict.TIP_TERMINATED : EmergencyNoticeDict.TIP_PUBLISHED);
        item.setStatusName(terminated
                ? EmergencyNoticeDict.STATUS_NAME_TERMINATED
                : EmergencyNoticeDict.STATUS_NAME_PUBLISHED);

        // 等级取该条自身；未填写且非终止时，回溯更早的同类别通知（「补充工作要求」通常不填等级）
        String levelCode = EmergencyNoticeDict.trimToNull(row.getLevelCode());
        if (levelCode == null && !terminated) {
            levelCode = lookupEarlierLevel(rows, index, row.getCategoryCode());
        }
        item.setLevelCode(levelCode);
        item.setLevelName(EmergencyNoticeDict.levelLabel(levelCode));

        // 提示文案直接用通知里录入的「灌区落实要求」原文，不做字段拼接
        String requirement = EmergencyNoticeDict.trimToNull(row.getRequirementText());
        item.setRequirementText(requirement);
        item.setTipText(requirement != null ? requirement : nvl(item.getTitle()));
        return item;
    }

    /** 在更早的同类别通知中找最近一次非空等级（rows 已按发布时间倒序） */
    private String lookupEarlierLevel(List<EmergencyNoticeRowVO> rows, int index, String categoryCode) {
        String category = EmergencyNoticeDict.trimToNull(categoryCode);
        for (int i = index + 1; i < rows.size(); i++) {
            EmergencyNoticeRowVO earlier = rows.get(i);
            if (earlier == null
                    || !Objects.equals(category, EmergencyNoticeDict.trimToNull(earlier.getCategoryCode()))) {
                continue;
            }
            String level = EmergencyNoticeDict.trimToNull(earlier.getLevelCode());
            if (level != null) {
                return level;
            }
        }
        return null;
    }

    /** 去首尾空白，空值归一为空串（文案兜底用） */
    private static String nvl(String value) {
        String trimmed = EmergencyNoticeDict.trimToNull(value);
        return trimmed == null ? "" : trimmed;
    }

    /** 汇总提示类型：存在已发布即 published，否则有终止即 terminated，都没有则 none */
    private String resolveTipType(List<EmergencyNoticeNavTipVO.Item> items) {
        boolean hasTerminated = false;
        for (EmergencyNoticeNavTipVO.Item item : items) {
            if (EmergencyNoticeDict.TIP_PUBLISHED.equals(item.getStatus())) {
                return EmergencyNoticeDict.TIP_PUBLISHED;
            }
            hasTerminated = true;
        }
        return hasTerminated ? EmergencyNoticeDict.TIP_TERMINATED : EmergencyNoticeDict.TIP_NONE;
    }
}
