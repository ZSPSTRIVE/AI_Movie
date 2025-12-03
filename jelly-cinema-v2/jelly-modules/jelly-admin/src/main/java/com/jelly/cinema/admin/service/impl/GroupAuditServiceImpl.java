package com.jelly.cinema.admin.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.admin.domain.vo.GroupAuditVO;
import com.jelly.cinema.admin.domain.vo.GroupMessageVO;
import com.jelly.cinema.admin.service.GroupAuditService;
import com.jelly.cinema.common.api.domain.RemoteGroup;
import com.jelly.cinema.common.api.domain.RemoteMessage;
import com.jelly.cinema.common.api.feign.RemoteImService;
import com.jelly.cinema.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 群组审计服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupAuditServiceImpl implements GroupAuditService {

    private final RemoteImService remoteImService;

    @Override
    public Page<GroupAuditVO> pageGroups(int pageNum, int pageSize, String keyword) {
        Page<GroupAuditVO> page = new Page<>(pageNum, pageSize);
        
        try {
            // 调用 IM 服务获取群组列表
            R<List<RemoteGroup>> result = remoteImService.getGroups(pageNum, pageSize, keyword);
            
            if (result.isSuccess() && result.getData() != null) {
                List<GroupAuditVO> records = result.getData().stream()
                        .map(this::toGroupAuditVO)
                        .toList();
                page.setRecords(records);
                
                // 获取总数
                R<Long> countResult = remoteImService.getGroupCount();
                if (countResult.isSuccess() && countResult.getData() != null) {
                    page.setTotal(countResult.getData());
                }
            } else {
                page.setRecords(new ArrayList<>());
                page.setTotal(0);
            }
        } catch (Exception e) {
            log.error("获取群组列表失败: {}", e.getMessage());
            page.setRecords(new ArrayList<>());
            page.setTotal(0);
        }
        
        return page;
    }

    @Override
    public Page<GroupMessageVO> getGroupMessages(Long groupId, int pageNum, int pageSize) {
        Page<GroupMessageVO> page = new Page<>(pageNum, pageSize);
        
        try {
            // 调用 IM 服务获取群聊历史消息
            R<List<RemoteMessage>> result = remoteImService.getGroupMessages(groupId, pageNum, pageSize);
            
            if (result.isSuccess() && result.getData() != null) {
                List<GroupMessageVO> records = result.getData().stream()
                        .map(this::toGroupMessageVO)
                        .toList();
                page.setRecords(records);
                
                // 获取消息总数
                R<Long> countResult = remoteImService.getMessageCount(groupId);
                if (countResult.isSuccess() && countResult.getData() != null) {
                    page.setTotal(countResult.getData());
                }
            } else {
                page.setRecords(new ArrayList<>());
                page.setTotal(0);
            }
        } catch (Exception e) {
            log.error("获取群聊消息失败: {}", e.getMessage());
            page.setRecords(new ArrayList<>());
            page.setTotal(0);
        }
        
        return page;
    }

    @Override
    public void dismissGroup(Long groupId, String reason) {
        try {
            // 调用 IM 服务解散群组
            R<Void> result = remoteImService.dismissGroup(groupId, reason);
            
            if (result.isSuccess()) {
                log.info("群组 {} 已被管理员解散，原因: {}", groupId, reason);
            } else {
                log.error("解散群组失败: {}", result.getMsg());
                throw new RuntimeException("解散群组失败: " + result.getMsg());
            }
        } catch (Exception e) {
            log.error("解散群组失败: {}", e.getMessage());
            throw new RuntimeException("解散群组失败: " + e.getMessage());
        }
    }

    private GroupAuditVO toGroupAuditVO(RemoteGroup group) {
        GroupAuditVO vo = new GroupAuditVO();
        vo.setId(group.getId());
        vo.setGroupNo(group.getGroupNo());
        vo.setName(group.getName());
        vo.setAvatar(group.getAvatar());
        vo.setOwnerId(group.getOwnerId());
        vo.setOwnerNickname(group.getOwnerNickname());
        vo.setMemberCount(group.getMemberCount());
        vo.setStatus(group.getStatus());
        vo.setCreateTime(group.getCreateTime());
        return vo;
    }

    private GroupMessageVO toGroupMessageVO(RemoteMessage msg) {
        GroupMessageVO vo = new GroupMessageVO();
        vo.setId(msg.getId());
        vo.setSenderId(msg.getSenderId());
        vo.setSenderName(msg.getSenderName());
        vo.setSenderAvatar(msg.getSenderAvatar());
        vo.setMsgType(msg.getMsgType());
        vo.setContent(msg.getContent());
        vo.setCreateTime(msg.getCreateTime());
        return vo;
    }
}
