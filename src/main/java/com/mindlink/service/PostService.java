package com.mindlink.service;

import com.mindlink.repository.CommentRepository;
import com.mindlink.repository.PostRepository;
import com.mindlink.repository.ReportRepository;
import org.springframework.stereotype.Service;

/**
 * @deprecated CommunityService 로 대체되었습니다.
 */
@Deprecated
@Service
public class PostService extends CommunityService {

    public PostService(PostRepository postRepository,
                       CommentRepository commentRepository,
                       ReportRepository reportRepository,
                       FileStorageService fileStorageService) {
        super(postRepository, commentRepository, reportRepository, fileStorageService);
    }
}
