package com.jelly.cinema.im.service;

import com.jelly.cinema.im.domain.vo.GroupSearchVO;
import com.jelly.cinema.im.domain.vo.UserSearchVO;

import java.util.List;

/**
 * 搜索服务
 *
 * @author Jelly Cinema
 */
public interface SearchService {

    /**
     * 搜索用户
     *
     * @param keyword 关键词（UID精确匹配或昵称模糊匹配）
     * @return 用户列表
     */
    List<UserSearchVO> searchUser(String keyword);

    /**
     * 搜索群组
     *
     * @param keyword 关键词（群号精确匹配或群名模糊匹配）
     * @return 群组列表
     */
    List<GroupSearchVO> searchGroup(String keyword);
}
