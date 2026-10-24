package com.qgyun.hltgq.hltgqsite.patrol.support;

import com.qgyun.hltgq.hltgqsite.model.util.CheckUtils;
import com.qgyun.hltgq.hltgqsite.patrol.vo.PatrolRecordPhotoFileVO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 巡检 / 问题现场照片公共工具（唯一实现点）。
 * <p>巡检记录、巡检计划、问题记录三个服务原先各抄一份 {@code normalizePhotoIds} 与
 * {@code PhotoContent}，记录与计划还各抄一份 {@code precheckNewPhotos}；实现逐字相同，仅照片张数上限由调用方决定。
 * <p>本次收敛不改变任何提示文案与校验顺序；下载 Content-Type 映射见
 * {@link com.qgyun.hltgq.hltgqsite.model.util.MediaTypeUtils}。
 */
public final class PatrolPhotoSupport {

    private PatrolPhotoSupport() {
    }

    /** 照片 id 归一：去空、去重（保持顺序），并校验数量上限。 */
    public static List<String> normalizePhotoIds(List<String> raw, int maxCount) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String v : raw) {
            String id = CheckUtils.trim(v);
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.size() > maxCount) {
            throw new IllegalArgumentException("现场照片最多上传 " + maxCount + " 张");
        }
        return new ArrayList<>(unique);
    }

    /** 新照片预校验：存在、属于当前上传人且未绑定过其他记录。 */
    public static void precheckNewPhotos(List<String> photoIds, String operatorId,
                                         Function<String, PatrolRecordPhotoFileVO> loader) {
        for (String photoId : photoIds) {
            PatrolRecordPhotoFileVO photo = loader.apply(photoId);
            if (photo == null) {
                throw new IllegalArgumentException("现场照片不存在或已失效，请重新上传");
            }
            if (!operatorId.equals(photo.getCreatedBy()) || photo.getRecordId() != null) {
                throw new IllegalArgumentException("现场照片不可用（非本人上传或已保存），请重新上传");
            }
        }
    }

    /** 照片字节与扩展名（Controller 组装 Content-Type）。 */
    public static final class PhotoContent {

        private final byte[] content;
        private final String ext;

        public PhotoContent(byte[] content, String ext) {
            this.content = content;
            this.ext = ext;
        }

        public byte[] getContent() {
            return content;
        }

        public String getExt() {
            return ext;
        }
    }
}
