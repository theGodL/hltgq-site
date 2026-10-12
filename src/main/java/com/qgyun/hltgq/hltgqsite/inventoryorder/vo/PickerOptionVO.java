package com.qgyun.hltgq.hltgqsite.inventoryorder.vo;

import lombok.Data;

/**
 * 通用下拉选项：value 提交值 / label 显示名 / sub 右侧辅助文字 / group 分组（如类型 in|out）。
 */
@Data
public class PickerOptionVO {

    private String value;
    private String label;
    private String sub;
    private String group;
}
