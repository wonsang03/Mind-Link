package com.mindlink.service;

import com.mindlink.domain.Notice;
import com.mindlink.dto.NoticeRequest;
import com.mindlink.repository.NoticeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final UserNotificationService notificationService;

    public NoticeService(NoticeRepository noticeRepository,
                         UserNotificationService notificationService) {
        this.noticeRepository = noticeRepository;
        this.notificationService = notificationService;
    }

    public List<Notice> findAll() {
        return noticeRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<Notice> findById(Long id) {
        return noticeRepository.findById(id);
    }

    @Transactional
    public Notice create(NoticeRequest req, boolean sendPushAlert) {
        Notice notice = new Notice();
        notice.setCategory(req.getCategory());
        notice.setTitle(req.getTitle());
        notice.setSummary(req.getSummary());
        notice.setContent(req.getContent());
        Notice saved = noticeRepository.save(notice);
        if (sendPushAlert) {
            notificationService.notifyNewNotice(saved);
        }
        return saved;
    }

    @Transactional
    public Notice update(Long id, NoticeRequest req) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다."));
        notice.setCategory(req.getCategory());
        notice.setTitle(req.getTitle());
        notice.setSummary(req.getSummary());
        notice.setContent(req.getContent());
        return notice;
    }

    @Transactional
    public void delete(Long id) {
        noticeRepository.deleteById(id);
    }
}
