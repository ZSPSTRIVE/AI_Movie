package com.jelly.cinema.im.service;

import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.im.domain.dto.MessageDTO;
import com.jelly.cinema.im.domain.entity.ChatMessage;
import com.jelly.cinema.im.domain.vo.MessageVO;
import com.jelly.cinema.im.domain.vo.SessionVO;

import java.util.List;

/**
 * 消息服务接口
 *
 * @author Jelly Cinema
 */
public interface MessageService {

    /**
     * 发送消息
     *
     * @param fromId 发送者 ID
     * @param dto    消息内容
     */
    void sendMessage(Long fromId, MessageDTO dto);

    /**
     * 获取会话列表
     *
     * @param userId 用户 ID
     * @return 会话列表
     */
    List<SessionVO> getSessionList(Long userId);

    /**
     * 获取历史消息
     *
     * @param sessionId 会话 ID
     * @param query     分页参数
     * @return 消息列表
     */
    PageResult<MessageVO> getHistory(String sessionId, PageQuery query);

    /**
     * 撤回消息
     *
     * @param userId    用户 ID
     * @param messageId 消息 ID
     */
    void recallMessage(Long userId, Long messageId);

    /**
     * 删除会话
     *
     * @param userId       用户 ID
     * @param sessionId    会话 ID
     * @param keepMessages 是否保留消息记录
     */
    void deleteSession(Long userId, String sessionId, Boolean keepMessages);

    /**
     * 删除单条消息
     *
     * @param userId    用户 ID
     * @param messageId 消息 ID
     */
    void deleteMessage(Long userId, Long messageId);

    /**
     * 清空会话消息
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     */
    void clearMessages(Long userId, String sessionId);

    /**
     * 标记消息已读
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     */
    void markAsRead(Long userId, String sessionId);

    /**
     * 保存消息（供 Netty 消息处理器调用）
     *
     * @param message 聊天消息实体
     */
    void saveMessage(ChatMessage message);

    /**
     * 批量标记消息已读
     *
     * @param messageIds 消息 ID 列表
     */
    void markMessagesAsRead(List<Long> messageIds);
}
