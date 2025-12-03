package com.jelly.cinema.im.service;

import com.jelly.cinema.im.domain.vo.FriendVO;

import java.util.List;

/**
 * 好友服务接口
 *
 * @author Jelly Cinema
 */
public interface FriendService {

    /**
     * 获取好友列表
     *
     * @param userId 用户ID
     * @return 好友列表
     */
    List<FriendVO> getFriendList(Long userId);

    /**
     * 删除好友
     *
     * @param userId       用户ID
     * @param friendId     好友ID
     * @param keepMessages 是否保留消息记录
     */
    void deleteFriend(Long userId, Long friendId, Boolean keepMessages);

    /**
     * 设置好友备注
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     * @param remark   备注名
     */
    void setRemark(Long userId, Long friendId, String remark);

    /**
     * 拉黑好友
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     */
    void blockFriend(Long userId, Long friendId);

    /**
     * 解除拉黑
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     */
    void unblockFriend(Long userId, Long friendId);

    /**
     * 获取黑名单列表
     *
     * @param userId 用户ID
     * @return 黑名单列表
     */
    List<FriendVO> getBlacklist(Long userId);
}
