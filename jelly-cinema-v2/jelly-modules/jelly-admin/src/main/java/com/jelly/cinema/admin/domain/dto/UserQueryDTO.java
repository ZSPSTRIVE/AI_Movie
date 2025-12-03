package com.jelly.cinema.admin.domain.dto;

import com.jelly.cinema.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户查询 DTO
 *
 * @author Jelly Cinema
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryDTO extends PageQuery {

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 角色
     */
    private String role;

    /**
     * 状态：0-正常，1-禁用
     */
    private Integer status;
}
