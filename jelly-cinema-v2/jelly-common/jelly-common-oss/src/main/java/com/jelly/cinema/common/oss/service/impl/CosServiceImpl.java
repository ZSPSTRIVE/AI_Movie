package com.jelly.cinema.common.oss.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.jelly.cinema.common.core.exception.ServiceException;
import com.jelly.cinema.common.oss.config.CosProperties;
import com.jelly.cinema.common.oss.service.OssService;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 腾讯云 COS 服务实现
 *
 * @author Jelly Cinema
 */
@Slf4j
@RequiredArgsConstructor
public class CosServiceImpl implements OssService {

    private final COSClient cosClient;
    private final CosProperties properties;

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
            
            // 完整路径
            String key = StrUtil.isNotBlank(folder) 
                    ? folder + "/" + datePath + "/" + newFileName
                    : datePath + "/" + newFileName;

            // 设置元数据
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(inputStream.available());
            
            // 根据文件扩展名设置 Content-Type
            String contentType = getContentType(ext);
            metadata.setContentType(contentType);

            // 上传
            PutObjectRequest request = new PutObjectRequest(properties.getBucket(), key, inputStream, metadata);
            cosClient.putObject(request);

            // 返回访问 URL
            String url = properties.getHost() + "/" + key;
            log.info("文件上传成功: {}", url);
            return url;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw new ServiceException("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public boolean delete(String fileUrl) {
        try {
            // 从 URL 中提取 key
            String key = fileUrl.replace(properties.getHost() + "/", "");
            cosClient.deleteObject(properties.getBucket(), key);
            log.info("文件删除成功: {}", key);
            return true;
        } catch (CosClientException e) {
            log.error("文件删除失败", e);
            return false;
        }
    }

    @Override
    public String getPresignedUrl(String key, int expireMinutes) {
        Date expiration = new Date(System.currentTimeMillis() + expireMinutes * 60 * 1000L);
        URL url = cosClient.generatePresignedUrl(properties.getBucket(), key, expiration, HttpMethodName.GET);
        return url.toString();
    }

    /**
     * 根据文件扩展名获取 Content-Type
     */
    private String getContentType(String ext) {
        if (ext == null) {
            return "application/octet-stream";
        }
        return switch (ext.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "mp4" -> "video/mp4";
            case "mp3" -> "audio/mpeg";
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "txt" -> "text/plain";
            case "json" -> "application/json";
            default -> "application/octet-stream";
        };
    }
}
