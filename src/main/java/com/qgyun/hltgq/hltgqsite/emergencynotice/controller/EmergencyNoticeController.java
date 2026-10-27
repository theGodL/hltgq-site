package com.qgyun.hltgq.hltgqsite.emergencynotice.controller;

import com.qgyun.hltgq.hltgqsite.emergencynotice.service.EmergencyNoticeService;
import com.qgyun.hltgq.hltgqsite.emergencynotice.vo.EmergencyNoticeNavTipVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应急通知（导航页提示）。
 * <p>只读接口，登录用户可用（拦截器统一鉴权）；
 * 通知的增删改仍由低代码平台「应急管理-应急通知」完成，本模块不提供写接口。
 */
@RestController
@RequestMapping("/emergency-notice")
public class EmergencyNoticeController {

    @Autowired
    private EmergencyNoticeService noticeService;

    /**
     * 导航页「应急响应 / 响应解除」提示。
     * <p>按响应类别分组返回当前状态（响应中/已解除），并给出建议提示文案与跳转标识
     * （appCode + pageCode）；无已发布通知时 hasTip=false、tipType=none。
     */
    @GetMapping("/nav-tip")
    public EmergencyNoticeNavTipVO navTip() {
        return noticeService.navTip();
    }
}
