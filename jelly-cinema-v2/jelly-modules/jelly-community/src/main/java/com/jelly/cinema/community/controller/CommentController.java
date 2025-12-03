package com.jelly.cinema.community.controller;

import com.jelly.cinema.common.core.domain.PageQuery;
import com.jelly.cinema.common.core.domain.PageResult;
import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.community.domain.dto.CommentCreateDTO;
import com.jelly.cinema.community.domain.vo.CommentVO;
import com.jelly.cinema.community.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 评论控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "评论管理")
@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "获取帖子评论列表")
    @GetMapping("/list/{postId}")
    public R<PageResult<CommentVO>> list(@PathVariable Long postId, PageQuery query) {
        return R.ok(commentService.listByPostId(postId, query));
    }

    @Operation(summary = "发布评论")
    @PostMapping("/create")
    public R<Long> create(@Valid @RequestBody CommentCreateDTO dto) {
        return R.ok(commentService.create(dto));
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return R.ok();
    }

    @Operation(summary = "点赞评论")
    @PostMapping("/like/{id}")
    public R<Void> like(@PathVariable Long id) {
        commentService.like(id);
        return R.ok();
    }

    @Operation(summary = "取消点赞")
    @DeleteMapping("/like/{id}")
    public R<Void> unlike(@PathVariable Long id) {
        commentService.unlike(id);
        return R.ok();
    }
}
