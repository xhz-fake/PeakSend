package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sky.local-upload")
@Data
public class LocalUploadProperties {
    /**
     * 本地上传文件保存目录
     */
    private String basePath;
}
