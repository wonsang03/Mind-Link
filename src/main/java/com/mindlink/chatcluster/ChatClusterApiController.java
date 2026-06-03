package com.mindlink.chatcluster;

import com.mindlink.domain.User;
import com.mindlink.domain.UserRole;
import com.mindlink.service.UserService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 정서 3D 클러스터링 REST API (Three.js viewer 백엔드).
 *  GET  /api/chat-cluster/visualization  3D 산점도용 JSON (비로그인 OK)
 *  GET  /api/chat-cluster/search         실사용자 이름 검색
 *  GET  /api/chat-cluster/my             내 점·집단·추천 (로그인 필요)
 *  POST /api/chat-cluster/profile        내 점수 저장 (로그인 필요)
 *  POST /api/chat-cluster/recompute      전체 재계산 (ADMIN)
 */
@RestController
@RequestMapping("/api/chat-cluster")
public class ChatClusterApiController {

    private final ClusterKMeansEngine kmeansEngine;
    private final ClusterVisualizationService visualizationService;
    private final ClusterProfileService profileService;
    private final UserService userService;

    public ChatClusterApiController(ClusterKMeansEngine kmeansEngine,
                                     ClusterVisualizationService visualizationService,
                                     ClusterProfileService profileService,
                                     UserService userService) {
        this.kmeansEngine = kmeansEngine;
        this.visualizationService = visualizationService;
        this.profileService = profileService;
        this.userService = userService;
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam("q") String q) {
        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "검색어(q)를 입력해 주세요."));
        }
        return ResponseEntity.ok(profileService.searchRealUsersByName(q));
    }

    @GetMapping("/visualization")
    public ResponseEntity<?> visualization(
            @RequestParam(defaultValue = "true") boolean includeSynthetic,
            @RequestParam(defaultValue = "true") boolean includeReal,
            @RequestParam(required = false) Integer highlightCluster,
            HttpSession session) {
        Long uid = sessionUserId(session);
        return ResponseEntity.ok(
                visualizationService.visualization(includeSynthetic, includeReal, highlightCluster, uid));
    }

    @GetMapping("/my")
    public ResponseEntity<?> my(HttpSession session) {
        Long uid = sessionUserId(session);
        if (uid == null) return unauthorized();
        return ResponseEntity.ok(profileService.buildMyClusterResponse(uid));
    }

    @PostMapping("/profile")
    public ResponseEntity<?> saveProfile(@RequestBody ClusterDtos.SaveProfileRequest req,
                                          HttpSession session) {
        Long uid = sessionUserId(session);
        if (uid == null) return unauthorized();
        if (req == null
                || req.stressScore() == null
                || req.depressionScore() == null
                || req.anxietyScore() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "stressScore·depressionScore·anxietyScore 가 모두 필요해요."));
        }
        User user = userService.findById(uid).orElse(null);
        if (user == null) return unauthorized();
        try {
            UserAssessmentProfile saved = profileService.saveOrUpdateUserProfile(
                    uid, user.getName(), req.stressScore(), req.depressionScore(), req.anxietyScore());
            return ResponseEntity.ok(Map.of(
                    "id", saved.getId(),
                    "clusterId", saved.getClusterId(),
                    "stressNorm", saved.getStressNorm(),
                    "depressionNorm", saved.getDepressionNorm(),
                    "anxietyNorm", saved.getAnxietyNorm()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/recompute")
    public ResponseEntity<?> recompute(HttpSession session) {
        Long uid = sessionUserId(session);
        if (uid == null) return unauthorized();
        User user = userService.findById(uid).orElse(null);
        if (user == null || user.getRole() != UserRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "관리자만 재계산할 수 있어요."));
        }
        return ResponseEntity.ok(kmeansEngine.recompute());
    }

    /** E-1 — 실사용자만(페르소나 제외) 군집 통계. ADMIN 전용. */
    @GetMapping("/stats/real")
    public ResponseEntity<?> realClusterStats(HttpSession session) {
        Long uid = sessionUserId(session);
        if (uid == null) return unauthorized();
        User user = userService.findById(uid).orElse(null);
        if (user == null || user.getRole() != UserRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "관리자만 조회할 수 있어요."));
        }
        return ResponseEntity.ok(profileService.realClusterStats());
    }

    private static Long sessionUserId(HttpSession session) {
        Object uid = session.getAttribute(SessionConst.LOGIN_USER_ID);
        return uid instanceof Long id ? id : null;
    }

    private static ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "로그인이 필요합니다."));
    }
}
