package com.jelly.cinema.im.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.common.api.domain.RemoteUser;
import com.jelly.cinema.common.api.feign.RemoteUserService;
import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.redis.service.RedisService;
import com.jelly.cinema.im.domain.dto.MessageDTO;
import com.jelly.cinema.im.domain.entity.ChatMessage;
import com.jelly.cinema.im.domain.entity.Group;
import com.jelly.cinema.im.domain.vo.MessageVO;
import com.jelly.cinema.im.domain.vo.SessionVO;
import com.jelly.cinema.im.mapper.ChatMessageMapper;
import com.jelly.cinema.im.mapper.GroupMapper;
import com.jelly.cinema.im.mapper.GroupMemberMapper;
import com.jelly.cinema.im.mq.MessageProducer;
import com.jelly.cinema.im.service.MessageService;
import com.jelly.cinema.im.websocket.ChatWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 消息服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
public class MessageServiceImpl implements MessageService {

    private final ChatMessageMapper chatMessageMapper;
    private final RedisService redisService;
    private final ChatWebSocketHandler webSocketHandler;
    private final RemoteUserService remoteUserService;
    private final GroupMapper groupMapper;
    private final GroupMemberMapper groupMemberMapper;
    
    @Autowired(required = false)
    private MessageProducer messageProducer;
    
    public MessageServiceImpl(ChatMessageMapper chatMessageMapper, 
                              RedisService redisService, 
                              ChatWebSocketHandler webSocketHandler,
                              RemoteUserService remoteUserService,
                              GroupMapper groupMapper,
                              GroupMemberMapper groupMemberMapper) {
        this.chatMessageMapper = chatMessageMapper;
        this.redisService = redisService;
        this.webSocketHandler = webSocketHandler;
        this.remoteUserService = remoteUserService;
        this.groupMapper = groupMapper;
        this.groupMemberMapper = groupMemberMapper;
    }

    private static final String SESSION_KEY = "jelly:im:session:";
    private static final String MSG_SEQ_KEY = "jelly:im:seq:";
    private static final String UNREAD_KEY = "jelly:im:unread:";

    @Override
    public void sendMessage(Long fromId, MessageDTO dto) {
        // 将字符串 toId 转换为 Long（避免 JavaScript 大数字精度丢失）
        Long toId = Long.parseLong(dto.getToId());
        log.info("接收到消息: fromId={}, toId={}, cmdType={}", fromId, toId, dto.getCmdType());
        
        // 生成会话 ID
        String sessionId = generateSessionId(fromId, toId, dto.getCmdType());

        // 生成消息序列号
        Long msgSeq = redisService.increment(MSG_SEQ_KEY + sessionId);

        // 构建消息
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setFromId(fromId);
        message.setToId(toId);
        message.setCmdType(dto.getCmdType());
        message.setMsgType(dto.getMsgType());
        message.setContent(dto.getContent());
        // extra 字段在数据库中为 JSON 类型，需要包装成合法 JSON
        String extra = dto.getExtra();
        if (extra != null && !extra.isEmpty()) {
            // 如果已经是 JSON 格式（以 { 或 [ 开头），直接使用；否则包装成 {"fileName":"xxx"}
            if (extra.startsWith("{") || extra.startsWith("[")) {
                message.setExtra(extra);
            } else {
                message.setExtra(JSONUtil.toJsonStr(Map.of("fileName", extra)));
            }
        }
        message.setMsgSeq(msgSeq);
        message.setStatus(0);

        // 写入 Redis Timeline（用于快速拉取）
        String timelineKey = SESSION_KEY + sessionId + ":timeline";
        MessageVO vo = toVO(message);
        redisService.zAdd(timelineKey, JSONUtil.toJsonStr(vo), System.currentTimeMillis());

        // 发送到 MQ 异步持久化（如果 MQ 可用）
        if (messageProducer != null) {
            messageProducer.sendMessage(message);
            log.info("消息已发送到MQ: sessionId={}, msgSeq={}", sessionId, msgSeq);
        } else {
            // MQ 不可用，直接同步写入数据库
            chatMessageMapper.insert(message);
            log.info("消息已同步写入数据库: sessionId={}, msgSeq={}, id={}", sessionId, msgSeq, message.getId());
        }

        // 实时推送
        String pushJson = JSONUtil.toJsonStr(Map.of(
                "type", "message",
                "data", vo
        ));
        
        log.info("准备推送消息: cmdType={}, fromId={}, toId={}", dto.getCmdType(), fromId, toId);
        
        if (dto.getCmdType() == 2) {
            // 群聊：推送给所有在线的群成员（除了发送者）
            List<Long> memberIds = groupMemberMapper.selectUserIdsByGroupId(toId);
            log.info("群成员列表: groupId={}, memberIds={}", toId, memberIds);
            for (Long memberId : memberIds) {
                if (!memberId.equals(fromId)) {
                    log.info("尝试推送给群成员: memberId={}, 在线={}", memberId, webSocketHandler.isOnline(memberId));
                    webSocketHandler.sendToUser(memberId, pushJson);
                }
            }
            log.info("群消息推送完成: groupId={}, 成员数={}", toId, memberIds.size());
        } else {
            // 私聊：推送给接收方
            log.info("尝试推送私聊消息: toId={}, 在线={}", toId, webSocketHandler.isOnline(toId));
            webSocketHandler.sendToUser(toId, pushJson);
        }

        log.info("消息发送成功: {} -> {}, sessionId={}", fromId, toId, sessionId);
    }

    @Override
    public List<SessionVO> getSessionList(Long userId) {
        List<SessionVO> result = new ArrayList<>();
        log.info("获取会话列表开始: userId={}", userId);
        
        // ========== 1. 查询私聊会话 ==========
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(ChatMessage::getFromId, userId).or().eq(ChatMessage::getToId, userId));
        wrapper.eq(ChatMessage::getCmdType, 1); // 私聊
        wrapper.orderByDesc(ChatMessage::getCreateTime);

        List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);

        // 按会话分组，取最后一条
        Map<String, ChatMessage> sessionMap = messages.stream()
                .collect(Collectors.toMap(
                        ChatMessage::getSessionId,
                        m -> m,
                        (m1, m2) -> m1.getCreateTime().isAfter(m2.getCreateTime()) ? m1 : m2
                ));

        // 收集所有需要查询的用户 ID
        Set<Long> userIds = new HashSet<>();
        for (ChatMessage msg : sessionMap.values()) {
            userIds.add(msg.getFromId().equals(userId) ? msg.getToId() : msg.getFromId());
        }
        
        // 批量查询用户信息
        Map<Long, RemoteUser> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            try {
                R<List<RemoteUser>> userResult = remoteUserService.getUsersByIds(new ArrayList<>(userIds));
                if (userResult.isSuccess() && userResult.getData() != null) {
                    userMap = userResult.getData().stream()
                            .collect(Collectors.toMap(RemoteUser::getId, u -> u, (a, b) -> a));
                }
            } catch (Exception e) {
                log.warn("查询用户信息失败: {}", e.getMessage());
            }
        }

        // 构建私聊会话列表
        for (ChatMessage msg : sessionMap.values()) {
            SessionVO vo = new SessionVO();
            Long targetUserId = msg.getFromId().equals(userId) ? msg.getToId() : msg.getFromId();
            
            // 确保 sessionId 有效，如果无效则生成
            String sessionId = msg.getSessionId();
            if (sessionId == null || sessionId.isEmpty() || !sessionId.startsWith("private_")) {
                sessionId = generateSessionId(userId, targetUserId, 1);
            }
            vo.setSessionId(sessionId);
            vo.setType(1); // 私聊
            vo.setUserId(targetUserId);
            vo.setLastMessage(msg.getContent());
            vo.setLastTime(msg.getCreateTime());
            
            // 计算未读数
            String unreadKey = UNREAD_KEY + userId + ":" + sessionId;
            Integer unreadCount = redisService.get(unreadKey);
            vo.setUnreadCount(unreadCount != null ? unreadCount : 0);
            
            // 填充用户信息
            RemoteUser targetUser = userMap.get(targetUserId);
            if (targetUser != null) {
                vo.setNickname(targetUser.getNickname());
                vo.setAvatar(targetUser.getAvatar());
            } else {
                vo.setNickname("用户" + targetUserId);
            }
            
            result.add(vo);
        }
        
        // ========== 2. 查询群聊会话 ==========
        List<Long> groupIds = groupMemberMapper.selectGroupIdsByUserId(userId);
        if (groupIds != null && !groupIds.isEmpty()) {
            // 查询群信息
            List<Group> groups = groupMapper.selectBatchIds(groupIds);
            
            for (Group group : groups) {
                if (group.getStatus() != 0) continue; // 跳过已解散的群
                
                String sessionId = "group_" + group.getId();
                SessionVO vo = new SessionVO();
                vo.setSessionId(sessionId);
                vo.setType(2); // 群聊
                vo.setGroupId(group.getId());
                vo.setNickname(group.getName());
                vo.setAvatar(group.getAvatar());
                
                // 查询群的最后一条消息
                LambdaQueryWrapper<ChatMessage> groupMsgWrapper = new LambdaQueryWrapper<>();
                groupMsgWrapper.eq(ChatMessage::getSessionId, sessionId);
                groupMsgWrapper.orderByDesc(ChatMessage::getCreateTime);
                groupMsgWrapper.last("LIMIT 1");
                ChatMessage lastMsg = chatMessageMapper.selectOne(groupMsgWrapper);
                
                if (lastMsg != null) {
                    vo.setLastMessage(lastMsg.getContent());
                    vo.setLastTime(lastMsg.getCreateTime());
                } else {
                    vo.setLastMessage("[群聊创建]");
                    vo.setLastTime(group.getCreateTime());
                }
                
                // 计算未读数
                String unreadKey = UNREAD_KEY + userId + ":" + sessionId;
                Integer unreadCount = redisService.get(unreadKey);
                vo.setUnreadCount(unreadCount != null ? unreadCount : 0);
                
                result.add(vo);
            }
        }
        
        // 按最后消息时间排序
        result.sort((a, b) -> {
            if (a.getLastTime() == null) return 1;
            if (b.getLastTime() == null) return -1;
            return b.getLastTime().compareTo(a.getLastTime());
        });

        log.info("获取会话列表结束: userId={}, sessionCount={}", userId, result.size());
        return result;
    }

    @Override
    public PageResult<MessageVO> getHistory(String sessionId, PageQuery query) {
        log.info("获取历史消息开始: sessionId={}, pageNum={}, pageSize={}",
                sessionId, query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        
        // 兼容新旧 sessionId 格式
        if (sessionId.startsWith("private_")) {
            // 新格式，同时查询新旧格式
            String oldSessionId = sessionId.substring("private_".length());
            wrapper.and(w -> w.eq(ChatMessage::getSessionId, sessionId)
                    .or().eq(ChatMessage::getSessionId, oldSessionId));
        } else {
            wrapper.eq(ChatMessage::getSessionId, sessionId);
        }
        
        wrapper.eq(ChatMessage::getStatus, 0);
        wrapper.orderByDesc(ChatMessage::getMsgSeq);

        Page<ChatMessage> page = chatMessageMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                wrapper
        );

        List<MessageVO> voList = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        PageResult<MessageVO> result = PageResult.build(voList, page.getTotal(),
                query.getPageNum(), query.getPageSize());
        log.info("获取历史消息结束: sessionId={}, total={}, pageNum={}, pageSize={}",
                sessionId, page.getTotal(), query.getPageNum(), query.getPageSize());
        return result;
    }

    @Override
    public void recallMessage(Long userId, Long messageId) {
        ChatMessage message = chatMessageMapper.selectById(messageId);
        if (message == null) {
            throw new ServiceException("消息不存在");
        }

        if (!message.getFromId().equals(userId)) {
            throw new ServiceException("无权撤回此消息");
        }

        // 检查是否超过 2 分钟
        if (Duration.between(message.getCreateTime(), LocalDateTime.now()).toMinutes() > 2) {
            throw new ServiceException("超过 2 分钟的消息无法撤回");
        }

        message.setStatus(1);
        chatMessageMapper.updateById(message);

        // 通知接收方撤回
        if (webSocketHandler.isOnline(message.getToId())) {
            String pushJson = JSONUtil.toJsonStr(Map.of(
                    "type", "recall",
                    "messageId", messageId
            ));
            webSocketHandler.sendToUser(message.getToId(), pushJson);
        }
    }

    /**
     * 生成会话 ID
     */
    private String generateSessionId(Long userId1, Long userId2, Integer cmdType) {
        if (cmdType == 2) {
            // 群聊直接用群 ID
            return "group_" + userId2;
        }
        // 私聊：private_min_max 格式
        long min = Math.min(userId1, userId2);
        long max = Math.max(userId1, userId2);
        return "private_" + min + "_" + max;
    }

    /**
     * 转换为 VO（单条消息）
     */
    private MessageVO toVO(ChatMessage message) {
        MessageVO vo = BeanUtil.copyProperties(message, MessageVO.class);
        
        // 解析 extra 字段：如果是 JSON 且包含 fileName，提取出来返回给前端
        String extra = message.getExtra();
        if (extra != null && extra.startsWith("{")) {
            try {
                cn.hutool.json.JSONObject jsonObj = JSONUtil.parseObj(extra);
                if (jsonObj.containsKey("fileName")) {
                    vo.setExtra(jsonObj.getStr("fileName"));
                }
            } catch (Exception e) {
                // 解析失败，保持原值
            }
        }
        
        // 查询发送者信息
        try {
            R<RemoteUser> userResult = remoteUserService.getUserById(message.getFromId());
            if (userResult.isSuccess() && userResult.getData() != null) {
                RemoteUser user = userResult.getData();
                vo.setFromNickname(user.getNickname());
                vo.setFromAvatar(user.getAvatar());
            }
        } catch (Exception e) {
            log.warn("查询发送者信息失败: {}", e.getMessage());
        }
        
        return vo;
    }
    
    /**
     * 批量转换为 VO（消息列表）
     */
    private List<MessageVO> toVOList(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 收集所有发送者 ID
        Set<Long> senderIds = messages.stream()
                .map(ChatMessage::getFromId)
                .collect(Collectors.toSet());
        
        // 批量查询用户信息
        Map<Long, RemoteUser> userMap = new HashMap<>();
        try {
            R<List<RemoteUser>> userResult = remoteUserService.getUsersByIds(new ArrayList<>(senderIds));
            if (userResult.isSuccess() && userResult.getData() != null) {
                userMap = userResult.getData().stream()
                        .collect(Collectors.toMap(RemoteUser::getId, u -> u, (a, b) -> a));
            }
        } catch (Exception e) {
            log.warn("批量查询用户信息失败: {}", e.getMessage());
        }
        
        // 转换为 VO
        List<MessageVO> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            MessageVO vo = BeanUtil.copyProperties(msg, MessageVO.class);
            
            // 解析 extra 字段：如果是 JSON 且包含 fileName，提取出来返回给前端
            String extra = msg.getExtra();
            if (extra != null && extra.startsWith("{")) {
                try {
                    cn.hutool.json.JSONObject jsonObj = JSONUtil.parseObj(extra);
                    if (jsonObj.containsKey("fileName")) {
                        vo.setExtra(jsonObj.getStr("fileName"));
                    }
                } catch (Exception e) {
                    // 解析失败，保持原值
                }
            }
            
            RemoteUser sender = userMap.get(msg.getFromId());
            if (sender != null) {
                vo.setFromNickname(sender.getNickname());
                vo.setFromAvatar(sender.getAvatar());
            }
            result.add(vo);
        }
        
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long userId, String sessionId, Boolean keepMessages) {
        log.info("删除会话: userId={}, sessionId={}, keepMessages={}", userId, sessionId, keepMessages);

        // 删除会话的未读计数
        String unreadKey = UNREAD_KEY + userId + ":" + sessionId;
        redisService.delete(unreadKey);

        // 如果不保留消息，删除该会话的所有消息
        if (!Boolean.TRUE.equals(keepMessages)) {
            LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ChatMessage::getSessionId, sessionId);
            // 对于私聊，只删除当前用户可见的消息（实际上是标记删除）
            // 为简化实现，这里直接删除消息
            chatMessageMapper.delete(wrapper);
            log.info("删除会话消息: sessionId={}", sessionId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessage(Long userId, Long messageId) {
        log.info("删除消息: userId={}, messageId={}", userId, messageId);

        ChatMessage message = chatMessageMapper.selectById(messageId);
        if (message == null) {
            throw new ServiceException("消息不存在");
        }

        // 验证权限：只能删除自己发送或接收的消息
        if (!message.getFromId().equals(userId) && !message.getToId().equals(userId)) {
            throw new ServiceException("无权删除此消息");
        }

        // 标记消息为已删除（status=2）
        message.setStatus(2);
        chatMessageMapper.updateById(message);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearMessages(Long userId, String sessionId) {
        log.info("清空会话消息: userId={}, sessionId={}", userId, sessionId);

        // 对于私聊，验证用户是否是会话参与者
        if (!sessionId.startsWith("group_")) {
            String[] parts = sessionId.split("_");
            if (parts.length == 2) {
                Long id1 = Long.parseLong(parts[0]);
                Long id2 = Long.parseLong(parts[1]);
                if (!userId.equals(id1) && !userId.equals(id2)) {
                    throw new ServiceException("无权操作此会话");
                }
            }
        }

        // 删除该会话的所有消息
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId);
        chatMessageMapper.delete(wrapper);

        // 清除未读计数
        String unreadKey = UNREAD_KEY + userId + ":" + sessionId;
        redisService.delete(unreadKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long userId, String sessionId) {
        log.info("标记消息已读: userId={}, sessionId={}", userId, sessionId);

        // 清除未读计数
        String unreadKey = UNREAD_KEY + userId + ":" + sessionId;
        redisService.delete(unreadKey);

        // 只处理私聊消息的已读状态（群聊不需要）
        if (!sessionId.startsWith("group_")) {
            // 查找该会话中发给当前用户的所有未读消息（兼容旧 sessionId 格式）
            LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();

            if (sessionId.startsWith("private_")) {
                // 新格式：private_min_max，同时兼容旧的 min_max
                String oldSessionId = sessionId.substring("private_".length());
                wrapper.and(w -> w.eq(ChatMessage::getSessionId, sessionId)
                        .or().eq(ChatMessage::getSessionId, oldSessionId));
            } else {
                // 旧格式：直接按原 sessionId 匹配
                wrapper.eq(ChatMessage::getSessionId, sessionId);
            }

            wrapper.eq(ChatMessage::getToId, userId)
                   .eq(ChatMessage::getCmdType, 1)  // 私聊
                   .and(w -> w.isNull(ChatMessage::getReadStatus).or().eq(ChatMessage::getReadStatus, 0));
            
            List<ChatMessage> unreadMessages = chatMessageMapper.selectList(wrapper);
            
            if (!unreadMessages.isEmpty()) {
                // 收集需要通知的发送者ID
                Set<Long> senderIds = new HashSet<>();
                
                // 批量更新消息为已读
                for (ChatMessage msg : unreadMessages) {
                    msg.setReadStatus(1);
                    chatMessageMapper.updateById(msg);
                    senderIds.add(msg.getFromId());
                }
                
                log.info("已更新 {} 条消息为已读状态", unreadMessages.size());
                
                // 通过 WebSocket 通知发送方消息已被读取
                for (Long senderId : senderIds) {
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("type", "read");
                    notification.put("sessionId", sessionId);
                    notification.put("readerId", String.valueOf(userId));  // 转为字符串避免 JS 大数字精度丢失
                    webSocketHandler.sendToUser(senderId, JSONUtil.toJsonStr(notification));
                }
            }
        }
    }
}
