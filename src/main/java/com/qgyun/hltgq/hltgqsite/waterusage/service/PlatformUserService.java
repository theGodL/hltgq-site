package com.qgyun.hltgq.hltgqsite.waterusage.service;

import com.qgyun.hltgq.hltgqsite.waterusage.WaterUsageSupport;
import com.qgyun.hltgq.hltgqsite.waterusage.mapper.WaterUserMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 平台用户选人（下拉候选 + 保存回查姓名快照），与用水户联系人同源（apaas 用户表，本单位 hltgq）。
 * <p>调度负责人 / 调度记录操作人的下拉候选由本服务下发；保存时**传了选人 id 就按 id 回查固化姓名**
 * （不信任前端文本），只回传姓名文本（编辑未换人、存量手填值）时按文本入库，不阻塞留痕。
 */
@Service
public class PlatformUserService {

    private final WaterUserMapper userMapper;

    public PlatformUserService(WaterUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 选人下拉候选池（id + 姓名，按姓名升序） */
    public List<Map<String, Object>> options() {
        return userMapper.selectPlatformUserOptions();
    }

    /**
     * 解析选人结果并返回入库姓名：userId 非空时按平台用户回查（不存在/姓名空报 400）；
     * 否则取前端姓名文本（trim 后 ≤50 字）。
     *
     * @param userId   选人下拉的 id（可空）
     * @param text     前端回传的姓名文本（可空）
     * @param label    字段名（错误文案用，如「调度负责人」）
     * @param required 必填时 id 与文本皆空报 400「请选择{label}」
     */
    public String resolveName(String userId, String text, String label, boolean required) {
        String id = WaterUsageSupport.trim(userId);
        if (id != null) {
            Map<String, Object> user = userMapper.selectPlatformUserById(id);
            if (user == null) {
                throw new IllegalArgumentException(label + "不存在，请重新选择");
            }
            String name = WaterUsageSupport.requireText((String) user.get("name"),
                    "该" + label + "姓名为空，请重新选择");
            WaterUsageSupport.maxLength(name, 50, label);
            return name;
        }
        String value = WaterUsageSupport.trim(text);
        if (value == null) {
            if (required) {
                throw new IllegalArgumentException("请选择" + label);
            }
            return null;
        }
        WaterUsageSupport.maxLength(value, 50, label);
        return value;
    }
}
