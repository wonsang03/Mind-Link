package com.mindlink.dto;

import com.mindlink.domain.Attachment;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AttachmentResponse {

    private static final Pattern YOUTUBE_PATTERN =
            Pattern.compile("(?:youtube\\.com/watch\\?v=|youtu\\.be/)([a-zA-Z0-9_-]+)");

    private Long id;
    private String fileUrl;
    private String originalFileName;
    private Attachment.FileType fileType;

    public AttachmentResponse(Attachment a) {
        this.id = a.getId();
        this.fileUrl = a.getFileUrl();
        this.originalFileName = a.getOriginalFileName();
        this.fileType = a.getFileType();
    }

    public Long getId() { return id; }
    public String getFileUrl() { return fileUrl; }
    public String getOriginalFileName() { return originalFileName; }
    public Attachment.FileType getFileType() { return fileType; }

    public boolean isYouTube() {
        return fileUrl != null && YOUTUBE_PATTERN.matcher(fileUrl).find();
    }

    public String getYouTubeEmbedUrl() {
        if (fileUrl == null) return null;
        Matcher m = YOUTUBE_PATTERN.matcher(fileUrl);
        return m.find() ? "https://www.youtube.com/embed/" + m.group(1) : null;
    }
}
