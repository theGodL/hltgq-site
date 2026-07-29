package com.qgyun.hltgq.hltgqsite.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.qgyun.hltgq.hltgqsite.entity.StRiverR;
import com.qgyun.hltgq.hltgqsite.vo.ReservoirRegimeVO;
import com.qgyun.hltgq.hltgqsite.vo.RiverRegimeVO;

import java.time.LocalDateTime;
import java.util.List;

public interface StRiverRService extends IService<StRiverR> {

    boolean saveOrUpdateByKey(StRiverR entity);

    List<StRiverR> latestPerStation();

    /**
     * 河道水情数据（分页）
     *
     * @param stcd      站点编号（必填，仅限 00000001/00000007）
     * @param startTime 起始时间
     * @param endTime   截止时间
     * @param page      页码
     * @param size      每页条数
     * @return 分页结果
     */
    Page<RiverRegimeVO> riverRegime(String stcd, LocalDateTime startTime, LocalDateTime endTime, long page, long size);

    /**
     * 水库水情数据（分页）
     *
     * @param stcd      站点编号（必填，目前仅支持 00000007 花凉亭坝上）
     * @param startTime 起始时间
     * @param endTime   截止时间
     * @param page      页码
     * @param size      每页条数
     * @return 分页结果
     */
    Page<ReservoirRegimeVO> reservoirRegime(String stcd, LocalDateTime startTime, LocalDateTime endTime, long page, long size);
}
