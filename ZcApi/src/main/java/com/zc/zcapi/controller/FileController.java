package com.zc.zcapi.controller;

import com.zc.zcapi.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final Path uploadDirectory;

    public FileController(@Value("${app.upload-dir:uploads}") String uploadDirectory) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    @PostMapping(value = "/avatars", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_AVATAR_SIZE || !ALLOWED_TYPES.contains(file.getContentType())) {
            throw new BusinessException(400, "请选择不超过 5MB 的 JPG、PNG 或 WebP 图片");
        }
        String extension = switch (file.getContentType()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String fileName = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(uploadDirectory.resolve("avatars"));
            file.transferTo(uploadDirectory.resolve("avatars").resolve(fileName));
        } catch (IOException exception) {
            throw new BusinessException(500, "头像保存失败");
        }
        return Map.of("url", "/uploads/avatars/" + fileName);
    }
}
