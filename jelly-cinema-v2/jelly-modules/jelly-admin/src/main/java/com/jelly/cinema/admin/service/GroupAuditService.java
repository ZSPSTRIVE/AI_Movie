package com.jelly.cinema.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.admin.domain.vo.GroupAuditVO;
import com.jelly.cinema.admin.domain.vo.GroupMessageVO;

/**
 * 群组审计服务
 *
 * @author Jelly Cinema
 */
public interface GroupAuditService {

    /**
     * 分页查询群组列表
     */
    Page<GroupAuditVO> pageGroups(int pageNum, int pageSize, String keyword);

    /**
     * 获取群聊历史消息
     */
    Page<GroupMessageVO> getGroupMessages(Long groupId, int pageNum, int pageSize);

    /**
     * 强制解散群组
     */
    void dismissGroup(Long groupId, String reason);
}
