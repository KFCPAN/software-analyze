package com.lnf.server.service;

import com.lnf.server.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 文件存储服务：图片上传到本地 uploads/yyyyMM/ 目录
 */
@Slf4j
@Service
public class FileStorageService {

    private static final long MAX_SIZE = 10L * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final DateTimeFormatter MONTH_DIR = DateTimeFormatter.ofPattern("yyyyMM");

    private final Path uploadRoot;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * 存储单张图片，返回访问路径 /files/yyyyMM/uuid.ext
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(2009, "上传文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(2008, "文件大小超过 10MB 限制");
        }

        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = originalName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException(2007, "仅支持 jpg/jpeg/png/webp 格式图片");
        }

        String monthDir = LocalDate.now().format(MONTH_DIR);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path targetDir = uploadRoot.resolve(monthDir);
        Path target = targetDir.resolve(fileName);
        try {
            Files.createDirectories(targetDir);
            file.transferTo(target);
        } catch (IOException e) {
            log.error("文件保存失败: {}", target, e);
            throw new BizException(2010, "文件保存失败，请重试");
        }

        // TODO(后续周次)：证件/人脸图片自动打码后存储
        return "/files/" + monthDir + "/" + fileName;
    }
}
