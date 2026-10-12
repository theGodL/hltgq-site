package com.qgyun.hltgq.hltgqsite.inventorystocktake.vo;

import lombok.Data;

/**
 * 附件下载读取结果（Service → Controller 传递：字节内容 + 原始文件名 + 扩展名）。
 */
@Data
public class StocktakeDownloadVO {

    private byte[] content;

    /** 原始文件名（响应头 Content-Disposition 使用） */
    private String fileName;

    /** 扩展名（小写，响应 Content-Type 判别） */
    private String fileExt;
}
