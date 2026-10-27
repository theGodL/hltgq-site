package com.qgyun.hltgq.hltgqsite.workorder.vo;

import lombok.Data;

/**
 * 工单附件下载载荷（二进制内容 + 原始文件名 + 扩展名），Controller 组装响应头。
 */
@Data
public class WorkOrderAttachmentDownloadVO {

    private byte[] content;

    private String fileName;

    private String fileExt;
}
