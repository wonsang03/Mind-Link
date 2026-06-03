package com.mindlink.chatcluster;

import com.mindlink.domain.Post;
import com.mindlink.domain.User;
import com.mindlink.repository.PostRepository;
import com.mindlink.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * C-1 — 같은 정서 군집(cluster_id)의 <b>실사용자</b>가 작성한 인기 게시글을 제공한다.
 *
 * <p>한계: {@code posts} 테이블에는 {@code user_id} FK가 없고 {@code author}(문자열)만 있어,
 * 작성자↔사용자 연결을 {@code users.name == posts.author} 로 근사한다.
 * 동명이인·닉네임/익명 사용 시 정확도가 떨어질 수 있으며, 이는 설계상 알려진 제약이다
 * (자세한 내용: docs/PERSONALIZATION_DESIGN.md / HANDOFF 문서).
 */
@Service
public class ClusterContentService {

    private final ClusterProfileService clusterProfileService;
    private final UserAssessmentProfileRepository profileRepo;
    private final UserRepository userRepository;
    private final PostRepository postRepository;

    public ClusterContentService(ClusterProfileService clusterProfileService,
                                  UserAssessmentProfileRepository profileRepo,
                                  UserRepository userRepository,
                                  PostRepository postRepository) {
        this.clusterProfileService = clusterProfileService;
        this.profileRepo = profileRepo;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
    }

    /**
     * 로그인 사용자와 같은 군집의 실사용자들이 쓴 인기 게시글 Top {@code limit}.
     * 프로필/군집이 없거나 매칭 게시글이 없으면 {@code null}.
     */
    public Result popularPostsForUserCluster(Long userId, int limit) {
        if (userId == null) return null;
        ClusterDtos.MyClusterResponse my = clusterProfileService.buildMyClusterResponse(userId);
        if (!my.hasProfile()) return null;

        List<Long> userIds = profileRepo.findRealUserIdsByCluster(my.clusterId());
        if (userIds.isEmpty()) return null;

        // author = users.name 근사 (위 클래스 주석의 한계 참고)
        List<String> authorNames = userRepository.findAllById(userIds).stream()
                .map(User::getName)
                .filter(n -> n != null && !n.isBlank())
                .distinct()
                .toList();
        if (authorNames.isEmpty()) return null;

        List<Post> posts = postRepository.findByAuthorInOrderByLikesDescCreatedAtDesc(authorNames);
        if (posts.isEmpty()) return null;
        if (limit > 0 && posts.size() > limit) {
            posts = posts.subList(0, limit);
        }
        return new Result(my.clusterLabel(), posts);
    }

    /** 유형 라벨 + 인기 게시글. */
    public record Result(String clusterLabel, List<Post> posts) {}
}
