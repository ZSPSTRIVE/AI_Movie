package com.jelly.cinema.common.oss.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * OSS 服务接口
 *
 * @author Jelly Cinema
 */
public interface OssService {

    /**
     * 上传文件
     *
     * @param file 文件
     * @param folder 文件夹路径
     * @return 文件访问 URL
     */
    String upload(MultipartFile file, String folder);

    /**
     * 上传文件
     *
     * @param inputStream 输入流
     * @param fileName 文件名
     * @param folder 文件夹路径
     * @return 文件访问 URL
     */
    String upload(InputStream inputStream, String fileName, String folder);

    /**
     * 删除文件
     *
     * @param fileUrl 文件 URL
     * @return 是否成功
     */
    boolean delete(String fileUrl);

    /**
     * 获取文件临时访问 URL（带签名）
     *
     * @param key 文件 key
     * @param expireMinutes 过期时间（分钟）
     * @return 临时访问 URL
     */
    String getPresignedUrl(String key, int expireMinutes);
}
