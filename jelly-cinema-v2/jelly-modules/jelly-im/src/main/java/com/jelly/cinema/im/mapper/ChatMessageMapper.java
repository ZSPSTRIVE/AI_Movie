package com.jelly.cinema.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jelly.cinema.im.domain.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息 Mapper
 *
 * @author Jelly Cinema
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

}
