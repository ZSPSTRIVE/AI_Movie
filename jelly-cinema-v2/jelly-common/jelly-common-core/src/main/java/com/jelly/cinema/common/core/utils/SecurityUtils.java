package com.jelly.cinema.common.core.utils;

import cn.hutool.crypto.digest.BCrypt;
import com.jelly.cinema.common.core.exception.ServiceException;

/**
 * 安全工具类
 *
 * @author Jelly Cinema
 */
public class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 生成 BCrypt 密码
     *
     * @param password 原始密码
     * @return 加密后的密码
     */
    public static String encryptPassword(String password) {
        return BCrypt.hashpw(password);
    }

    /**
     * 校验密码
     *
     * @param rawPassword     原始密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    public static boolean matchesPassword(String rawPassword, String encodedPassword) {
        return BCrypt.checkpw(rawPassword, encodedPassword);
    }

    /**
     * 校验密码，不匹配则抛异常
     *
     * @param rawPassword     原始密码
     * @param encodedPassword 加密后的密码
     */
    public static void checkPassword(String rawPassword, String encodedPassword) {
        if (!matchesPassword(rawPassword, encodedPassword)) {
            throw new ServiceException(401, "密码错误");
        }
    }
}
