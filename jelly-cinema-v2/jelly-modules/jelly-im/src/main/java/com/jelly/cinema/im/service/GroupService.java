package com.jelly.cinema.im.service;

import com.jelly.cinema.im.domain.vo.GroupMemberVO;
import com.jelly.cinema.im.domain.vo.GroupVO;

import java.util.List;

/**
 * 群组服务
 *
 * @author Jelly Cinema
 */
public interface GroupService {

    /**
     * 创建群聊
     *
     * @param name        群名称
     * @param memberIds   初始成员ID列表（不含群主）
     * @return 群ID
     */
    Long createGroup(String name, List<Long> memberIds);

    /**
     * 获取群详情
     *
     * @param groupId 群ID
     * @return 群详情
     */
    GroupVO getGroupDetail(Long groupId);

    /**
     * 获取群成员列表
     *
     * @param groupId 群ID
     * @return 成员列表
     */
    List<GroupMemberVO> getGroupMembers(Long groupId);

    /**
     * 更新群资料
     *
     * @param groupId     群ID
     * @param name        群名称
     * @param description 群简介
     * @param notice      群公告
     */
    void updateGroupInfo(Long groupId, String name, String description, String notice);

    /**
     * 设置/取消管理员
     *
     * @param groupId 群ID
     * @param userId  用户ID
     * @param type    类型：1-设置管理员, 0-取消管理员
     */
    void setAdmin(Long groupId, Long userId, int type);

    /**
     * 踢出成员
     *
     * @param groupId   群ID
     * @param memberIds 成员ID列表
     */
    void kickMembers(Long groupId, List<Long> memberIds);

    /**
     * 禁言成员
     *
     * @param groupId  群ID
     * @param memberId 成员ID
     * @param duration 禁言时长（分钟，0表示解除禁言）
     */
    void muteMember(Long groupId, Long memberId, int duration);

    /**
     * 全员禁言
     *
     * @param groupId 群ID
     * @param mute    true-禁言, false-解除
     */
    void muteAll(Long groupId, boolean mute);

    /**
     * 修改我的群名片
     *
     * @param groupId  群ID
     * @param nickname 群名片
     */
    void updateMyGroupNick(Long groupId, String nickname);

    /**
     * 转让群主
     *
     * @param groupId    群ID
     * @param newOwnerId 新群主ID
     */
    void transferOwner(Long groupId, Long newOwnerId);

    /**
     * 退出群聊
     *
     * @param groupId 群ID
     */
    void quitGroup(Long groupId);

    /**
     * 解散群聊（仅群主）
     *
     * @param groupId 群ID
     */
    void dissolveGroup(Long groupId);

    /**
     * 邀请好友入群
     *
     * @param groupId  群ID
     * @param userIds  用户ID列表
     */
    void inviteMembers(Long groupId, List<Long> userIds);
}
