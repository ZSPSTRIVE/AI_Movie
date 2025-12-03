package com.jelly.cinema.im.service;

import com.jelly.cinema.im.domain.dto.ApplyHandleDTO;
import com.jelly.cinema.im.domain.dto.FriendApplyDTO;
import com.jelly.cinema.im.domain.dto.GroupApplyDTO;
import com.jelly.cinema.im.domain.vo.ApplyRecordVO;

import java.util.List;

/**
 * 申请服务
 *
 * @author Jelly Cinema
 */
public interface ApplyService {

    /**
     * 发起好友申请
     *
     * @param dto 好友申请信息
     */
    void applyFriend(FriendApplyDTO dto);

    /**
     * 发起入群申请
     *
     * @param dto 入群申请信息
     */
    void applyGroup(GroupApplyDTO dto);

    /**
     * 获取待处理的申请列表
     *
     * @param type 类型：0-全部, 1-好友申请, 2-入群申请
     * @return 申请列表
     */
    List<ApplyRecordVO> getApplyList(Integer type);

    /**
     * 处理申请
     *
     * @param dto 处理信息
     */
    void handleApply(ApplyHandleDTO dto);

    /**
     * 获取未读申请数量
     *
     * @return 未读数量
     */
    int getUnreadCount();
}
