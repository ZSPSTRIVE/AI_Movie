package com.jelly.cinema.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.admin.domain.vo.ReportVO;

/**
 * 举报服务
 *
 * @author Jelly Cinema
 */
public interface ReportService {

    /**
     * 分页查询举报列表
     */
    Page<ReportVO> page(int pageNum, int pageSize, Integer status, Integer targetType);

    /**
     * 获取举报详情
     */
    ReportVO getDetail(Long id);

    /**
     * 处理举报
     *
     * @param id       举报ID
     * @param action   处理动作：1-忽略, 2-警告, 3-封禁
     * @param feedback 处理反馈
     */
    void handle(Long id, int action, String feedback);

    /**
     * 获取待处理举报数量
     */
    int getPendingCount();
}
