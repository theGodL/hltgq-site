package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

/**
 * 闸口下拉选项（站档 t_auto_hltgq_5nw74_vnqqef 中 epjutj 含 #4# 闸站的站点）。
 * <p>站档的站点编号列（iofhpi）在闸口类站点普遍未录入，返回后恒为 null，
 * 故下拉只提供 id + name 两项，编号不对外暴露。
 */
@Data
public class GateOptionVO {

    private String id;

    /** 站点名称（zzkaec） */
    private String name;
}
