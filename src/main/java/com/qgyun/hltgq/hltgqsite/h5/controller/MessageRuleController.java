package com.qgyun.hltgq.hltgqsite.h5.controller;

import com.qgyun.hltgq.hltgqsite.auth.RequireAdmin;
import com.qgyun.hltgq.hltgqsite.h5.service.MessageRuleService;
import com.qgyun.hltgq.hltgqsite.h5.vo.MessageRuleMetaVO;
import com.qgyun.hltgq.hltgqsite.h5.vo.MessageRuleSaveVO;
import com.qgyun.hltgq.hltgqsite.h5.vo.MessageRuleVO;
import com.qgyun.hltgq.hltgqsite.inventoryorder.vo.PickerOptionVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 消息发送设置（仅系统管理员）：五类消息的接收规则维护，配合 H5 消息中心按需同步。
 * <p>规则语义：前三类（告警/举报投诉/意见征集）命中规则即可见；值班/模型为附加接收人；
 * 站点范围（site_id）仅告警可用（空=不限站点）。H5 端接口契约不变（/h5/message/*）。
 */
@RequireAdmin
@RestController
@RequestMapping("/message-rule")
public class MessageRuleController {

    private final MessageRuleService messageRuleService;

    public MessageRuleController(MessageRuleService messageRuleService) {
        this.messageRuleService = messageRuleService;
    }

    /** 页面元数据：消息类型（含站点支持/附加接收人语义与说明）、接收维度字典 */
    @GetMapping("/meta")
    public MessageRuleMetaVO meta() {
        return messageRuleService.meta();
    }

    /** 规则分页：messageType 可选过滤（#1#~#5#） */
    @GetMapping("/page")
    public Map<String, Object> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String messageType) {
        return messageRuleService.page(page, size, messageType);
    }

    /** 多选候选：kind = org 部门 / position 岗位 / role 角色 / user 人员 / site 站点 */
    @GetMapping("/options")
    public List<PickerOptionVO> options(@RequestParam String kind) {
        return messageRuleService.options(kind);
    }

    /** 新增规则（类型/维度/目标必填；站点仅告警可传，空=不限站点） */
    @PostMapping
    public MessageRuleVO create(@RequestBody MessageRuleSaveVO body) {
        return messageRuleService.create(body);
    }

    /** 编辑规则（类型与维度锁定，可改目标与站点范围） */
    @PutMapping("/{id}")
    public MessageRuleVO update(@PathVariable String id, @RequestBody MessageRuleSaveVO body) {
        return messageRuleService.update(id, body);
    }

    /** 删除规则 */
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable String id) {
        messageRuleService.delete(id);
        return true;
    }
}
