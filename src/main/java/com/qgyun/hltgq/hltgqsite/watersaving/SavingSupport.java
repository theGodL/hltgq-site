package com.qgyun.hltgq.hltgqsite.watersaving;

import com.qgyun.hltgq.hltgqsite.model.util.ApiResultUtils;
import com.qgyun.hltgq.hltgqsite.model.util.CheckUtils;
import com.qgyun.hltgq.hltgqsite.model.util.OperatorUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 节水管理支撑门面：文本归一 / 数值校验 / 分页 / 审计操作人一律委托全站唯一实现点
 * （{@link CheckUtils} / {@link ApiResultUtils} / {@link OperatorUtils}），本类不再各抄一份；
 * 只保留节水口径常量与页级方法（today / sameValue），调用点 {@code SavingSupport.xxx(...)} 保持不变。
 * <ul>
 *   <li>水量口径一律万m³（录入 ≤6 位小数；潜力和节水量可为负，目标/水量记录非负）；</li>
 *   <li>附件本地存储 file-dir/saving/{yyyy-MM}/{短id}_{原名}，文件 IO 见 {@code LocalFileStorageUtils}。</li>
 * </ul>
 */
public final class SavingSupport {

    /** 附件存储根目录名（file-dir 下的节水专属目录） */
    public static final String SAVING_DIR = "saving";

    /** 附件大小上限（方案口径：≤2MB） */
    public static final long ATTACHMENT_MAX_BYTES = 2L * 1024 * 1024;

    /** 水量录入小数位上限（万m³，6 位） */
    public static final int WATER_SCALE = 6;

    private SavingSupport() {
    }

    /** 去首尾空白，空串按 null 处理（委托 CheckUtils 唯一实现） */
    public static String trim(String value) {
        return CheckUtils.trim(value);
    }

    /** 必填文本（trim 后为空报 400；委托 CheckUtils） */
    public static String requireText(String value, String message) {
        return CheckUtils.requireText(value, message);
    }

    /** 长度上限校验（列宽约束前置拦截；null 放过；委托 CheckUtils） */
    public static String maxLength(String value, int max, String label) {
        return CheckUtils.maxLength(value, max, label);
    }

    /** 必填水量（万m³，非负、≤6 位小数；委托 CheckUtils，按 WATER_SCALE 参数化） */
    public static BigDecimal requireNonNegative(BigDecimal value, String label) {
        return CheckUtils.requireNonNegative(value, label, WATER_SCALE);
    }

    /** 选填水量（null 放过；填写则非负、≤6 位小数；委托 CheckUtils） */
    public static BigDecimal optionalNonNegative(BigDecimal value, String label) {
        return CheckUtils.optionalNonNegative(value, label, WATER_SCALE);
    }

    /** 必填正数（>0、≤6 位小数；目标值等；委托 CheckUtils） */
    public static BigDecimal requirePositive(BigDecimal value, String label) {
        return CheckUtils.requirePositive(value, label, WATER_SCALE);
    }

    /** 年度校验（2000 ~ 2100，防御脏数据；委托 CheckUtils） */
    public static int requireYear(Integer year, String label) {
        return CheckUtils.requireYear(year, label);
    }

    /** 页码收敛（1 ~ 100000；委托 ApiResultUtils） */
    public static int clampPage(int page) {
        return ApiResultUtils.clampPage(page);
    }

    /** 页大小收敛（1 ~ 100；委托 ApiResultUtils） */
    public static int clampSize(int size) {
        return ApiResultUtils.clampSize(size);
    }

    /** 分页结果（全站口径 total/page/size/pages/records；委托 ApiResultUtils） */
    public static Map<String, Object> pageResult(long total, int page, int size, List<?> records) {
        return ApiResultUtils.pageResult(total, page, size, records);
    }

    /** 当前操作人（会话缺失时用配置兜底；委托 OperatorUtils 统一告警文案） */
    public static String currentOperatorId(String fallbackOperator) {
        return OperatorUtils.currentOperatorId(fallbackOperator);
    }

    /** 操作成功应答（{"success": true}；委托 ApiResultUtils） */
    public static Map<String, Object> ok() {
        return ApiResultUtils.ok();
    }

    /** 当天日期文本（yyyy-MM-dd，核验日期） */
    public static String today() {
        return LocalDate.now().toString();
    }

    /** BigDecimal 同值比较（compareTo 语义；null 仅与 null 相等） */
    public static boolean sameValue(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.compareTo(b) == 0;
    }
}
