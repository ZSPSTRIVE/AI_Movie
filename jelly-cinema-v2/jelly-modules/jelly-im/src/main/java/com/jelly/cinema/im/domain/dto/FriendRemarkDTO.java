package com.jelly.cinema.im.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 好友备注 DTO
 *
 * @author Jelly Cinema
 */
@Data
public class FriendRemarkDTO {

    /**
     * 好友ID
     */
    @NotNull(message = "好友ID不能为空")
    private Long friendId;

    /**
     * 备注名
     */
    @Size(max = 20, message = "备注名最长20个字符")
    private String remark;
}
