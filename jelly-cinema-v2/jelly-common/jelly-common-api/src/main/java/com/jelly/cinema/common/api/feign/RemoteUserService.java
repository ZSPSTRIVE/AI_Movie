package com.jelly.cinema.common.api.feign;

import com.jelly.cinema.common.api.domain.RemoteUser;
import com.jelly.cinema.common.api.feign.fallback.RemoteUserFallback;
import com.jelly.cinema.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 用户服务 Feign 接口
 *
 * @author Jelly Cinema
 */
@FeignClient(value = "jelly-auth", fallbackFactory = RemoteUserFallback.class)
public interface RemoteUserService {

    /**
     * 根据ID获取用户信息
     */
    @GetMapping("/auth/user/info/{id}")
    R<RemoteUser> getUserById(@PathVariable("id") Long id);

    /**
     * 批量获取用户信息
     */
    @GetMapping("/auth/user/batch")
    R<List<RemoteUser>> getUsersByIds(@RequestParam("ids") List<Long> ids);

    /**
     * 搜索用户
     */
    @GetMapping("/auth/user/search")
    R<List<RemoteUser>> searchUsers(@RequestParam("keyword") String keyword);
}
