package com.qgyun.hltgq.hltgqsite.weather.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 活跃台风列表响应 VO（方案 §5.2 ①）
 * <p>「活跃」判定为上游列表项下标 `7 == "start"`；非台风季正常返回空列表，
 * 上游失败且无可用缓存时同样降级为 `count=0` + `list=[]`，不抛 5xx（方案 §5.5）。
 */
@Data
public class TyphoonActiveVO {

    /** 活跃台风数量（= list.size()） */
    private Integer count = 0;

    /** 活跃台风列表（顺序与上游 `list_default` 一致，不做后端重排） */
    private List<TyphoonItemVO> list = new ArrayList<>();
}
