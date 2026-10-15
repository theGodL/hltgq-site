package com.qgyun.hltgq.hltgqsite.model.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 本地附件存储工具（唯一实现点，挂载目录 {@code hltgq.file-dir}）。
 * <p>收敛内容：根目录归一、相对路径解析 + 目录穿越校验、落盘、读取、静默删除、
 * 事务提交后清理、原始文件名清洗、扩展名提取与白名单判定。
 * <p>各模块只保留自己的存储子目录名与大小上限（如盘点 stocktake/、节水 saving/），
 * 文件操作一律走本类——历史上设备盘点与节水方案曾各抄一份完全相同的实现。
 */
public final class LocalFileStorageUtils {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageUtils.class);

    /** 附件扩展名白名单（图片 / PDF / Word / Excel） */
    public static final Set<String> ALLOWED_EXTENSIONS;

    static {
        Set<String> exts = new HashSet<>(Arrays.asList(
                "jpg", "jpeg", "png", "gif", "bmp", "webp", "pdf", "doc", "docx", "xls", "xlsx"));
        ALLOWED_EXTENSIONS = Collections.unmodifiableSet(exts);
    }

    /** 附件按月分桶目录（yyyy-MM） */
    public static final DateTimeFormatter MONTH_DIR = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 清洗后文件名长度上限（超出则截断主干、保留扩展名） */
    private static final int NAME_MAX_LENGTH = 120;

    private static final int NAME_STEM_MAX_LENGTH = 100;

    private LocalFileStorageUtils() {
    }

    /** 存储根目录（绝对路径归一，用作穿越校验基准） */
    public static Path root(String fileDir) {
        return Paths.get(fileDir).toAbsolutePath().normalize();
    }

    /** 相对路径解析 + 目录穿越校验（normalize 后必须仍在根目录下） */
    public static Path resolve(Path base, String relPath) {
        Path target = base.resolve(relPath).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("附件路径非法");
        }
        return target;
    }

    /** 落盘（建目录 + 覆盖同名；IO 失败包装为业务可读异常） */
    public static void write(Path base, String relPath, InputStream in) {
        Path target = resolve(base, relPath);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("附件保存失败: " + e.getMessage());
        }
    }

    /** 落盘（MultipartFile 直传，调用方无需处理 IO 异常） */
    public static void write(Path base, String relPath, MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            write(base, relPath, in);
        } catch (IOException e) {
            throw new IllegalStateException("附件保存失败: " + e.getMessage());
        }
    }

    /** 读取全部字节（文件缺失提示已被清理；IO 失败包装为业务可读异常） */
    public static byte[] readBytes(Path base, String relPath) {
        Path target = resolve(base, relPath);
        if (!Files.exists(target)) {
            throw new IllegalArgumentException("附件文件不存在，可能已被清理");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new IllegalStateException("附件读取失败: " + e.getMessage());
        }
    }

    /** 物理删除附件文件（失败仅告警，不阻断业务） */
    public static void deleteQuietly(Path base, String relPath) {
        if (CheckUtils.trim(relPath) == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(base, relPath));
        } catch (Exception e) {
            log.warn("附件文件删除失败（忽略）：path={}, error={}", relPath, e.getMessage());
        }
    }

    /** 批量静默删除 */
    public static void deleteQuietly(Path base, List<String> relPaths) {
        if (relPaths == null) {
            return;
        }
        relPaths.forEach(relPath -> deleteQuietly(base, relPath));
    }

    /**
     * 事务提交后再物理删文件（回滚时行仍在、文件不丢）；无事务同步上下文时立即执行。
     * <p>删除失败一律静默（{@link #deleteQuietly}），不会产生孤儿行。
     */
    public static void deleteAfterCommit(Path base, List<String> relPaths) {
        if (relPaths == null || relPaths.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(base, relPaths);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(base, relPaths);
            }
        });
    }

    /** 原始文件名清洗：去路径段与控制字符、空白兜底、超长截断（保留扩展名） */
    public static String sanitizeFileName(String raw) {
        String name = raw == null ? "" : raw;
        name = name.replace('\\', '/');
        int idx = name.lastIndexOf('/');
        if (idx >= 0) {
            name = name.substring(idx + 1);
        }
        name = name.replaceAll("[\\r\\n\\t\\u0000]", "").trim();
        if (name.isEmpty()) {
            name = "file";
        }
        if (name.length() > NAME_MAX_LENGTH) {
            String ext = extOf(name);
            String stem = ext == null ? name : name.substring(0, name.length() - ext.length() - 1);
            if (stem.length() > NAME_STEM_MAX_LENGTH) {
                stem = stem.substring(0, NAME_STEM_MAX_LENGTH);
            }
            name = ext == null ? stem : stem + "." + ext;
        }
        return name;
    }

    /** 扩展名提取（小写；无扩展名或入参为空返回 null） */
    public static String extOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    /** 扩展名是否在白名单内（null 一律不通过） */
    public static boolean isAllowedExt(String ext) {
        return ext != null && ALLOWED_EXTENSIONS.contains(ext);
    }
}
