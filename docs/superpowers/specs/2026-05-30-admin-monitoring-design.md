# 관리자 모니터링 페이지 설계

**날짜**: 2026-05-30  
**경로**: `/admin/monitoring`  
**대상**: ADMIN 역할 전용

---

## 1. 목표

관리자 페이지에 모니터링 전용 페이지를 추가한다.

- 상단 요약 카드: 클러스터 맵 기반 집단별 인원 수 표시
- 탭 1 (고위험군 알림): 고위험 검사 결과 발생 시 알림, 건별 확인 전까지 강조 표시 영구 유지
- 탭 2 (고위험군 관리): 고위험 유저 테이블, 인라인 메시지 전송

---

## 2. DB 변경

### 파일: `sql/02_features/MONITORING_ADMIN_CONFIRMED.sql`

```sql
ALTER TABLE user_alerts ADD admin_confirmed NUMBER(1) DEFAULT 0 NOT NULL;
COMMENT ON COLUMN user_alerts.admin_confirmed IS '관리자 확인 여부 (1=확인, 0=미확인). HIGH_RISK 알림 전용.';
```

- 기존 레코드는 모두 `0(미확인)`으로 초기화됨
- `alert_type = 'HIGH_RISK'` 인 레코드에만 의미 있게 사용됨

---

## 3. 백엔드

### 3-1. `UserAlert` 도메인

`adminConfirmed` 필드 추가:

```java
@Column(name = "admin_confirmed", nullable = false)
private boolean adminConfirmed;

public boolean isAdminConfirmed() { return adminConfirmed; }
public void setAdminConfirmed(boolean adminConfirmed) { this.adminConfirmed = adminConfirmed; }
```

### 3-2. `UserAlertRepository`

메서드 2개 추가:

```java
List<UserAlert> findByAlertTypeOrderByCreatedAtDesc(String alertType);
long countByAlertTypeAndAdminConfirmedFalse(String alertType);
```

### 3-3. `MonitoringService`

메서드 3개 추가:

```java
// 전체 HIGH_RISK 알림 목록 (관리자용)
public List<UserAlert> getHighRiskAlertsForAdmin();

// 건별 확인 처리
@Transactional
public void confirmHighRiskAlert(Long alertId);

// 미확인 HIGH_RISK 건수 (사이드바 배지용)
public long countUnconfirmedHighRisk();
```

### 3-4. `AdminMonitoringService` (신규)

모니터링 전용 집계 서비스.

```java
@Service
public class AdminMonitoringService {

    // ClusterVisualizationService에서 centroids를 가져와
    // 각 clusterId별 memberCount + 레이블 도출
    public List<ClusterSummary> getClusterSummaries();

    // AssessmentResult에서 is_high_risk=true인 최신 결과 기준 유저 목록
    // 유저당 최신 고위험 결과 1건만 포함
    public List<HighRiskUserInfo> getHighRiskUsers();
}
```

**`ClusterSummary` record**:
```java
record ClusterSummary(int clusterId, String label, long memberCount)
```

> `memberCount`는 `user_assessment_profiles`에서 `user_id IS NOT NULL`인 실사용자만 집계.  
> `Centroid.memberCount`는 합성 페르소나 포함이라 사용하지 않음.

**집단 레이블 도출 로직** (중심 정규화 좌표 기준):

| 조건 | 레이블 |
|---|---|
| S, D, A 모두 < 0.35 | 안정 |
| S ≥ 0.5, D < 0.4, A < 0.4 | 스트레스 |
| D ≥ 0.5, S < 0.4, A < 0.4 | 우울 |
| A ≥ 0.5, S < 0.4, D < 0.4 | 불안 |
| S ≥ 0.6, D ≥ 0.6, A ≥ 0.6 | 고위험 |
| 나머지 (둘 이상 우세) | 복합 |

**`HighRiskUserInfo` record**:
```java
record HighRiskUserInfo(Long userId, String name, String email,
                        String typeKey, String typeName, String level,
                        LocalDateTime completedAt)
```

- `AssessmentResultRepository`에 신규 쿼리 추가:
  ```java
  // 유저별 최신 고위험 결과 (JPQL: GROUP BY user, 최신 1건)
  List<AssessmentResult> findLatestHighRiskPerUser();
  ```

### 3-5. `AdminController`

엔드포인트 3개 추가:

| Method | URL | 동작 |
|---|---|---|
| GET | `/admin/monitoring` | 모니터링 페이지 렌더링 |
| POST | `/admin/monitoring/confirm/{alertId}` | 알림 건별 확인, redirect back |
| POST | `/admin/monitoring/message` | 고위험 유저에게 메시지 전송 (기존 `notificationService.sendAdminMessage` 재사용) |

`GET /admin/monitoring` 모델 속성:

- `clusterSummaries` — `List<ClusterSummary>`
- `highRiskAlerts` — `List<UserAlert>` (HIGH_RISK, 최신순)
- `unconfirmedCount` — `long` (사이드바 배지, 페이지 탭 배지 공용)
- `highRiskUsers` — `List<HighRiskUserInfo>`

---

## 4. 프론트엔드

### 4-1. `admin/monitoring.html`

```
[페이지 헤더] 모니터링

[요약 카드 행 — clusterId별 카드, K개]
┌──────────┐  ┌──────────┐  ┌──────────┐  ...
│  안정     │  │  우울     │  │  복합    │
│  41명     │  │  23명     │  │  17명    │
└──────────┘  └──────────┘  └──────────┘

[탭 버튼]
[ 고위험군 알림  🔴3 ]  [ 고위험군 관리 ]

── 탭 1: 고위험군 알림 ──────────────────────────────
미확인 알림: 빨간 배경(admin-alert-unconfirmed 클래스) + 좌측 붉은 border
확인된 알림: 일반 스타일(연한 회색)

각 행:
┌──────────────────────────────────────────────────────────┐
│ 🔴 홍길동 · 우울증(PHQ-9) · 중증 · 2025-05-30  [확인]  │ ← 미확인
│    "우울증 검사 결과가 마음에 걸려요..."                  │
├──────────────────────────────────────────────────────────┤
│    이순신 · 불안장애 · 중증 · 2025-05-28  ✓ 확인됨      │ ← 확인 완료
└──────────────────────────────────────────────────────────┘

── 탭 2: 고위험군 관리 ──────────────────────────────
┌──────────┬─────────────────────────┬──────────────────┐
│  이름     │  이메일                 │  액션            │
├──────────┼─────────────────────────┼──────────────────┤
│  홍길동   │  hong@example.com      │  [메시지 보내기]  │
├──────────┴─────────────────────────┴──────────────────┤
│  ▼ 인라인 폼 (메시지 보내기 클릭 시 펼쳐짐)            │
│  제목(선택): [___________________________]             │
│  내용(필수): [___________________________]             │
│                                    [취소]  [전송]      │
└────────────────────────────────────────────────────────┘
```

**탭 전환**:
- 순수 JS (jQuery 미사용)
- URL hash(`#alerts`, `#users`)로 새로고침 후 탭 위치 유지

**인라인 폼 전송**:
- `POST /admin/monitoring/message` — `userId`, `title`(optional), `message` 파라미터
- 기존 `UserNotificationService.sendAdminMessage` 내부 재사용

### 4-2. `admin-layout.html` 사이드바 수정

"모니터링" 항목 추가 (대시보드와 정서 클러스터 사이):

```html
<a th:href="@{/admin/monitoring}" class="admin-nav-item"
   th:classappend="${active == 'monitoring'} ? 'active'">
  <!-- 심장 박동 아이콘 -->
  <span>모니터링</span>
  <!-- 미확인 HIGH_RISK 건수가 있으면 배지 표시 -->
  <span class="admin-nav-badge" th:if="${unconfirmedHighRiskCount > 0}"
        th:text="${unconfirmedHighRiskCount}"></span>
</a>
```

> 사이드바 배지(`unconfirmedHighRiskCount`)는 `AdminController`에 `@ModelAttribute` 메서드로 공통화.  
> 모든 GET 핸들러에 자동 주입되므로 개별 핸들러 수정 불필요.

### 4-3. CSS (`admin.css`)

추가할 클래스:

```css
/* 미확인 HIGH_RISK 알림 행 강조 */
.admin-alert-unconfirmed {
    background: #fff1f0;
    border-left: 4px solid #e53e3e;
}

/* 사이드바 배지 */
.admin-nav-badge {
    background: #e53e3e;
    color: #fff;
    border-radius: 999px;
    font-size: 0.72rem;
    padding: 1px 6px;
    margin-left: auto;
}

/* 모니터링 요약 카드 */
.monitoring-cluster-card { ... }  /* 기존 admin-stat-card 스타일 유사 */
```

---

## 5. 변경 파일 목록

| 파일 | 변경 유형 |
|---|---|
| `sql/02_features/MONITORING_ADMIN_CONFIRMED.sql` | 신규 |
| `domain/UserAlert.java` | 필드 추가 |
| `repository/UserAlertRepository.java` | 메서드 추가 |
| `repository/AssessmentResultRepository.java` | 메서드 추가 |
| `service/MonitoringService.java` | 메서드 추가 |
| `service/AdminMonitoringService.java` | 신규 |
| `controller/AdminController.java` | 엔드포인트 추가, @ModelAttribute 공통화 |
| `templates/admin/monitoring.html` | 신규 |
| `templates/fragments/admin-layout.html` | 사이드바 항목 추가, 배지 변수 |
| `static/css/admin.css` | 클래스 추가 |

---

## 6. 범위 외

- 클러스터 레이블 커스터마이징 UI (관리자가 직접 수정) — 향후 작업
- 고위험군 유저 추이 차트 — 향후 작업
- 실시간 알림 (WebSocket) — 향후 작업
