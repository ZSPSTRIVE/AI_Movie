package com.jelly.cinema.auth.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_point")
public class UserPoint {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private Integer points;

    @Version
    private Integer version;

    private LocalDateTime updatedTime;
}
