package com.jelly.cinema.im.service;

import com.jelly.cinema.common.api.domain.RemoteFriend;
import com.jelly.cinema.common.api.domain.RemoteGroup;
import com.jelly.cinema.common.api.domain.RemoteGroupSimple;
import com.jelly.cinema.common.api.domain.RemoteMessage;

import java.util.List;

/**
 * IM 管理端服务接口
 *
 * @author Jelly Cinema
 */
public interface ImAdminService {

    /**
     * 分页获取群组列表
     */
    List<RemoteGroup> getGroups(int pageNum, int pageSize, String keyword);

    /**
     * 获取群组总数
     */
    Long getGroupCount();

    /**
     * 获取活跃群组数（7天内有消息）
     */
    Integer getActiveGroupCount();

    /**
     * 强制解散群组
     */
    void dismissGroup(Long groupId, String reason);

    /**
     * 获取群聊历史消息
     */
    List<RemoteMessage> getGroupMessages(Long groupId, int pageNum, int pageSize);

    /**
     * 获取消息总数
     */
    Long getMessageCount(Long groupId);

    /**
     * 获取用户好友列表
     */
    List<RemoteFriend> getUserFriends(Long userId);

    /**
     * 获取用户加入的群组
     */
    List<RemoteGroupSimple> getUserGroups(Long userId);
}
