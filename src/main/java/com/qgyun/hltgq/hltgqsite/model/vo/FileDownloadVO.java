package com.qgyun.hltgq.hltgqsite.model.vo;

import lombok.Data;

/**
 * 本地文件下载载体（Service → Controller 统一传递：字节内容 + 原始文件名 + 扩展名）。
 * <p>响应头由 DownloadHeaderUtils 生成（Content-Disposition 用 fileName，Content-Type 按 fileExt）。
 * <p>watersaving（原 SavingDownloadVO）已收敛为本类；inventorystocktake 的 StocktakeDownloadVO
 * 为同构实现，属模块外改动，留待后续轮次统一。
 */
@Data
public class FileDownloadVO {

    /** 文件二进制内容 */
    private byte[] content;

    /** 原始文件名（Content-Disposition 使用） */
    private String fileName;

    /** 扩展名（小写，Content-Type 判别） */
    private String fileExt;
}
