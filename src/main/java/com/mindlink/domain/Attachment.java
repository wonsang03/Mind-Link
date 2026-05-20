package com.mindlink.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String originalFileName;

    @Column(nullable = false, length = 255)
    private String storedFileName;

    @Column(nullable = false, length = 500)
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FileType fileType;

    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public enum FileType { IMAGE, VIDEO, LINK }
    public enum TargetType { POST, COMMENT }

    public Attachment() {}

    public Long getId() { return id; }
    public String getOriginalFileName() { return originalFileName; }
    public String getStoredFileName() { return storedFileName; }
    public String getFileUrl() { return fileUrl; }
    public FileType getFileType() { return fileType; }
    public long getFileSize() { return fileSize; }
    public TargetType getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setOriginalFileName(String v) { this.originalFileName = v; }
    public void setStoredFileName(String v) { this.storedFileName = v; }
    public void setFileUrl(String v) { this.fileUrl = v; }
    public void setFileType(FileType v) { this.fileType = v; }
    public void setFileSize(long v) { this.fileSize = v; }
    public void setTargetType(TargetType v) { this.targetType = v; }
    public void setTargetId(Long v) { this.targetId = v; }
}
