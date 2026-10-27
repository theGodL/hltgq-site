package com.qgyun.hltgq.hltgqsite.waterusage.vo;

import lombok.Data;

/**
 * 用水计划附件下载载荷（二进制内容 + 原始文件名 + 扩展名），Controller 组装响应头。
 */
@Data
public class WaterPlanDownloadVO {

    private byte[] content;

    private String fileName;

    private String fileExt;
}
