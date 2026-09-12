package com.qgyun.hltgq.hltgqsite.stationdata.service;

import com.qgyun.hltgq.hltgqsite.stationdata.mapper.StationDataMapper;
import com.qgyun.hltgq.hltgqsite.stationdata.vo.StationDataVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        List<StationDataVO> rows = mapper.selectStationList(trimToNull(name), trimToNull(code),
                resolveCode(STATUS_LABELS, status));
        for (StationDataVO row : rows) {
            row.setType(translateMulti(TYPE_LABELS, row.getTypeCodes()));
            row.setStatus(translate(STATUS_LABELS, row.getStatusCode()));
            row.setMethod(translateMulti(METHOD_LABELS, row.getMethodCodes()));
            if (row.getLon() != null && row.getLat() != null) {
                row.setLnglat(row.getLon().toPlainString() + ", " + row.getLat().toPlainString());
            }
        }
        return rows;
    }

    /** 单值编码 → 中文（未知编码原样返回） */
    private String translate(Map<String, String> labels, String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        return labels.getOrDefault(code, code);
    }

    /** 多选编码（| 分隔）→ 中文顿号拼接（空段跳过，未知段原样保留） */
    private String translateMulti(Map<String, String> labels, String codes) {
        if (codes == null || codes.isEmpty()) {
            return codes;
        }
        StringBuilder sb = new StringBuilder();
        for (String part : codes.split("\\|")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(translate(labels, token));
        }
        return sb.toString();
    }

    /** 过滤值归一：空 → null；编码（#...#）原样；中文 → 编码；未知原样下传 */
    private String resolveCode(Map<String, String> labels, String value) {
        String v = trimToNull(value);
        if (v == null || v.startsWith("#")) {
            return v;
        }
        for (Map.Entry<String, String> e : labels.entrySet()) {
            if (e.getValue().equals(v)) {
                return e.getKey();
            }
        }
        return v;
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
