package com.jelly.cinema.im.service;

import com.jelly.cinema.im.domain.dto.ReportDTO;

/**
 * 举报服务接口
 *
 * @author Jelly Cinema
 */
public interface ReportService {

    /**
     * 提交举报
     *
     * @param userId 举报人ID
     * @param dto    举报信息
     */
    void submit(Long userId, ReportDTO dto);
}
