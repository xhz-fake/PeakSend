package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.properties.LocalUploadProperties;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.UUID;

/**
 * 通用接口
 */
@RestController
@RequestMapping("/admin/common")
@Slf4j
@Api(tags = "通用接口")
public class CommonController {

    @Autowired
    private LocalUploadProperties localUploadProperties;

    /**
     * 文件上传
     *
     * @param file
     * @return
     *
     */
    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) {// 是一种“表单 + 文件”的特殊请求格式
        log.info("文件上传：{}", file != null ? file.getOriginalFilename() : "null");

        if (file == null || file.isEmpty()) {
            return Result.error(MessageConstant.UPLOAD_FAILED);
        }
        //- 如果没传文件
        //- 或者文件是空的
        //- 直接返回失败

        if (!StringUtils.hasText(localUploadProperties.getBasePath())) {
            log.error("本地上传目录未配置，当前无法上传文件");
            return Result.error(MessageConstant.UPLOAD_FAILED);
        }
        //- 如果配置文件里根本没写本地上传目录
        //- 那我就不知道把图片存哪
        //- 所以直接失败

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String fileName = UUID.randomUUID() + extension;
            File baseDir = new File(localUploadProperties.getBasePath());
            if (!baseDir.exists() && !baseDir.mkdirs()) {
                log.error("本地上传目录创建失败：{}", baseDir.getAbsolutePath());
                return Result.error(MessageConstant.UPLOAD_FAILED);
            }

            File targetFile = new File(baseDir, fileName);
            file.transferTo(targetFile);

            String fileAccessUrl = String.format("%s://%s:%d/uploads/%s",
                    request.getScheme(),
                    request.getServerName(),
                    request.getServerPort(),
                    fileName);
            // - 根据当前请求的协议信息、主机、端口
            //- 拼出一个可访问地址
            //- 比如：
            //- http://localhost:8080/uploads/f756c50e-17fc-49f8-9265-a2f379b1e3fc.jpg
            //然后把这个地址返回给前端。

            log.info("文件已保存到本地：{}", targetFile.getAbsolutePath());
            return Result.success(fileAccessUrl);
        } catch (Exception e) {
            log.error("文件上传失败：{}", e.getMessage(), e);
            return Result.error(MessageConstant.UPLOAD_FAILED);
        }
    }
}
