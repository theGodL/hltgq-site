package com.qgyun.hltgq.hltgqsite.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 站点档案实体（t_auto_hltgq_5nw74_vnqqef）。
 * <p>用于站点信息 Excel 导入入库（POST /station-data/import）；仅映射导入所需业务列：
 * 渠系类别 ywvyds（存渠系管理表 id）、管理单位 ahieto（档案表自关联 id）、坐标拆分列
 * bviiio_x/bviiio_y（geohash 不写入）；devicecode、核心指标（ccnhtm/ijzsby）及 _c_ 历史
 * 副本列不映射不写入。
 * <p>监测类型 epjutj / 监测方法 nxtggq 为多选，库中以 | 分隔、每项形如 #N#（如 #1#|#5#）。
 * <p>与旧实体 {@link StStinfo}（仅映射 5 列、以 iofhpi 为主键）并存：本实体继承
 * {@link BaseWaterEntity}（id 短 ID 主键 + 审计字段），供导入写入使用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"qixiao-apaas\".\"t_auto_hltgq_5nw74_vnqqef\"")
public class StationArchive extends BaseWaterEntity {

    /** 站点名称（zzkaec，必填） */
    @TableField("\"zzkaec\"")
    private String name;

    /** 站点编号（iofhpi，站码；其他业务表 stcd 用值，导入防重） */
    @TableField("\"iofhpi\"")
    private String code;

    /** 站点位置（mivbcz） */
    @TableField("\"mivbcz\"")
    private String location;

    /** 监测类型（epjutj，多选 #N#|#N#：#1# 水位站、#2# 雨量站、#3# 流量站、#4# 闸站、#5# 视频站、#6# 模型、#7# 墒情、#8# 水质、#9# 气象） */
    @TableField("\"epjutj\"")
    private String typeCodes;

    /** 坐标定位-经度（bviiio_x） */
    @TableField("\"bviiio_x\"")
    private BigDecimal lon;

    /** 坐标定位-纬度（bviiio_y） */
    @TableField("\"bviiio_y\"")
    private BigDecimal lat;

    /** 站点状态（zebpsu：#1# 在线、#2# 离线） */
    @TableField("\"zebpsu\"")
    private String status;

    /** 管理单位（ahieto，档案表自关联站点 id） */
    @TableField("\"ahieto\"")
    private String unitId;

    /** 渠系类别（ywvyds，渠系管理表 t_auto_hltgq_knc3g_egvnhw id） */
    @TableField("\"ywvyds\"")
    private String canalId;

    /** 是否接通市电（waljdn：#1# 是、#2# 否） */
    @TableField("\"waljdn\"")
    private String mainsPower;

    /** 监测方法（nxtggq，多选 #N#|#N#：#1# 水工建筑物法…#8# 土壤墒情） */
    @TableField("\"nxtggq\"")
    private String methodCodes;

    /** 传输方式（bhsqxd） */
    @TableField("\"bhsqxd\"")
    private String comm;

    /** 负责人（lhwhuc） */
    @TableField("\"lhwhuc\"")
    private String owner;

    /** 联系电话（cbitue，手机格式） */
    @TableField("\"cbitue\"")
    private String phone;

    /** 介绍（viwmmc，文本域） */
    @TableField("\"viwmmc\"")
    private String intro;

    /** 模型类型（discharge_type） */
    @TableField("\"discharge_type\"")
    private String modelType;

    /** 操作说明（badfhe） */
    @TableField("\"badfhe\"")
    private String operationNote;
}
