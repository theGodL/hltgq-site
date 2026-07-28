package com.qgyun.hltgq.hltgqsite.vo;

import lombok.Data;

import java.util.List;

@Data
public class WaterIntakeVO {
    private String wiustNm;
    private List<WaterIntakeRecordVO> records;
}
