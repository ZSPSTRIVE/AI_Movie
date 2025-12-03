package com.jelly.cinema.community.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.common.api.domain.RemoteUser;
import com.jelly.cinema.common.api.feign.RemoteUserService;
import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.redis.service.RedisService;
import com.jelly.cinema.common.security.utils.LoginHelper;
import com.jelly.cinema.community.domain.dto.CommentCreateDTO;
import com.jelly.cinema.community.domain.entity.Comment;
import com.jelly.cinema.community.domain.entity.Post;
import com.jelly.cinema.community.domain.vo.CommentVO;
import com.jelly.cinema.community.mapper.CommentMapper;
import com.jelly.cinema.community.mapper.PostMapper;
import com.jelly.cinema.community.service.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 评论服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final RedisService redisService;
    private final RemoteUserService remoteUserService;

    private static final String COMMENT_LIKE_KEY = "jelly:comment:like:";

    @Override
    public PageResult<CommentVO> listByPostId(Long postId, PageQuery query) {
        // 查询根评论
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Comment::getPostId, postId);
        wrapper.isNull(Comment::getParentId);
        wrapper.eq(Comment::getStatus, 0);
        wrapper.orderByDesc(Comment::getCreateTime);

        Page<Comment> page = commentMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                wrapper
        );

        // 查询所有子评论
        List<Long> rootIds = page.getRecords().stream()
                .map(Comment::getId)
                .collect(Collectors.toList());

        Map<Long, List<Comment>> childrenMap = Map.of();
        if (!rootIds.isEmpty()) {
            LambdaQueryWrapper<Comment> childWrapper = new LambdaQueryWrapper<>();
            childWrapper.in(Comment::getRootId, rootIds);
            childWrapper.eq(Comment::getStatus, 0);
            childWrapper.orderByAsc(Comment::getCreateTime);

            List<Comment> children = commentMapper.selectList(childWrapper);
            childrenMap = children.stream()
                    .collect(Collectors.groupingBy(Comment::getRootId));
        }

        // 收集所有用户 ID
        Set<Long> userIds = new HashSet<>();
        page.getRecords().forEach(c -> userIds.add(c.getUserId()));
        childrenMap.values().forEach(list -> list.forEach(c -> {
            userIds.add(c.getUserId());
            if (c.getReplyUserId() != null) {
                userIds.add(c.getReplyUserId());
            }
        }));
        
        // 批量查询用户信息
        Map<Long, RemoteUser> userMap = batchGetUsers(userIds);
        
        // 转换为树形结构
        Map<Long, List<Comment>> finalChildrenMap = childrenMap;
        List<CommentVO> voList = page.getRecords().stream()
                .map(comment -> {
                    CommentVO vo = toVO(comment, userMap);
                    List<Comment> childList = finalChildrenMap.getOrDefault(comment.getId(), new ArrayList<>());
                    vo.setChildren(childList.stream().map(c -> toVO(c, userMap)).collect(Collectors.toList()));
                    return vo;
                })
                .collect(Collectors.toList());

        return PageResult.build(voList, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CommentCreateDTO dto) {
        Long userId = LoginHelper.getUserId();

        // 验证帖子存在
        Post post = postMapper.selectById(dto.getPostId());
        if (post == null || post.getStatus() == 1) {
            throw new ServiceException("帖子不存在");
        }

        Comment comment = new Comment();
        comment.setPostId(dto.getPostId());
        comment.setUserId(userId);
        comment.setContent(dto.getContent());
        comment.setLikeCount(0);
        comment.setStatus(0);

        // 处理回复
        if (dto.getParentId() != null) {
            Comment parent = commentMapper.selectById(dto.getParentId());
            if (parent == null) {
                throw new ServiceException("父评论不存在");
            }
            comment.setParentId(dto.getParentId());
            comment.setRootId(parent.getRootId() != null ? parent.getRootId() : parent.getId());
            comment.setReplyUserId(dto.getReplyUserId());
        }

        commentMapper.insert(comment);

        // 更新帖子评论数
        post.setCommentCount(post.getCommentCount() + 1);
        postMapper.updateById(post);

        log.info("用户 {} 评论帖子 {}", userId, dto.getPostId());
        return comment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = LoginHelper.getUserId();
        Comment comment = commentMapper.selectById(id);

        if (comment == null) {
            throw new ServiceException("评论不存在");
        }

        if (!comment.getUserId().equals(userId) && !LoginHelper.isAdmin()) {
            throw new ServiceException("无权删除此评论");
        }

        comment.setStatus(1);
        commentMapper.updateById(comment);

        // 更新帖子评论数
        Post post = postMapper.selectById(comment.getPostId());
        if (post != null) {
            post.setCommentCount(Math.max(0, post.getCommentCount() - 1));
            postMapper.updateById(post);
        }
    }

    @Override
    public void like(Long id) {
        Long userId = LoginHelper.getUserId();
        String key = COMMENT_LIKE_KEY + id;

        if (Boolean.TRUE.equals(redisService.sIsMember(key, userId))) {
            throw new ServiceException("已点赞");
        }

        redisService.sAdd(key, userId);

        Comment comment = commentMapper.selectById(id);
        if (comment != null) {
            comment.setLikeCount(comment.getLikeCount() + 1);
            commentMapper.updateById(comment);
        }
    }

    @Override
    public void unlike(Long id) {
        Long userId = LoginHelper.getUserId();
        String key = COMMENT_LIKE_KEY + id;

        redisService.sRemove(key, userId);

        Comment comment = commentMapper.selectById(id);
        if (comment != null) {
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            commentMapper.updateById(comment);
        }
    }

    /**
     * 批量获取用户信息
     */
    private Map<Long, RemoteUser> batchGetUsers(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            R<List<RemoteUser>> result = remoteUserService.getUsersByIds(new ArrayList<>(userIds));
            if (result.isSuccess() && result.getData() != null) {
                return result.getData().stream()
                        .collect(Collectors.toMap(RemoteUser::getId, u -> u, (a, b) -> a));
            }
        } catch (Exception e) {
            log.warn("批量查询用户信息失败: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    /**
     * 转换为 VO
     */
    private CommentVO toVO(Comment comment, Map<Long, RemoteUser> userMap) {
        CommentVO vo = BeanUtil.copyProperties(comment, CommentVO.class);

        // 检查当前用户是否已点赞
        if (LoginHelper.isLogin()) {
            Long userId = LoginHelper.getUserId();
            String key = COMMENT_LIKE_KEY + comment.getId();
            vo.setLiked(redisService.sIsMember(key, userId));
        } else {
            vo.setLiked(false);
        }

        // 填充用户信息
        RemoteUser user = userMap.get(comment.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
        }
        
        // 填充被回复用户信息
        if (comment.getReplyUserId() != null) {
            RemoteUser replyUser = userMap.get(comment.getReplyUserId());
            if (replyUser != null) {
                vo.setReplyNickname(replyUser.getNickname());
            }
        }
        
        return vo;
    }
}
