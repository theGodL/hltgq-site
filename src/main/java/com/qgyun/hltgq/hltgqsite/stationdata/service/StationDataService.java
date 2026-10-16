package com.qgyun.hltgq.hltgqsite.stationdata.service;

import com.qgyun.hltgq.hltgqsite.stationdata.mapper.StationDataMapper;
import com.qgyun.hltgq.hltgqsite.stationdata.vo.StationCoordsVO;
import com.qgyun.hltgq.hltgqsite.stationdata.vo.StationDataVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.qgyun.hltgq.hltgqsite.model.util.CheckUtils;
import com.qgyun.hltgq.hltgqsite.model.util.DictUtils;

/**
 * 站点数据查询服务：编码 → 中文翻译（口径见《数据表结构汇总.md》第 1 节）。
 * <p>监测类型/监测方法为多选，库中以 | 分隔，译文以「、」拼接；
 * 过滤参数支持中文与编码双写法（未知值原样下传，命中空结果）。
 */
@Service
public class StationDataService {

    /** 监测类型编码 → 名称（epjutj，9 档） */
    private static final Map<String, String> TYPE_LABELS = new LinkedHashMap<>();
    /** 监测方法编码 → 名称（nxtggq，8 档） */
    private static final Map<String, String> METHOD_LABELS = new LinkedHashMap<>();
    /** 在线状态：#1# 在线、#2# 离线（zebpsu） */
    private static final Map<String, String> STATUS_LABELS = new LinkedHashMap<>();
    /** 是否接通市电：#1# 是、#2# 否（waljdn） */
    private static final Map<String, String> POWER_LABELS = new LinkedHashMap<>();

    static {
        TYPE_LABELS.put("#1#", "水位站");
        TYPE_LABELS.put("#2#", "雨量站");
        TYPE_LABELS.put("#3#", "流量站");
        TYPE_LABELS.put("#4#", "闸站");
        TYPE_LABELS.put("#5#", "视频站");
        TYPE_LABELS.put("#6#", "模型");
        TYPE_LABELS.put("#7#", "墒情");
        TYPE_LABELS.put("#8#", "水质");
        TYPE_LABELS.put("#9#", "气象");

        METHOD_LABELS.put("#1#", "水工建筑物法");
        METHOD_LABELS.put("#2#", "全自动测流车");
        METHOD_LABELS.put("#3#", "单垂线流速分布法");
        METHOD_LABELS.put("#4#", "雷达波量水");
        METHOD_LABELS.put("#5#", "雷达测流矩阵");
        METHOD_LABELS.put("#6#", "ADCP 测流");
        METHOD_LABELS.put("#7#", "一体化闸门");
        METHOD_LABELS.put("#8#", "土壤墒情");

        STATUS_LABELS.put("#1#", "在线");
        STATUS_LABELS.put("#2#", "离线");

        POWER_LABELS.put("#1#", "是");
        POWER_LABELS.put("#2#", "否");
    }

    @Autowired
    private StationDataMapper mapper;

    /**
     * 站点列表。
     *
     * @param name   站点名称模糊，可选
     * @param code   站点编号模糊，可选
     * @param status 站点状态：在线/离线 或编码 #1#/#2#，可选
     */
    public List<StationDataVO> list(String name, String code, String status) {
        List<StationDataVO> rows = mapper.selectStationList(CheckUtils.trim(name), CheckUtils.trim(code),
                DictUtils.resolveCode(STATUS_LABELS, status));
        for (StationDataVO row : rows) {
            row.setType(DictUtils.labelMulti(TYPE_LABELS, row.getTypeCodes()));
            row.setStatus(DictUtils.label(STATUS_LABELS, row.getStatusCode()));
            row.setMethod(DictUtils.labelMulti(METHOD_LABELS, row.getMethodCodes()));
            row.setMainsPower(DictUtils.label(POWER_LABELS, row.getMainsPowerCode()));
        }
        return rows;
    }

    /**
     * 批量站点经纬度（按站点档案主键 IN 查询）。
     * <p>id 列表去空去重（保序）；空列表直接返回空（避免 IN () 语法错误）；
     * 未命中/非本企业/坐标未填的行均不出现在结果中（或 lon/lat 为 null）。
     *
     * @param ids 站点档案主键列表（可含空白项/重复项，容忍）
     */
    public List<StationCoordsVO> coords(List<String> ids) {
        Set<String> unique = new LinkedHashSet<>();
        if (ids != null) {
            for (String id : ids) {
                String t = CheckUtils.trim(id);
                if (t != null) {
                    unique.add(t);
                }
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyList();
        }
        return mapper.selectCoordsByIds(new ArrayList<>(unique));
    }
}
