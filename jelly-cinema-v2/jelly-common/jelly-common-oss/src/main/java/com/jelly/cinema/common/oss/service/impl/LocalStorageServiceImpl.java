package com.jelly.cinema.common.oss.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.oss.service.OssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 本地文件存储服务实现（当 COS 未配置时使用）
 *
 * @author Jelly Cinema
 */
@Slf4j
@Service
@ConditionalOnMissingBean(OssService.class)
public class LocalStorageServiceImpl implements OssService {

    @Value("${storage.local.path:./uploads}")
    private String storagePath;

    @Value("${storage.local.domain:http://localhost:8060}")
    private String domain;

    @Override
    public String upload(MultipartFile file, String folder) {
        try {
            return upload(file.getInputStream(), file.getOriginalFilename(), folder);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new ServiceException("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public String upload(InputStream inputStream, String fileName, String folder) {
        try {
            // 生成唯一文件名
            String ext = FileUtil.extName(fileName);
            String newFileName = IdUtil.simpleUUID() + "." + ext;
            
            // 生成日期路径
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            
            // 完整目录路径
            String dirPath = StrUtil.isNotBlank(folder) 
                    ? storagePath + "/" + folder + "/" + datePath
                    : storagePath + "/" + datePath;
            
            // 创建目录
            Path directory = Paths.get(dirPath);
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }
            
            // 保存文件
            Path filePath = directory.resolve(newFileName);
            Files.copy(inputStream, filePath);
            
            // 返回访问 URL
            String relativePath = StrUtil.isNotBlank(folder) 
                    ? folder + "/" + datePath + "/" + newFileName
                    : datePath + "/" + newFileName;
            String url = domain + "/uploads/" + relativePath;
            
            log.info("文件上传成功（本地存储）: {}", url);
            return url;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new ServiceException("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public boolean delete(String fileUrl) {
        try {
            // 从 URL 中提取相对路径
            String relativePath = fileUrl.replace(domain + "/uploads/", "");
            Path filePath = Paths.get(storagePath, relativePath);
            
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("文件删除成功: {}", filePath);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("文件删除失败", e);
            return false;
        }
    }

    @Override
    public String getPresignedUrl(String key, int expireMinutes) {
        // 本地存储不需要签名 URL
        return domain + "/uploads/" + key;
    }
}
