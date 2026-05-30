# 관리자 모니터링 페이지 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `/admin/monitoring` 페이지를 추가한다 — 클러스터별 인원 요약 카드, 고위험군 알림 탭(건별 확인·강조 영구 유지), 고위험군 관리 탭(테이블·인라인 메시지 전송).

**Architecture:** `user_alerts`에 `admin_confirmed` 컬럼을 추가해 관리자 확인 상태를 DB에 영구 저장한다. 집계 전용 `AdminMonitoringService`를 신규로 만들고, `MonitoringService`에는 관리자용 HIGH_RISK 알림 조회·확인 메서드를 추가한다. `AdminController`에 `@ModelAttribute`로 미확인 배지 수를 모든 admin 페이지에 공통 주입한다.

**Tech Stack:** Java 17, Spring Boot 4, Spring Data JPA, Thymeleaf, Oracle DB, 순수 JS (jQuery 없음)

---

### Task 1: DB 패치 스크립트

**Files:**
- Create: `sql/02_features/MONITORING_ADMIN_CONFIRMED.sql`

- [ ] **Step 1: SQL 파일 생성**

```sql
-- sql/02_features/MONITORING_ADMIN_CONFIRMED.sql
-- 관리자 모니터링: HIGH_RISK 알림 확인 여부 컬럼 추가
ALTER TABLE user_alerts ADD admin_confirmed NUMBER(1) DEFAULT 0 NOT NULL;
COMMENT ON COLUMN user_alerts.admin_confirmed IS '관리자 확인 여부 (1=확인, 0=미확인). HIGH_RISK 알림 전용.';
```

- [ ] **Step 2: Oracle SQL Developer(또는 동등한 도구)에서 APP_USER 계정으로 실행**

실행 후 확인:
```sql
SELECT column_name, data_type, data_default
FROM user_tab_columns
WHERE table_name = 'USER_ALERTS' AND column_name = 'ADMIN_CONFIRMED';
-- 결과: ADMIN_CONFIRMED | NUMBER | 0
```

---

### Task 2: UserAlert 도메인 — adminConfirmed 필드 추가

**Files:**
- Modify: `src/main/java/com/mindlink/domain/UserAlert.java`

- [ ] **Step 1: 필드·getter·setter 추가**

`UserAlert.java`의 `private boolean read;` 바로 아래에 추가:

```java
@Column(name = "admin_confirmed", nullable = false)
private boolean adminConfirmed;
```

`isRead()` getter 아래에 추가:

```java
public boolean isAdminConfirmed() { return adminConfirmed; }
public void setAdminConfirmed(boolean adminConfirmed) { this.adminConfirmed = adminConfirmed; }
```

- [ ] **Step 2: 앱 기동 확인 (JPA 매핑 오류 없음)**

```bash
./mvnw spring-boot:run -q 2>&1 | head -30
```

기동 성공 시 `Started MindLinkApplication` 로그 확인. 컬럼 미실행 상태면 `ORA-00904: "ADMIN_CONFIRMED"` 에러 → Task 1 먼저 실행.

---

### Task 3: Repository 메서드 추가 (3개 파일)

**Files:**
- Modify: `src/main/java/com/mindlink/repository/UserAlertRepository.java`
- Modify: `src/main/java/com/mindlink/chatcluster/UserAssessmentProfileRepository.java`
- Modify: `src/main/java/com/mindlink/repository/AssessmentResultRepository.java`

- [ ] **Step 1: UserAlertRepository — HIGH_RISK 전용 메서드 2개 추가**

기존 `void deleteByUser(User user);` 아래에 추가:

```java
List<UserAlert> findByAlertTypeOrderByCreatedAtDesc(String alertType);

long countByAlertTypeAndAdminConfirmedFalse(String alertType);
```

- [ ] **Step 2: UserAssessmentProfileRepository — 클러스터별 실사용자 수 쿼리 추가**

기존 `searchRealUsers` 아래에 추가:

```java
@Query("""
        SELECT p.clusterId, COUNT(p) FROM UserAssessmentProfile p
        WHERE p.userId IS NOT NULL AND p.isSynthetic = 0
        GROUP BY p.clusterId
        """)
List<Object[]> countRealUsersByCluster();
```

- [ ] **Step 3: AssessmentResultRepository — 유저별 최신 고위험 결과 쿼리 추가**

기존 `findTop5ByHighRiskTrueOrderByCompletedAtDesc()` 아래에 추가:

```java
@Query("""
        SELECT r FROM AssessmentResult r
        WHERE r.highRisk = true
          AND r.completedAt = (
              SELECT MAX(r2.completedAt) FROM AssessmentResult r2
              WHERE r2.user = r.user AND r2.highRisk = true
          )
        ORDER BY r.completedAt DESC
        """)
List<AssessmentResult> findLatestHighRiskPerUser();
```

- [ ] **Step 4: 컴파일 확인**

```bash
./mvnw compile -q
```

`BUILD SUCCESS` 확인.

---

### Task 4: MonitoringService — 관리자용 메서드 3개 추가

**Files:**
- Modify: `src/main/java/com/mindlink/service/MonitoringService.java`

- [ ] **Step 1: 메서드 3개 추가**

클래스 마지막 `deleteAllAlerts` 메서드 아래에 추가:

```java
// ===== 관리자 전용 =====

public List<UserAlert> getHighRiskAlertsForAdmin() {
    return alertRepo.findByAlertTypeOrderByCreatedAtDesc("HIGH_RISK");
}

@Transactional
public void confirmHighRiskAlert(Long alertId) {
    alertRepo.findById(alertId).ifPresent(a -> a.setAdminConfirmed(true));
}

public long countUnconfirmedHighRisk() {
    return alertRepo.countByAlertTypeAndAdminConfirmedFalse("HIGH_RISK");
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./mvnw compile -q
```

`BUILD SUCCESS` 확인.

---

### Task 5: AdminMonitoringService 신규 생성

**Files:**
- Create: `src/main/java/com/mindlink/service/AdminMonitoringService.java`

- [ ] **Step 1: 파일 생성**

```java
package com.mindlink.service;

import com.mindlink.chatcluster.ClusterVisualizationService;
import com.mindlink.chatcluster.UserAssessmentProfileRepository;
import com.mindlink.domain.AssessmentResult;
import com.mindlink.domain.User;
import com.mindlink.repository.AssessmentResultRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AdminMonitoringService {

    public record ClusterSummary(int clusterId, String label, long memberCount) {}

    public record HighRiskUserInfo(Long userId, String name, String email,
                                   String typeKey, String typeName, String level,
                                   LocalDateTime completedAt) {}

    private final ClusterVisualizationService vizService;
    private final UserAssessmentProfileRepository profileRepo;
    private final AssessmentResultRepository resultRepo;

    public AdminMonitoringService(ClusterVisualizationService vizService,
                                  UserAssessmentProfileRepository profileRepo,
                                  AssessmentResultRepository resultRepo) {
        this.vizService   = vizService;
        this.profileRepo  = profileRepo;
        this.resultRepo   = resultRepo;
    }

    // ===== 클러스터 요약 카드 =====

    public List<ClusterSummary> getClusterSummaries() {
        // 실사용자 클러스터별 수
        Map<Integer, Long> countMap = new LinkedHashMap<>();
        for (Object[] row : profileRepo.countRealUsersByCluster()) {
            Integer cid = (Integer) row[0];
            Long    cnt = (Long)    row[1];
            if (cid != null) countMap.put(cid, cnt);
        }

        // 중심 좌표 → 레이블 도출
        Map<Integer, double[]> centroidMap = new LinkedHashMap<>();
        vizService.visualization(true, true, null, null)
                  .centroids()
                  .forEach(c -> centroidMap.put(c.clusterId(),
                          new double[]{c.centroidS(), c.centroidD(), c.centroidA()}));

        List<ClusterSummary> result = new ArrayList<>();
        for (Map.Entry<Integer, double[]> e : centroidMap.entrySet()) {
            int cid = e.getKey();
            double[] coords = e.getValue();
            String label = deriveLabel(coords[0], coords[1], coords[2]);
            long count = countMap.getOrDefault(cid, 0L);
            result.add(new ClusterSummary(cid, label, count));
        }
        result.sort(Comparator.comparingInt(ClusterSummary::clusterId));
        return result;
    }

    private String deriveLabel(double s, double d, double a) {
        if (s >= 0.6 && d >= 0.6 && a >= 0.6) return "고위험";
        if (s < 0.35 && d < 0.35 && a < 0.35)  return "안정";
        if (s >= 0.5 && d < 0.4 && a < 0.4)    return "스트레스";
        if (d >= 0.5 && s < 0.4 && a < 0.4)    return "우울";
        if (a >= 0.5 && s < 0.4 && d < 0.4)    return "불안";
        return "복합";
    }

    // ===== 고위험군 유저 목록 =====

    public List<HighRiskUserInfo> getHighRiskUsers() {
        List<AssessmentResult> latest = resultRepo.findLatestHighRiskPerUser();
        List<HighRiskUserInfo> result = new ArrayList<>();
        for (AssessmentResult r : latest) {
            User u = r.getUser();
            result.add(new HighRiskUserInfo(
                    u.getId(), u.getName(), u.getEmail(),
                    r.getTypeKey(), r.getTypeName(), r.getLevel(),
                    r.getCompletedAt()
            ));
        }
        return result;
    }
}
```

- [ ] **Step 2: 컴파일 확인**

```bash
./mvnw compile -q
```

`BUILD SUCCESS` 확인.

---

### Task 6: AdminController — @ModelAttribute 공통화 + 모니터링 엔드포인트 3개

**Files:**
- Modify: `src/main/java/com/mindlink/controller/AdminController.java`

- [ ] **Step 1: import 추가 및 의존성 주입**

클래스 상단 import에 추가:

```java
import com.mindlink.service.AdminMonitoringService;
import com.mindlink.service.MonitoringService;
import org.springframework.web.bind.annotation.ModelAttribute;
```

생성자 주입 필드 추가 (`private final UserNotificationService notificationService;` 아래):

```java
private final AdminMonitoringService adminMonitoringService;
private final MonitoringService monitoringService;
```

생성자 파라미터 및 본문에 추가 (기존 생성자 시그니처 뒤에 두 파라미터 추가):

```java
public AdminController(AdminService adminService,
                       CommunityService communityService,
                       NoticeService noticeService,
                       UserService userService,
                       SqlConsoleService sqlConsoleService,
                       UserNotificationService notificationService,
                       AdminMonitoringService adminMonitoringService,
                       MonitoringService monitoringService) {
    this.adminService            = adminService;
    this.communityService        = communityService;
    this.noticeService           = noticeService;
    this.userService             = userService;
    this.sqlConsoleService       = sqlConsoleService;
    this.notificationService     = notificationService;
    this.adminMonitoringService  = adminMonitoringService;
    this.monitoringService       = monitoringService;
}
```

- [ ] **Step 2: @ModelAttribute 공통 메서드 추가**

클래스 내 `// ===== 대시보드 =====` 바로 위에 추가:

```java
// ===== 공통 모델 속성 (모든 GET 자동 주입) =====

@ModelAttribute("unconfirmedHighRiskCount")
public long unconfirmedHighRiskCount(HttpSession session) {
    Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
    if (!(userId instanceof Long uid)) return 0L;
    return userService.findById(uid)
            .filter(u -> u.getRole() == UserRole.ADMIN)
            .map(u -> monitoringService.countUnconfirmedHighRisk())
            .orElse(0L);
}
```

- [ ] **Step 3: 모니터링 엔드포인트 3개 추가**

`// ===== 서버 로그 뷰어 =====` 섹션 바로 위에 추가:

```java
// ===== 모니터링 =====

@GetMapping("/monitoring")
public String monitoring(HttpSession session, Model model, RedirectAttributes ra) {
    if (!isAdmin(session)) return denied(ra);
    model.addAttribute("clusterSummaries",  adminMonitoringService.getClusterSummaries());
    model.addAttribute("highRiskAlerts",    monitoringService.getHighRiskAlertsForAdmin());
    model.addAttribute("unconfirmedCount",  monitoringService.countUnconfirmedHighRisk());
    model.addAttribute("highRiskUsers",     adminMonitoringService.getHighRiskUsers());
    return "admin/monitoring";
}

@PostMapping("/monitoring/confirm/{alertId}")
public String confirmAlert(@PathVariable Long alertId,
                           HttpSession session, RedirectAttributes ra) {
    if (!isAdmin(session)) return denied(ra);
    monitoringService.confirmHighRiskAlert(alertId);
    return "redirect:/admin/monitoring#alerts";
}

@PostMapping("/monitoring/message")
public String sendMonitoringMessage(@RequestParam Long userId,
                                    @RequestParam(required = false) String title,
                                    @RequestParam String message,
                                    HttpSession session, RedirectAttributes ra) {
    if (!isAdmin(session)) return denied(ra);
    try {
        notificationService.sendAdminMessage(userId, title, message, false, null);
        ra.addFlashAttribute("flash", "메시지를 전송했습니다.");
    } catch (IllegalArgumentException e) {
        ra.addFlashAttribute("flash", e.getMessage());
    }
    return "redirect:/admin/monitoring#users";
}
```

- [ ] **Step 4: 컴파일 확인**

```bash
./mvnw compile -q
```

`BUILD SUCCESS` 확인.

---

### Task 7: admin.css — 새 클래스 추가

**Files:**
- Modify: `src/main/resources/static/css/admin.css`

- [ ] **Step 1: CSS 파일 끝에 추가**

```css
/* ─── 모니터링 페이지 ───────────────────────────────────── */

/* 클러스터 요약 카드 — admin-stat-card 동일 스타일, 색상 포인트만 다름 */
.monitoring-cluster-card {
    background: #fff;
    border: 1px solid var(--border, #e3e8e3);
    border-radius: 10px;
    padding: 1.1rem 1.3rem;
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    min-width: 110px;
}

.monitoring-cluster-label {
    font-size: 0.78rem;
    font-weight: 600;
    color: var(--muted-foreground, #6b7a6e);
    text-transform: uppercase;
    letter-spacing: 0.04em;
}

.monitoring-cluster-count {
    font-size: 1.7rem;
    font-weight: 700;
    color: var(--foreground, #1c2620);
    line-height: 1.1;
}

.monitoring-cluster-sub {
    font-size: 0.75rem;
    color: var(--muted-foreground, #6b7a6e);
}

/* 탭 */
.monitoring-tabs {
    display: flex;
    gap: 0.25rem;
    border-bottom: 2px solid var(--border, #e3e8e3);
    margin-bottom: 1.25rem;
}

.monitoring-tab-btn {
    padding: 0.55rem 1.1rem;
    font-size: 0.88rem;
    font-weight: 500;
    background: none;
    border: none;
    border-bottom: 2px solid transparent;
    margin-bottom: -2px;
    cursor: pointer;
    color: var(--muted-foreground, #6b7a6e);
    display: flex;
    align-items: center;
    gap: 0.4rem;
    transition: color 0.15s;
}

.monitoring-tab-btn.active {
    color: var(--primary, #6b8e6f);
    border-bottom-color: var(--primary, #6b8e6f);
}

.monitoring-tab-badge {
    background: #e53e3e;
    color: #fff;
    border-radius: 999px;
    font-size: 0.7rem;
    padding: 1px 6px;
    font-weight: 700;
}

/* 미확인 HIGH_RISK 알림 행 강조 */
.admin-alert-unconfirmed {
    background: #fff1f0;
    border-left: 4px solid #e53e3e;
}

.admin-alert-confirmed {
    background: #f9faf9;
    border-left: 4px solid transparent;
    color: var(--muted-foreground, #6b7a6e);
}

/* 사이드바 배지 (미확인 HIGH_RISK 수) */
.admin-nav-badge {
    background: #e53e3e;
    color: #fff;
    border-radius: 999px;
    font-size: 0.7rem;
    padding: 1px 6px;
    margin-left: auto;
    font-weight: 700;
}

/* 고위험군 관리 테이블 인라인 폼 */
.monitoring-inline-form {
    background: #f4f7f4;
    border: 1px solid var(--border, #e3e8e3);
    border-radius: 8px;
    padding: 1rem 1.2rem;
    margin-top: 0.5rem;
    display: none;
}

.monitoring-inline-form.open {
    display: block;
}
```

---

### Task 8: admin-layout.html — 사이드바 모니터링 항목 추가

**Files:**
- Modify: `src/main/resources/templates/fragments/admin-layout.html`

- [ ] **Step 1: 두 개의 sidebar fragment 모두에 모니터링 항목 추가**

파일에는 sidebar fragment가 두 곳에 있다 (`th:fragment="sidebar(active)"` 와 `th:fragment="admin-layout"` 내부). 두 곳 모두 수정한다.

**첫 번째 sidebar (`th:fragment="sidebar(active)"`):**
`<a th:href="@{/admin/cluster}"` 블록 바로 위에 삽입:

```html
<a th:href="@{/admin/monitoring}" class="admin-nav-item"
   th:classappend="${active == 'monitoring'} ? 'active'">
    <span class="admin-nav-ico" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75"
             stroke-linecap="round" stroke-linejoin="round">
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
        </svg>
    </span>
    <span>모니터링</span>
    <span class="admin-nav-badge"
          th:if="${unconfirmedHighRiskCount != null and unconfirmedHighRiskCount > 0}"
          th:text="${unconfirmedHighRiskCount}"></span>
</a>
```

**두 번째 sidebar (`th:fragment="admin-layout"` 내부):**
동일하게 `<a th:href="@{/admin/cluster}"` 블록 바로 위에 삽입. (`th:classappend`는 `currentUri` 기반으로 변경)

```html
<a th:href="@{/admin/monitoring}" class="admin-nav-item"
   th:classappend="${currentUri == '/admin/monitoring'} ? 'active'">
    <span class="admin-nav-ico" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75"
             stroke-linecap="round" stroke-linejoin="round">
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
        </svg>
    </span>
    <span>모니터링</span>
    <span class="admin-nav-badge"
          th:if="${unconfirmedHighRiskCount != null and unconfirmedHighRiskCount > 0}"
          th:text="${unconfirmedHighRiskCount}"></span>
</a>
```

---

### Task 9: admin/monitoring.html — 모니터링 페이지 템플릿

**Files:**
- Create: `src/main/resources/templates/admin/monitoring.html`

- [ ] **Step 1: 파일 생성**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/admin-layout :: head('마음이음 관리자 - 모니터링')}"></head>
<body>
<link rel="stylesheet" th:href="@{/css/admin.css}"/>

<div class="admin-layout">
    <aside th:replace="~{fragments/admin-layout :: sidebar('monitoring')}"></aside>

    <div class="admin-main">
        <div class="admin-page-header">
            <h1>모니터링</h1>
            <p class="muted">클러스터 현황 및 고위험군 관리</p>
        </div>

        <div th:if="${flash}" class="alert alert-info" th:text="${flash}"></div>

        <!-- 클러스터 요약 카드 -->
        <div style="display:flex; flex-wrap:wrap; gap:0.9rem; margin-bottom:2rem;">
            <div th:each="cs : ${clusterSummaries}" class="monitoring-cluster-card">
                <div class="monitoring-cluster-label" th:text="${cs.label}">안정</div>
                <div class="monitoring-cluster-count" th:text="${cs.memberCount + '명'}">0명</div>
                <div class="monitoring-cluster-sub" th:text="'집단 ' + ${cs.clusterId}">집단 0</div>
            </div>
            <div th:if="${#lists.isEmpty(clusterSummaries)}" class="muted" style="font-size:0.9rem;">
                클러스터 데이터가 없습니다. 정서 클러스터 재계산 후 다시 확인하세요.
            </div>
        </div>

        <!-- 탭 버튼 -->
        <div class="monitoring-tabs">
            <button type="button" class="monitoring-tab-btn" id="tab-btn-alerts"
                    onclick="switchTab('alerts')">
                고위험군 알림
                <span class="monitoring-tab-badge"
                      th:if="${unconfirmedCount > 0}"
                      th:text="${unconfirmedCount}"></span>
            </button>
            <button type="button" class="monitoring-tab-btn" id="tab-btn-users"
                    onclick="switchTab('users')">
                고위험군 관리
            </button>
        </div>

        <!-- 탭 1: 고위험군 알림 -->
        <div id="tab-alerts">
            <div th:if="${#lists.isEmpty(highRiskAlerts)}"
                 class="muted" style="padding:1.5rem 0; font-size:0.9rem;">
                고위험군 알림이 없습니다.
            </div>
            <div th:unless="${#lists.isEmpty(highRiskAlerts)}"
                 style="display:flex; flex-direction:column; gap:0.5rem;">
                <div th:each="alert : ${highRiskAlerts}"
                     th:classappend="${alert.adminConfirmed} ? 'admin-alert-confirmed' : 'admin-alert-unconfirmed'"
                     style="border-radius:8px; padding:0.85rem 1rem;">
                    <div style="display:flex; align-items:flex-start; justify-content:space-between; gap:1rem;">
                        <div>
                            <span th:if="${!alert.adminConfirmed}" style="color:#e53e3e; margin-right:0.4rem;">●</span>
                            <strong th:text="${alert.user.name}">홍길동</strong>
                            <span class="muted" style="margin:0 0.3rem;">·</span>
                            <span th:text="${alert.assessmentResult != null ? alert.assessmentResult.typeName : '-'}">우울증 (PHQ-9)</span>
                            <span class="muted" style="margin:0 0.3rem;">·</span>
                            <span th:text="${alert.assessmentResult != null ? alert.assessmentResult.level : '-'}">중증</span>
                            <span class="muted" style="margin:0 0.5rem;">·</span>
                            <span class="muted" style="font-size:0.82rem;"
                                  th:text="${#temporals.format(alert.createdAt, 'yyyy-MM-dd HH:mm')}">2025-05-30</span>
                        </div>
                        <div style="flex-shrink:0;">
                            <form th:if="${!alert.adminConfirmed}"
                                  th:action="@{/admin/monitoring/confirm/{id}(id=${alert.id})}"
                                  method="post" style="display:inline;">
                                <button type="submit" class="btn btn-primary btn-sm">확인</button>
                            </form>
                            <span th:if="${alert.adminConfirmed}"
                                  class="muted" style="font-size:0.82rem;">✓ 확인됨</span>
                        </div>
                    </div>
                    <div style="margin-top:0.35rem; font-size:0.85rem; color:#4a5568;"
                         th:text="${alert.message}">메시지 내용</div>
                </div>
            </div>
        </div>

        <!-- 탭 2: 고위험군 관리 -->
        <div id="tab-users" style="display:none;">
            <div th:if="${#lists.isEmpty(highRiskUsers)}"
                 class="muted" style="padding:1.5rem 0; font-size:0.9rem;">
                고위험군 유저가 없습니다.
            </div>
            <div th:unless="${#lists.isEmpty(highRiskUsers)}">
                <table class="admin-table" style="width:100%;">
                    <thead>
                    <tr>
                        <th>이름</th>
                        <th>이메일</th>
                        <th>액션</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr th:each="u, stat : ${highRiskUsers}" th:id="'user-row-' + ${u.userId}">
                        <td th:text="${u.name}">홍길동</td>
                        <td th:text="${u.email}">hong@example.com</td>
                        <td>
                            <button type="button" class="btn btn-secondary btn-sm"
                                    th:onclick="'toggleMessageForm(' + ${u.userId} + ')'">
                                메시지 보내기
                            </button>
                        </td>
                    </tr>
                    <!-- 인라인 메시지 폼 행 -->
                    <tr th:each="u : ${highRiskUsers}"
                        th:id="'form-row-' + ${u.userId}" style="display:none;">
                        <td colspan="3" style="padding:0;">
                            <div class="monitoring-inline-form open">
                                <form th:action="@{/admin/monitoring/message}" method="post">
                                    <input type="hidden" name="userId" th:value="${u.userId}"/>
                                    <div style="margin-bottom:0.6rem;">
                                        <label style="font-size:0.83rem; font-weight:500;">제목 (선택)</label>
                                        <input type="text" name="title" class="form-control"
                                               style="margin-top:0.3rem;"
                                               placeholder="알림 제목을 입력하세요"/>
                                    </div>
                                    <div style="margin-bottom:0.75rem;">
                                        <label style="font-size:0.83rem; font-weight:500;">내용 <span style="color:#e53e3e;">*</span></label>
                                        <textarea name="message" class="form-control" rows="3"
                                                  style="margin-top:0.3rem;"
                                                  placeholder="메시지 내용을 입력하세요" required></textarea>
                                    </div>
                                    <div style="display:flex; gap:0.5rem; justify-content:flex-end;">
                                        <button type="button" class="btn btn-secondary btn-sm"
                                                th:onclick="'toggleMessageForm(' + ${u.userId} + ')'">취소</button>
                                        <button type="submit" class="btn btn-primary btn-sm">전송</button>
                                    </div>
                                </form>
                            </div>
                        </td>
                    </tr>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
</div>

<script>
    function switchTab(tab) {
        document.getElementById('tab-alerts').style.display = tab === 'alerts' ? '' : 'none';
        document.getElementById('tab-users').style.display  = tab === 'users'  ? '' : 'none';
        document.getElementById('tab-btn-alerts').classList.toggle('active', tab === 'alerts');
        document.getElementById('tab-btn-users').classList.toggle('active',  tab === 'users');
        history.replaceState(null, '', '#' + tab);
    }

    function toggleMessageForm(userId) {
        const row = document.getElementById('form-row-' + userId);
        row.style.display = row.style.display === 'none' ? '' : 'none';
    }

    // 페이지 로드 시 hash로 탭 복원, 기본값은 alerts
    (function () {
        const hash = location.hash === '#users' ? 'users' : 'alerts';
        switchTab(hash);
    })();
</script>

</body>
</html>
```

- [ ] **Step 2: 앱 기동 후 브라우저 확인**

```
http://localhost:8081/admin/monitoring
```

체크리스트:
- [ ] 클러스터 요약 카드가 집단별로 표시되고 레이블(안정/우울/불안/스트레스/복합/고위험)이 보임
- [ ] 사이드바에 "모니터링" 항목이 추가됨
- [ ] 미확인 HIGH_RISK 알림이 있으면 사이드바와 탭에 빨간 배지가 표시됨
- [ ] "고위험군 알림" 탭: 미확인 알림은 빨간 배경 + "확인" 버튼, 확인 클릭 후 일반 스타일로 전환
- [ ] "고위험군 관리" 탭: 테이블 표시, "메시지 보내기" 클릭 시 인라인 폼 펼쳐짐
- [ ] 메시지 전송 후 flash 메시지 "메시지를 전송했습니다." 표시
- [ ] URL hash `#users`로 접근하면 관리 탭이 활성화됨
- [ ] 다른 admin 페이지(대시보드 등)에서도 미확인 배지가 사이드바에 표시됨
