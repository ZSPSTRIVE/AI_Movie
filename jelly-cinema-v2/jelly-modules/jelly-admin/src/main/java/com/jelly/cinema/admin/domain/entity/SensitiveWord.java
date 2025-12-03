package com.jelly.cinema.admin.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感词实体
 *
 * @author Jelly Cinema
 */
@Data
@TableName("t_sensitive_word")
public class SensitiveWord {

    /**
     * ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 敏感词
     */
    private String word;

    /**
     * 类型：1-政治, 2-色情, 3-暴恐, 4-广告, 5-其他
     */
    private Integer type;

    /**
     * 策略：1-替换为***, 2-直接拦截
     */
    private Integer strategy;

    /**
     * 状态：0-禁用, 1-启用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    // ========== 类型常量 ==========
    public static final int TYPE_POLITICS = 1;
    public static final int TYPE_PORN = 2;
    public static final int TYPE_VIOLENCE = 3;
    public static final int TYPE_AD = 4;
    public static final int TYPE_OTHER = 5;

    // ========== 策略常量 ==========
    public static final int STRATEGY_REPLACE = 1;
    public static final int STRATEGY_BLOCK = 2;
}
