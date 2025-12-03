package com.jelly.cinema.community.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.redis.service.RedisService;
import com.jelly.cinema.common.security.utils.LoginHelper;
import com.jelly.cinema.community.domain.dto.PostCreateDTO;
import com.jelly.cinema.community.domain.entity.Post;
import com.jelly.cinema.community.domain.vo.PostVO;
import com.jelly.cinema.community.mapper.PostMapper;
import com.jelly.cinema.community.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 帖子服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final RedisService redisService;

    private static final String POST_VIEW_KEY = "jelly:post:view:";
    private static final String POST_VOTE_KEY = "jelly:post:vote:";

    @Override
    public PageResult<PostVO> list(PageQuery query, String keyword, Long filmId) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(keyword), Post::getTitle, keyword);
        wrapper.eq(filmId != null, Post::getFilmId, filmId);
        wrapper.eq(Post::getStatus, 0);
        wrapper.orderByDesc(Post::getCreateTime);

        Page<Post> page = postMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                wrapper
        );

        List<PostVO> voList = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return PageResult.build(voList, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    @Override
    public PostVO getDetail(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null || post.getStatus() == 1) {
            throw new ServiceException("帖子不存在");
        }
        return toVO(post);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PostCreateDTO dto) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }

        Post post = new Post();
        post.setUserId(userId);
        post.setTitle(dto.getTitle());
        post.setContentHtml(dto.getContentHtml());
        post.setContentSummary(extractSummary(dto.getContentHtml()));
        post.setFilmId(dto.getFilmId());
        post.setVoteUp(0);
        post.setVoteDown(0);
        post.setViewCount(0);
        post.setCommentCount(0);
        post.setStatus(0);

        postMapper.insert(post);
        log.info("用户 {} 发布帖子: {}", userId, post.getTitle());
        return post.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = LoginHelper.getUserId();
        Post post = postMapper.selectById(id);
        
        if (post == null) {
            throw new ServiceException("帖子不存在");
        }
        
        if (!post.getUserId().equals(userId) && !LoginHelper.isAdmin()) {
            throw new ServiceException("无权删除此帖子");
        }

        post.setStatus(1);
        postMapper.updateById(post);
        log.info("用户 {} 删除帖子: {}", userId, id);
    }

    @Override
    public void vote(Long id, Integer type) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }
        String key = POST_VOTE_KEY + id + ":" + userId;

        Integer currentVote = redisService.get(key);
        if (currentVote == null) {
            currentVote = 0;
        }

        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new ServiceException("帖子不存在");
        }

        // 取消之前的投票
        if (currentVote == 1) {
            post.setVoteUp(post.getVoteUp() - 1);
        } else if (currentVote == -1) {
            post.setVoteDown(post.getVoteDown() - 1);
        }

        // 应用新投票
        if (type == 1) {
            post.setVoteUp(post.getVoteUp() + 1);
        } else if (type == -1) {
            post.setVoteDown(post.getVoteDown() + 1);
        }

        postMapper.updateById(post);

        if (type == 0) {
            redisService.delete(key);
        } else {
            redisService.set(key, type, 365, TimeUnit.DAYS);
        }
    }

    @Override
    public void incrementViewCount(Long id) {
        String key = POST_VIEW_KEY + id;
        redisService.increment(key);
        
        // 每 10 次同步到数据库
        Object countObj = redisService.get(key);
        long count = countObj != null ? ((Number) countObj).longValue() : 0L;
        if (count > 0 && count % 10 == 0) {
            Post post = postMapper.selectById(id);
            if (post != null) {
                post.setViewCount(post.getViewCount() + 10);
                postMapper.updateById(post);
            }
        }
    }

    @Override
    public PageResult<PostVO> listMyPosts(PageQuery query) {
        Long userId = LoginHelper.getUserId();
        if (userId == null) {
            throw new ServiceException("请先登录");
        }

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getUserId, userId);
        wrapper.eq(Post::getStatus, 0);
        wrapper.orderByDesc(Post::getCreateTime);

        Page<Post> page = postMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                wrapper
        );

        List<PostVO> voList = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return PageResult.build(voList, page.getTotal(), query.getPageNum(), query.getPageSize());
    }

    /**
     * 提取摘要（去除 HTML 标签，截取前 200 字）
     */
    private String extractSummary(String html) {
        if (StrUtil.isBlank(html)) {
            return "";
        }
        String text = html.replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    /**
     * 转换为 VO
     */
    private PostVO toVO(Post post) {
        PostVO vo = BeanUtil.copyProperties(post, PostVO.class);

        // 填充默认用户信息
        if (vo.getUsername() == null) {
            vo.setUsername("用户" + post.getUserId()); // 临时使用 ID 作为用户名
            vo.setUserAvatar("https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png");
        }

        // 从 Redis 获取实时浏览次数
        String viewKey = POST_VIEW_KEY + post.getId();
        Object viewCountObj = redisService.get(viewKey);
        if (viewCountObj != null) {
            vo.setViewCount(((Number) viewCountObj).intValue());
        }

        // 获取当前用户投票状态
        if (LoginHelper.isLogin()) {
            Long userId = LoginHelper.getUserId();
            String key = POST_VOTE_KEY + post.getId() + ":" + userId;
            Integer voteStatus = redisService.get(key);
            vo.setVoteStatus(voteStatus != null ? voteStatus : 0);
        } else {
            vo.setVoteStatus(0);
        }

        return vo;
    }
}
