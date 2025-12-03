package com.jelly.cinema.common.oss.controller;

import com.jelly.cinema.common.core.domain.R;
import com.jelly.cinema.common.oss.service.OssService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件上传控制器
 *
 * @author Jelly Cinema
 */
@Tag(name = "文件上传")
@RestController
@RequestMapping("/oss")
@AllArgsConstructor
public class OssController {

    private final OssService ossService;

    @Operation(summary = "上传单个文件")
    @PostMapping("/upload")
    public R<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "upload") String folder) {
        String url = ossService.upload(file, folder);
        return R.ok(url);
    }

    @Operation(summary = "上传多个文件")
    @PostMapping("/upload/batch")
    public R<List<String>> uploadBatch(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "folder", defaultValue = "upload") String folder) {
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            urls.add(ossService.upload(file, folder));
        }
        return R.ok(urls);
    }

    @Operation(summary = "上传图片")
    @PostMapping("/upload/image")
    public R<String> uploadImage(@RequestParam("file") MultipartFile file) {
        return R.ok(ossService.upload(file, "images"));
    }

    @Operation(summary = "上传视频")
    @PostMapping("/upload/video")
    public R<String> uploadVideo(@RequestParam("file") MultipartFile file) {
        return R.ok(ossService.upload(file, "videos"));
    }

    @Operation(summary = "上传头像")
    @PostMapping("/upload/avatar")
    public R<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return R.ok(ossService.upload(file, "avatars"));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping
    public R<Boolean> delete(@RequestParam("url") String url) {
        return R.ok(ossService.delete(url));
    }
}
