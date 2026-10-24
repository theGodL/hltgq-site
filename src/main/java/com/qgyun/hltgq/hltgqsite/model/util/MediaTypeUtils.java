package com.qgyun.hltgq.hltgqsite.model.util;

import org.springframework.http.MediaType;

/**
 * 附件下载 Content-Type 映射（唯一实现点）。
 * <p>历史上设备盘点、节水方案、巡检现场照片（记录 / 问题）四处 Controller 各抄一份
 * {@code mediaTypeOf(String ext)}，图片段逐字相同，仅节水方案多 pdf / csv 两项。
 * <p>本类取并集：对各单位原有入参（图片扩展名、pdf、csv）返回值与原实现完全一致，
 * 未知扩展名一律按二进制流，避免浏览器猜测类型。
 */
public final class MediaTypeUtils {

    private MediaTypeUtils() {
    }

    /** 扩展名 → Content-Type（null / 未知返回 APPLICATION_OCTET_STREAM） */
    public static MediaType of(String ext) {
        if (ext == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        switch (ext.toLowerCase()) {
            case "jpg":
            case "jpeg":
                return MediaType.IMAGE_JPEG;
            case "png":
                return MediaType.IMAGE_PNG;
            case "gif":
                return MediaType.IMAGE_GIF;
            case "bmp":
                return MediaType.parseMediaType("image/bmp");
            case "webp":
                return MediaType.parseMediaType("image/webp");
            case "pdf":
                return MediaType.APPLICATION_PDF;
            case "csv":
                return MediaType.parseMediaType("text/csv;charset=UTF-8");
            default:
                return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
