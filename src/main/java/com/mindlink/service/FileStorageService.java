package com.mindlink.service;

import com.mindlink.domain.Attachment;
import com.mindlink.dto.AttachmentResponse;
import com.mindlink.repository.AttachmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FileStorageService {

    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> VIDEO_EXTS = Set.of("mp4", "webm");
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;   // 10 MB
    private static final long MAX_VIDEO_SIZE = 100L * 1024 * 1024;  // 100 MB

    private final AttachmentRepository attachmentRepository;
    private final Path uploadPath;

    public FileStorageService(AttachmentRepository attachmentRepository,
                               @Value("${app.upload.dir:uploads}") String uploadDir) {
        this.attachmentRepository = attachmentRepository;
        this.uploadPath = Paths.get(System.getProperty("user.dir"), uploadDir)
                .toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadPath);
        } catch (IOException e) {
            throw new RuntimeException("업로드 디렉토리를 생성할 수 없습니다: " + uploadPath, e);
        }
    }

    @Transactional
    public List<Attachment> saveFiles(MultipartFile[] files,
                                       Attachment.TargetType targetType,
                                       Long targetId) {
        List<Attachment> saved = new ArrayList<>();
        if (files == null) return saved;

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;

            String original = Objects.requireNonNullElse(file.getOriginalFilename(), "file");
            String ext = extension(original).toLowerCase();

            Attachment.FileType fileType = resolveType(ext);
            if (fileType == null) {
                throw new IllegalArgumentException("허용되지 않는 파일 형식입니다: " + ext
                        + " (허용: jpg/jpeg/png/gif/webp/mp4/webm)");
            }

            long maxSize = fileType == Attachment.FileType.IMAGE ? MAX_IMAGE_SIZE : MAX_VIDEO_SIZE;
            if (file.getSize() > maxSize) {
                String limit = fileType == Attachment.FileType.IMAGE ? "10MB" : "100MB";
                throw new IllegalArgumentException(
                        "파일 크기(" + (file.getSize() / 1024 / 1024) + "MB)가 " + limit + "를 초과합니다: " + original);
            }

            String stored = UUID.randomUUID() + "." + ext;
            try {
                file.transferTo(uploadPath.resolve(stored));
            } catch (IOException e) {
                throw new RuntimeException("파일 저장에 실패했습니다: " + original, e);
            }

            Attachment a = new Attachment();
            a.setOriginalFileName(original);
            a.setStoredFileName(stored);
            a.setFileUrl("/uploads/" + stored);
            a.setFileType(fileType);
            a.setFileSize(file.getSize());
            a.setTargetType(targetType);
            a.setTargetId(targetId);
            saved.add(attachmentRepository.save(a));
        }
        return saved;
    }

    @Transactional
    public void deleteByTarget(Attachment.TargetType targetType, Long targetId) {
        List<Attachment> list = attachmentRepository.findByTargetTypeAndTargetId(targetType, targetId);
        for (Attachment a : list) {
            deleteFile(a.getStoredFileName());
        }
        attachmentRepository.deleteByTargetTypeAndTargetId(targetType, targetId);
    }

    @Transactional
    public List<Attachment> saveLinks(String[] urls,
                                       Attachment.TargetType targetType,
                                       Long targetId) {
        List<Attachment> saved = new ArrayList<>();
        if (urls == null) return saved;
        for (String url : urls) {
            if (url == null || url.isBlank()) continue;
            String trimmed = url.trim();
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                throw new IllegalArgumentException("유효한 URL 형식이 아닙니다: " + trimmed);
            }
            Attachment a = new Attachment();
            a.setOriginalFileName(trimmed);
            a.setStoredFileName("");
            a.setFileUrl(trimmed);
            a.setFileType(Attachment.FileType.LINK);
            a.setFileSize(0);
            a.setTargetType(targetType);
            a.setTargetId(targetId);
            saved.add(attachmentRepository.save(a));
        }
        return saved;
    }

    @Transactional
    public void deleteById(Long attachmentId) {
        attachmentRepository.findById(attachmentId).ifPresent(a -> {
            deleteFile(a.getStoredFileName());
            attachmentRepository.delete(a);
        });
    }

    public List<AttachmentResponse> findByTarget(Attachment.TargetType targetType, Long targetId) {
        return attachmentRepository.findByTargetTypeAndTargetId(targetType, targetId)
                .stream().map(AttachmentResponse::new).collect(Collectors.toList());
    }

    public String saveProfileImage(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        String original = Objects.requireNonNullElse(file.getOriginalFilename(), "profile");
        String ext = extension(original).toLowerCase();
        if (!IMAGE_EXTS.contains(ext)) {
            throw new IllegalArgumentException("프로필 사진은 이미지 파일만 업로드할 수 있습니다. (jpg/jpeg/png/gif/webp)");
        }
        long maxProfileSize = 5L * 1024 * 1024;
        if (file.getSize() > maxProfileSize) {
            throw new IllegalArgumentException("프로필 사진 크기가 5MB를 초과합니다.");
        }
        String stored = UUID.randomUUID() + "." + ext;
        try {
            file.transferTo(uploadPath.resolve(stored));
        } catch (IOException e) {
            throw new RuntimeException("프로필 사진 저장에 실패했습니다.", e);
        }
        return "/uploads/" + stored;
    }

    public void deleteProfileImage(String profileImageUrl) {
        if (profileImageUrl == null || profileImageUrl.isBlank()) return;
        String stored = profileImageUrl.startsWith("/uploads/")
                ? profileImageUrl.substring("/uploads/".length())
                : profileImageUrl;
        deleteFile(stored);
    }

    private void deleteFile(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) return;
        try {
            Files.deleteIfExists(uploadPath.resolve(storedFileName));
        } catch (IOException ignored) {}
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }

    private Attachment.FileType resolveType(String ext) {
        if (IMAGE_EXTS.contains(ext)) return Attachment.FileType.IMAGE;
        if (VIDEO_EXTS.contains(ext)) return Attachment.FileType.VIDEO;
        return null;
    }
}
