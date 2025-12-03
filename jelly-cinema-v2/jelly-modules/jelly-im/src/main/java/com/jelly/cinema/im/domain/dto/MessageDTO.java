package com.jelly.cinema.im.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 消息 DTO
 *
 * @author Jelly Cinema
 */
@Data
public class MessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息类型：1-私聊，2-群聊
     */
    private Integer cmdType = 1;

    /**
     * 接收者 ID (使用 String 接收，避免 JavaScript 大数字精度丢失)
     */
    private String toId;

    /**
     * 内容类型：1-文本，2-图片，3-文件，4-语音
     */
    private Integer msgType = 1;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 扩展字段
     */
    private String extra;
}
