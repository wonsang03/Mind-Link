# 백엔드

Spring Boot 기반 MVC 서버. Oracle DB(운영) / H2(로컬) 이중 환경을 지원한다.

---

## 목차

1. [기술 스택](#기술-스택)
2. [프로젝트 구조](#프로젝트-구조)
3. [인증 · 세션](#인증--세션)
4. [사용자 역할](#사용자-역할)
5. [주요 기능 및 핵심 로직](#주요-기능-및-핵심-로직)
6. [서비스 레이어](#서비스-레이어)
7. [도메인 모델](#도메인-모델)
8. [파일 업로드](#파일-업로드)
9. [AI 기능](#ai-기능)
10. [알림 · 모니터링](#알림--모니터링)
11. [환경 설정](#환경-설정)

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| 프레임워크 | Spring Boot 3.x |
| 언어 | Java 17+ |
| ORM | Spring Data JPA (Hibernate) |
| DB (운영) | Oracle (`jdbc:oracle:thin:@localhost:1521/FREEPDB1`) |
| DB (로컬) | H2 인메모리 (`--spring.profiles.active=local`) |
| 뷰 | Thymeleaf |
| 보안 | Spring Security (전 경로 permitAll, CSRF 비활성) + 세션 기반 인증 |
| 파일 업로드 | Spring Multipart → 로컬 `uploads/` 디렉토리 |
| AI (편지) | OpenAI gpt-4o-mini |
| AI (추천) | Google Gemini API (`gemini-2.0-flash`) |
| 외부 검색 | 네이버 도서 검색 API, 네이버 지역검색 API |

---

## 프로젝트 구조

### 패키지 트리

```
com.mindlink
├── config/               # 앱 전역 설정
├── controller/           # HTTP 요청 처리 (MVC 페이지 + REST)
├── domain/               # JPA 엔티티
├── dto/                  # 요청·응답 DTO
├── repository/           # Spring Data JPA 인터페이스
├── service/              # 비즈니스 로직
├── web/                  # 뷰 공통 지원 (ControllerAdvice, 세션 상수)
├── external/             # 외부 API 클라이언트 (OpenAI)
├── care/                 # AI 위로 편지 도메인 (독립 서브패키지)
├── recommendation/       # AI 도서 추천 도메인 (독립 서브패키지)
└── chatcluster/          # 정서 3D 클러스터링 도메인 (독립 서브패키지)
```

### 패키지별 역할 설명

**`config/`**
- `SecurityConfig` — Spring Security 전 경로 permitAll, CSRF 비활성, 정적 리소스(/uploads) 허용
- `AppConfig` — `BCryptPasswordEncoder`, `RestClient`(connect 3s / read 5s) 빈 등록, `/uploads/**` 정적 리소스 핸들러 등록(`WebMvcConfigurer`), `@EnableConfigurationProperties(NaverProperties.class)` 활성화
- `DotEnvLoader` — `.env` 파일을 읽어 시스템 프로퍼티에 주입 (API 키 관리)
- `NaverProperties` — 네이버 API 클라이언트 ID/Secret 바인딩

**`controller/`**
- MVC 페이지 컨트롤러(`AuthController`, `CommunityController`, `SelfAssessmentController` 등)와 REST API 컨트롤러(`CounselingApiController`, `BookReviewController`) 혼재
- 접근 제어는 Spring Security가 아닌 컨트롤러에서 세션 확인으로 직접 처리

**`domain/`**
- JPA 엔티티 클래스 전체 위치. `User`, `Post`, `Booking`, `AssessmentResult`, `UserAlert`, `CareReport` 등
- 각 엔티티는 `@Entity` + `@Table` 어노테이션으로 테이블과 매핑

**`dto/`**
- 컨트롤러 ↔ 서비스 간 데이터 전달 객체. 폼 바인딩용(`SignupForm`, `LoginForm`)과 API 요청/응답용(`BookingRequest`, `PostResponse` 등) 구분

**`repository/`**
- Spring Data JPA `JpaRepository` 확장 인터페이스. 커스텀 쿼리는 메서드 이름 기반 쿼리 또는 `@Query` JPQL 사용

**`service/`**
- 핵심 비즈니스 로직 담당. 트랜잭션 경계는 서비스 레이어에서 관리
- `NaverLocalSearchClient`가 서비스 패키지에 위치하나 외부 HTTP 호출 역할을 담당

**`web/`**
- `SessionConst` — 세션 키 상수 (`LOGIN_USER_ID`)
- `CurrentUserAdvice` — `@ControllerAdvice`로 모든 Thymeleaf 뷰에 `loginUser`, `currentUri`, `unreadAlertCount` 자동 주입

**`external/`**
- `OpenAiClient` — OpenAI Chat Completions API 호출 래퍼. 타임아웃 설정 및 HTTP 에러 처리 포함

**`care/`** (AI 위로 편지 독립 서브패키지)
- `CareReportPageController` — MVC 페이지 (위저드, 목록, 상세)
- `CareReportApiController` — REST API (생성, 조회, PDF 다운로드)
- `CareReportService` — 보고서 생성 전체 파이프라인 (입력 검증→정제→채점→AI→출력검증→저장), 일일 한도 관리
- `CareLetterService` — OpenAI gpt-4o-mini 프롬프트 구성·호출, Fallback 편지 반환
- `CareContextAggregator` — 위저드 입력 + 자가진단 결과 → 스냅샷 JSON 구성 (최대 8000자), Risk Level 판정. DB 활동 데이터(게시글·댓글 등) 수집 금지
- `CareSafetyFilter` — 입력 안전 필터(위기 키워드 차단·PII 마스킹·욕설 마스킹) + 출력 안전 필터(의료 진단 완화·유해 출력 차단·핫라인 안내 부착)
- `CareReportPdfBuilder` — 보고서 PDF 생성 (OpenPDF/librepdf 기반, CJK 폰트 `HYSMyeongJo-Medium`으로 한글 출력, 로드 실패 시 Helvetica 폴백). CRISIS 레벨이면 핫라인 안내 박스 포함. 파일명 형식: `mindlink-letter-{yyyyMMdd}-{id}.pdf`
- `CareWebSupport` — 위저드 세션 관련 공통 유틸 (세션 저장/조회 헬퍼)

**`recommendation/`** (AI 도서 추천 독립 서브패키지)
- `RecommendationController` — REST 엔드포인트
- `RecommendationService` — 감정 카테고리 매핑, DB 조회, Gemini 위로 멘트 생성
- `GeminiClient` — Google Gemini API 호출 (감정 탐지, 도서 선별, 위로 멘트)
- `NaverBookApiClient` — 네이버 도서 검색 API. `/ai` 경로(`getAiRecommendations`) 2단계에서 후보 수집 시 사용 (정렬·시작점을 달리해 2회 호출). 프로퍼티 키: `naver.api.client-id` / `naver.api.client-secret`
- `EmotionCategory` — `DEPRESSION / STRESS / ANXIETY / LETHARGY / RELATIONSHIP / NORMAL`

**`chatcluster/`** (정서 3D 클러스터링 독립 서브패키지)
- `ChatClusterApiController` — REST 엔드포인트 (시각화 데이터, 개인 클러스터 조회)
- `ChatClusterVizController` — `/community/cluster-viz` → `/admin/cluster` 리다이렉트
- `ClusterKMeansEngine` — K-Means 알고리즘 구현 (가중 유클리드 거리)
- `ClusterProfileService` — 사용자 프로필 저장·갱신, 개인 클러스터 응답 구성
- `ClusterVisualizationService` — 시각화용 데이터 집계 및 캐싱
- `UserAssessmentProfile` — 클러스터링용 엔티티 (stress/depression/anxiety 정규화 점수)

---

## 인증 · 세션

- 로그인 성공 시 `session.setAttribute(SessionConst.LOGIN_USER_ID, user.getId())`로 `Long` ID 저장
- 각 컨트롤러에서 `session.getAttribute(SessionConst.LOGIN_USER_ID)` → `instanceof Long uid` 패턴으로 인증 확인
- `CurrentUserAdvice` (`@ControllerAdvice`)가 모든 뷰에 `loginUser` (User 엔티티), `currentUri`, `unreadAlertCount`를 자동 주입
- Spring Security는 CSRF 비활성 / 전 경로 permitAll — 접근 제어는 컨트롤러에서 수동 처리

---

## 사용자 역할

| 역할 | 설명 |
|------|------|
| `USER` | 일반 사용자 (기본값) |
| `ADMIN` | 관리자 — 대시보드, 사용자 관리, SQL 콘솔, 클러스터 재계산, 공지 관리 |
| `COUNSELOR` | 상담사 (현재 미사용, 향후 확장용) |

---

## 주요 기능 및 핵심 로직

### 1. 자가진단 (Self-Assessment)

**기능 개요**: PHQ-9(우울), GAD-7(불안), PSS-10(스트레스), CBI(번아웃) 4가지 표준 심리 검사 제공.

**핵심 로직 흐름**:
```
사용자 문항 응답 제출
  → AssessmentService.evaluate()
      - 역채점 문항(reversed=true) 처리
      - 번아웃(CBI): 파트1(개인)/파트2(업무) 분리 계산
      - ScoreRange 테이블에서 점수 구간 조회 → level, highRisk 결정
  → MonitoringService.saveAndMonitor()
      - 제출 전 직전 동일 typeKey 결과 조회 (저장 후 조회 시 현재 결과가 포함되는 문제 방지)
      - AssessmentResult 저장
      - 고위험(highRisk=true) → HIGH_RISK 알림 즉시 생성
      - 이전 결과 있을 경우: 점수 레벨 비교 → DETERIORATION / IMPROVEMENT / IMPROVEMENT_MIN / RECOMMEND 알림 생성
      - 반환된 UserAlert → 결과 화면 배너에 표시
  → ClusterProfileService.mergeAssessmentScore()
      - stress / depression / anxiety 한정 (burnout 제외)
      - 정규화 후 UserAssessmentProfile 갱신 → K-Means 자동 반영
```

**레벨 비교 방식**: `LEVEL_ORDER` 맵으로 문자열 레벨을 숫자 순서로 변환 후 이전/현재 값 비교.

---

### 2. 커뮤니티 게시판

**기능 개요**: 카테고리별 게시글 작성·조회·댓글·좋아요·신고 기능.

**카테고리**: `전체 / 스트레스 / 우울 / 불안 / 인간관계 / 일상·기타`

**핵심 로직 흐름**:
```
게시글 목록 조회
  → CommunityService.findAll(category) — DB에서 카테고리 필터만 수행
  → 키워드 검색(q 파라미터): 컨트롤러에서 결과 목록을 in-memory 필터링
      (title/content에 대소문자 무관 포함 여부)
  → CommunityCategoryPreferenceService.resolvePreferredCategories()
      - UserAssessmentProfile의 norm 점수 조회
      - 최대값 축 기준 1순위 카테고리 선정 (임계값 0.2 이상)
      - 다른 축이 0.3 이상이면 2순위 카테고리 추가
      - 추천 카테고리를 목록 상단에 고정 표시

댓글 작성
  → CommunityService.addComment() — 중첩 답글 지원 (parent_comment_id)
  → UserNotificationService.onPostComment()
      - 답글(parentComment 있음): 부모 댓글 작성자에게 COMMENT_REPLY 알림
        (본인 댓글에 본인 답글 제외)
      - 일반 댓글: 게시글 작성자에게 POST_COMMENT 알림
        (본인 게시글에 본인 댓글 제외)
      - 추가: 같은 게시글에 댓글을 단 다른 사용자 전체에게도 POST_COMMENT 알림
        ("관심 게시글에 새 댓글" 메시지) — 중복 알림 방지를 위해 이미 알림 받은 사용자 제외
```

---

### 3. 상담소 찾기 & 예약

**기능 개요**: 네이버 지역검색 API로 상담소 검색 후 예약 접수.

**핵심 로직 흐름**:
```
상담소 검색
  → CounselingService.searchCenters()
      - isExternalApiConfigured() → 네이버 API 키 설정 여부 확인
      - 키 없으면 더미 데이터 반환 (개발 환경 대응)
      - NaverLocalSearchClient로 지역검색 API 호출 → CenterResponse 목록 반환

예약 생성
  → CounselingService.createBooking()
      - 기본 상태: Status.REQUESTED
      - 예약 취소: Status.CANCELLED로 변경
```

---

### 4. AI 위로 편지 (CareReport)

→ [AI 기능 섹션](#ai-위로-편지-carereport) 참조

---

### 5. AI 도서 추천 (Recommendation)

→ [AI 기능 섹션](#ai-도서-추천-recommendation) 참조

---

### 6. 정서 3D 클러스터링 (ChatCluster)

→ [AI 기능 섹션](#정서-3d-클러스터링-chatcluster) 참조

---

### 7. 관리자 기능 (Admin)

**기능 개요**: 사용자 관리, 게시글 관리, 통계 대시보드, SQL 콘솔, 클러스터 재계산.

**핵심 로직**:
- `AdminService.stats()` — 사용자·게시글·예약 수 집계 (AdminStats DTO 반환)
- `SqlConsoleService` — 관리자 임의 SQL 직접 실행. SELECT/WITH → 결과 행 반환(최대 500행), DML/DDL → 영향받은 행 수 반환 (운영 환경에서 비활성화 권고)
- 클러스터 재계산: `ClusterProfileService.backfillAllFromAssessmentResults()` → 전체 AssessmentResult를 재처리해 모든 UserAssessmentProfile 재생성 → K-Means 전체 재실행

---

### 8. 공지사항 & 알림 발송

**핵심 로직**:
```
공지 작성 (sendPushAlert=true)
  → NoticeService.create()
  → UserNotificationService.notifyNewNotice()
      - ADMIN 역할 제외 전체 사용자에 NOTICE 알림 일괄 생성
      - notification_enabled 여부 미체크 (모든 사용자 수신)
```

---

## 서비스 레이어

| 서비스 | 주요 책임 |
|--------|----------|
| `UserService` | 회원가입(`signup`), 로그인(`login`), 프로필 조회·수정, 역할 변경 |
| `AuthService` | `UserService`로 위임 (현재 직접 호출 없음) |
| `FileStorageService` | 파일/링크 저장(`saveFiles`, `saveLinks`), 삭제(`deleteById`, `deleteByTarget`), 프로필 이미지 교체 |
| `CommunityService` | 게시글·댓글 CRUD, 좋아요, 신고, 관리자 강제 수정·삭제 |
| `AssessmentService` | 검사 유형·문항 조회, 응답 채점(`evaluate`), 역채점·파트 분리 처리 |
| `CounselingService` | 상담소 검색(외부 API 또는 더미), 예약 생성·조회·취소 |
| `NoticeService` | 공지 CRUD |
| `AdminService` | 통계 집계(사용자/역할별/게시글/댓글/신고/예약/공지 카운트), 사용자 삭제(예약·신고·리뷰만 연쇄 삭제 — 게시글·댓글은 author가 String이라 FK 없으므로 그대로 남음), 사용자별 게시글·댓글·예약 조회 |
| `MonitoringService` | 자가진단 결과 저장 + 변화 감지 → UserAlert 생성, 알림 조회·읽음·삭제 |
| `UserNotificationService` | 댓글 알림(`onPostComment`), 공지 알림(`notifyNewNotice`), 관리자 메시지(`sendAdminMessage`) |
| `CommunityCategoryPreferenceService` | 사용자 norm 점수 → 커뮤니티 추천 카테고리 1~2개 추론 |
| `CareReportService` | AI 편지 생성 진입점, 일일 한도 검사(`care-report.daily-limit`) |
| `CareLetterService` | OpenAI gpt-4o-mini 프롬프트 구성·호출, Fallback 편지 반환 |
| `RecommendationService` | 감정 카테고리별 DB 도서 조회, Gemini 위로 멘트 생성, 맞춤 추천(메시지 기반 감정 탐지) |
| `ClusterProfileService` | UserAssessmentProfile 저장·갱신, 개인 클러스터 응답 구성, 전체 백필 |
| `BookReviewService` | 도서 리뷰 CRUD (조회·작성·삭제) |
| `SqlConsoleService` | 관리자 임의 SQL 실행. SELECT/WITH → 결과 행 반환(최대 500행), DML/DDL → 영향받은 행 수 반환. 오류 시 SqlResult.error 반환 |
| `NaverLocalSearchClient` | 네이버 지역검색 API 호출. 쿼리에 상담 키워드 없으면 "심리상담센터" 자동 추가, 결과 중 상담 관련 항목 우선 정렬, 상위 6건 이미지 검색 API로 보강 |

---

## 도메인 모델

| 엔티티 | 테이블 | 비고 |
|--------|--------|------|
| `User` | `users` | `UserRole` ENUM (USER / ADMIN / COUNSELOR) |
| `Post` | `posts` | 커뮤니티 게시글 |
| `PostComment` | `post_comments` | 중첩 답글 지원 (`parent_comment_id`) |
| `Attachment` | `attachments` | IMAGE / VIDEO / LINK, `target_type` + `target_id` 다형 연관 |
| `Report` | `reports` | 게시글·댓글 신고, `TargetType` ENUM |
| `Booking` | `bookings` | 상담 예약, `Status` ENUM (REQUESTED / CONFIRMED / CANCELLED). 주요 컬럼: `center_name`, `center_title`, `center_phone`, `center_address`, `type`(상담 유형), `memo`, `booking_date`, `booking_time` |
| `Notice` | `notices` | 공지사항. 주요 컬럼: `category`, `summary`, `content`, `title` |
| `AssessmentType` | `assessment_types` | 진단 유형 (`type_key` UK). 추가 컬럼: `description`(VARCHAR 300), `duration`(VARCHAR 30) |
| `AssessmentQuestion` | `assessment_questions` | `reversed`(역채점) · `part`(번아웃 파트) |
| `AssessmentChoice` | `assessment_choices` | 선택지 및 점수 |
| `ScoreRange` | `score_ranges` | 점수 구간별 결과 레벨·메시지 |
| `AssessmentResult` | `assessment_results` | 자가진단 제출 결과 이력 |
| `UserAlert` | `user_alerts` | 알림 (9가지 타입, alertType은 String) |
| `CareReport` | `care_reports` | AI 위로 편지 + 스냅샷 JSON |
| `UserAssessmentProfile` | `user_assessment_profiles` | K-Means 클러스터링용 점수 |
| `BookReview` | `book_reviews` | 도서 리뷰. 주요 컬럼: `book_link`(UK with user_id), `book_title`, `rating`(1~5), `content`. upsert 방식(같은 user+book_link면 수정) |
| `RecommendationBook` | `recommendation_books` | 감정별 추천 도서 (DB) |

### User 프로필 확장 컬럼

`ORACLE_SETUP.sql` → `ASSESSMENT_SEED.sql` 순서로 수동 실행 (`ddl-auto=none`).

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `nickname` | VARCHAR2(100) | 선택 |
| `region` | VARCHAR2(100) | 선택 |
| `notification_enabled` | NUMBER(1) DEFAULT 0 | 0/1 체크 제약 |
| `phone` | VARCHAR2(20) | 선택 |
| `profile_image_url` | VARCHAR2(500) | `/uploads/{uuid}.ext` 형태 |

### AssessmentResult 주요 컬럼

| DB 컬럼 | 타입 | 비고 |
|---------|------|------|
| `user_id` | Long (FK) | 필수 |
| `type_key` | VARCHAR(50) | depression / anxiety / stress / burnout |
| `type_name` | VARCHAR(100) | 한글 명칭 (예: 우울증 (PHQ-9)) — `@PrePersist`로 자동 설정 |
| `score` | Integer | 전체 점수 (burnout은 null) |
| `score_level` | VARCHAR(30) | 레벨 문자열 (예: 중등도) |
| `result_level` | VARCHAR(30) | `score_level`과 동일값 — 구 테이블 호환용 중복 컬럼 |
| `is_high_risk` | boolean | 고위험 여부 (NOT NULL) |
| `personal_score` | Integer | 번아웃 파트1(개인) 점수 |
| `personal_level` | VARCHAR(30) | 번아웃 파트1 레벨 |
| `work_score` | Integer | 번아웃 파트2(업무) 점수 |
| `work_level` | VARCHAR(30) | 번아웃 파트2 레벨 |
| `created_at` | LocalDateTime | 제출 시각 (Java 필드명: `completedAt`) |

### UserAlert 알림 타입

`alertType`은 DB 컬럼 `VARCHAR(30)`에 문자열로 저장된다.

| alertType | 설명 | 발생 위치 |
|-----------|------|----------|
| `HIGH_RISK` | 자가진단 고위험 감지 | `MonitoringService` |
| `DETERIORATION` | 이전 결과 대비 악화 | `MonitoringService` |
| `IMPROVEMENT` | 이전 결과 대비 개선 (최저 수준 미도달) | `MonitoringService` |
| `IMPROVEMENT_MIN` | 현재 결과가 최저(최소) 수준 — 개선 후 최저 도달 또는 최저 상태 유지 | `MonitoringService` |
| `RECOMMEND` | 중간 상태 유지 — 콘텐츠 추천 | `MonitoringService` |
| `POST_COMMENT` | 내 게시글에 새 댓글 달림 | `UserNotificationService` |
| `COMMENT_REPLY` | 내 댓글에 답글 달림 | `UserNotificationService` |
| `NOTICE` | 새 공지사항 등록 | `UserNotificationService` |
| `ADMIN_MESSAGE` | 관리자 직접 발송 메시지 | `AdminController` |

### UserAlert 추가 컬럼

기본 컬럼(`alertType`, `message`, `isRead`, `createdAt`) 외 추가 필드:

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `title` | VARCHAR(200) | 알림 제목 (선택, 관리자 메시지에서 사용) |
| `link_url` | VARCHAR(500) | 바로가기 링크 (예: `/community/1#comment-2`) |
| `related_post_id` | Long | 연관 게시글 ID (댓글 알림) |
| `related_comment_id` | Long | 연관 댓글 ID (댓글·답글 알림) |
| `notice_id` | Long | 연관 공지 ID (NOTICE 알림) |
| `assessment_result_id` | Long (FK) | 연관 자가진단 결과 (모니터링 알림) |

---

### CareReport 주요 컬럼

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `user_id` | Long | 필수 |
| `snapshot_json` | CLOB | 익명화된 위저드 입력 + 자가진단 결과 |
| `letter_body` | CLOB | AI 생성 편지 전문 |
| `risk_level` | RiskLevel ENUM | NORMAL / ELEVATED / CRISIS |
| `themes` | String | 쉼표 구분 감정 라벨 (예: `우울,불안`) |
| `created_at` | LocalDateTime | 자동 생성 |

### UserAssessmentProfile 주요 컬럼

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `id` | Long (PK, Auto) | — |
| `user_id` | Long (nullable) | FK → users. NULL 이면 합성 페르소나 |
| `persona_key` | VARCHAR(40) NOT NULL | 실사용자: `real_user_{userId}`, 페르소나: 고유 식별자 |
| `persona_label` | VARCHAR(80) NOT NULL | 3D 시각화 표시명 (실사용자명 또는 페르소나 유형) |
| `persona_story` | VARCHAR(500) | 페르소나 배경 스토리 (실사용자: 고정 문구) |
| `stress_score` | Double NOT NULL | PSS-10 원점수 (0–40) |
| `depression_score` | Double NOT NULL | PHQ-9 원점수 (0–27) |
| `anxiety_score` | Double NOT NULL | GAD-7 원점수 (0–21) |
| `stress_norm` | Double NOT NULL | [0,1] 정규화 값 (score / 40) |
| `depression_norm` | Double NOT NULL | [0,1] 정규화 값 (score / 27) |
| `anxiety_norm` | Double NOT NULL | [0,1] 정규화 값 (score / 21) |
| `cluster_id` | Integer (nullable) | K-Means 배정 클러스터 번호 |
| `is_synthetic` | NUMBER NOT NULL DEFAULT 1 | 0: 실사용자, 1: 합성 페르소나 |
| `created_at` / `updated_at` | LocalDateTime | `@PrePersist`/`@PreUpdate` 자동 설정 |

---

## 파일 업로드

- 저장 경로: 프로젝트 루트 `uploads/` (`app.upload.dir=uploads`)
- URL: `/uploads/{uuid}.ext` → Spring 정적 리소스로 서빙

| 종류 | 허용 확장자 | 최대 크기 |
|------|------------|----------|
| 이미지 (게시글·댓글) | jpg, jpeg, png, gif, webp | 10 MB |
| 동영상 (게시글·댓글) | mp4, webm | 100 MB |
| 프로필 사진 | jpg, jpeg, png, gif, webp | 5 MB |
| 링크 | `http://` 또는 `https://` URL | — |

---

## AI 기능

### AI 위로 편지 (CareReport)

**위저드 흐름** (총 10단계):

| 단계 | 입력 필드 | 설명 |
|------|----------|------|
| 1 | `mood` | 현재 기분·정서 (텍스트) |
| 2 | stress 자가진단 | PSS-10 (10문항) |
| 3 | depression 자가진단 | PHQ-9 (9문항) |
| 4 | anxiety 자가진단 | GAD-7 (7문항) |
| 5 | `recentHardship` | 최근 어려운 일 (텍스트) |
| 6 | `concern` | 요즘 걱정되는 것 (텍스트) |
| 7 | `smallComfort` | 작은 위로가 되는 것 (텍스트) |
| 8 | `hopeForward` | 앞으로 바라는 점 (텍스트) |
| 9 | `oneLineMessage` | 하루 한마디 (텍스트) |
| 10 | — | 요약 확인 → 보고서 생성 요청 |

**생성 파이프라인** (`CareReportService.generate`):
```
① 입력 검증
    - 필수: mood, recentHardship, concern, smallComfort, hopeForward
    - oneLineMessage 는 선택

② 일일 한도 검사
    - 최근 24시간 내 care_reports 수 ≥ dailyLimit(기본 3) → RateLimitedException

③ 입력 정제 (CareSafetyFilter.sanitizeUserInput)
    - 필드별 최대 길이 제한: mood 300, hardship/concern 700, comfort/hope 500, message 400
    - 위기 표현 감지 → 원문을 "[위험 표현 포함 — 본문 생략, 상담 안내 우선]"으로 교체
    - PII 마스킹 (전화번호·주민번호·이메일·주소)
    - 욕설 마스킹

④ 자가진단 채점 (stress / depression / anxiety 한정, burnout 제외)
    - 완료된 검사만 채점 → AssessmentScore 목록

⑤ 정보 종합 (CareContextAggregator.collect)
    - 프로필(닉네임·joinedAt) + 위저드 입력 + 자가진단 결과 → 스냅샷 JSON (최대 8000자)
    - Risk Level 판정

⑥ LLM 호출 (CareLetterService → OpenAI gpt-4o-mini)

⑦ 출력 안전 필터 (CareSafetyFilter.reviewGeneratedLetter)
    - 유해 표현(자해 방법·자살 권유) → reject → Fallback
    - 의료 확정 진단 표현 → 완화 표현으로 교체
    - 약물 처방 권유 → "전문가와 상담하기"로 교체
    - CRISIS 사용자에게 핫라인 안내(1393·1577-0199) 누락 시 본문 앞에 자동 부착

⑧ CareReport 저장 (letter_body + snapshot_json + risk_level + themes)
```

**제한**: 사용자당 24시간 내 `care-report.daily-limit`회 (기본 3회)

**Risk Level 판정** (`CareContextAggregator.detectRiskLevel`):
- `CRISIS`: 텍스트 입력에서 CRISIS_KEYWORDS 감지 (자살, 자해, 죽고 싶 등 18개)
- `ELEVATED`: CRISIS_KEYWORDS 미감지 + (ELEVATED_KEYWORDS 감지 ※ 또는 자가진단 결과 중 `highRisk=true`)
  - ELEVATED_KEYWORDS: 우울, 절망, 허무, 외로, 공황, 무기력, 불안, 지쳐, 힘들 등
- `NORMAL`: 위 조건 모두 해당 없는 경우

※ ELEVATED_KEYWORDS는 문자 포함 여부로만 판단하므로 일상 문장에서도 감지될 수 있음

---

### AI 도서 추천 (Recommendation)

**추천 흐름 — 엔드포인트별 완전히 다른 로직**:

`GET ?emotion=...` (`getRecommendations`):
```
parseEmotion(emotionStr)
  → DB 조회 (findByEmotion)
  → DB에 도서 있으면: Gemini 위로 멘트 생성 → 결과 반환 (source: "DB")
  → DB에 도서 없으면: 위로 멘트만 반환 (source: "EMPTY") ← 네이버 API 호출 없음
```

`POST /personalize` (`getPersonalizedRecommendations`) — **DB만 사용, 네이버 API 호출 없음**:
```
① 키워드 기반 복수 감정 탐지 (최대 2개)
② 슬롯 결정: Gemini 또는 fallback → 감정별 권수 비율 결정 (합=3)
③ 슬롯별 DB 후보 수집 + relevance 점수 계산
④ 슬롯 쿼터 적용 3패스 선택 (쿼터 → 보충 → 전체 풀)
⑤ Gemini 위로 멘트 생성
```

`POST /ai` (`getAiRecommendations`) — **3단계 파이프라인, 네이버 API 사용**:
```
① Gemini: 사용자 메시지 → 감정·검색어·요약 판단
② 서버: DB 후보 + 네이버 도서 검색 API로 후보 수집
   - 네이버 검색: 정렬·시작점·보조 검색어를 바꿔 2회 호출로 다양화
   - 자해·위기 메시지 감지 시 원문을 검색어에 섞지 않고 안전 키워드만 사용
   - 추천 확정 도서는 비동기(asyncCache)로 DB에 저장·갱신
③ Gemini: 후보 목록만 보고 3권 + 이유 확정
   - relevanceScore 필터로 부적합 권(수능/만화/잡학 등) 배제
```

**중복 제거**: 세션(`ml_ai_recent_isbns`)에 최대 40개 ISBN 저장, 마지막 35개를 중복 제거에 활용

---

### 정서 3D 클러스터링 (ChatCluster)

**알고리즘**: `KMeansPlusPlusClusterer` (commons-math3), 기본 K=6 (`chat.cluster.k`)

**3개 축**: stress (PSS-10) / depression (PHQ-9) / anxiety (GAD-7) — 각 [0,1] 정규화 후 사용

**가중치 적용 방식**: 좌표에 `√w`를 곱해 단순 유클리드 거리로 가중 유클리드 효과 구현
- stress × √1.0, depression × √1.2, anxiety × √1.0

**재시작 + Silhouette 최적화**:
- `chat.cluster.kmeans.restarts`(기본 10)번 재시작
- 각 실행마다 Silhouette 계수(-1~+1) 계산 → 최고 점수 결과 채택

**cluster_id 안정화**: centroid 좌표의 (s+d+a) 합 오름차순으로 정렬해 재시작마다 클러스터 인덱스가 뒤섞이는 문제 방지

**시각화**: Three.js 기반 3D 산점도 — 실사용자 + 익명 페르소나 동시 표시

**자동 업데이트 및 실시간 클러스터 배정**:
- 자가진단 제출 시 `ClusterProfileService.mergeAssessmentScore()` → 해당 축 점수 갱신 + `nearestClusterId()` 즉시 호출
- `nearestClusterId()`: 전체 재계산 없이 현재 DB 프로필들에서 centroid를 직접 계산해 가장 가까운 클러스터 근사치 배정
- 관리자 `/api/chat-cluster/recompute` 시에만 `KMeansPlusPlusClusterer`로 전체 재계산

---

## 알림 · 모니터링

### 자동 발생 알림

| 트리거 | 알림 타입 |
|--------|-----------|
| 자가진단 고위험 기준 초과 | `HIGH_RISK` |
| 이전 결과 대비 점수 악화 | `DETERIORATION` |
| 이전 결과 대비 개선 (최저 수준 미도달) | `IMPROVEMENT` |
| 최저(최소) 수준 도달 또는 유지 | `IMPROVEMENT_MIN` |
| 중간 상태 유지 | `RECOMMEND` |
| 내 게시글에 새 댓글 | `POST_COMMENT` |
| 내 댓글에 답글 | `COMMENT_REPLY` |
| 새 공지사항 등록 | `NOTICE` |
| 관리자 직접 발송 | `ADMIN_MESSAGE` |

### MonitoringService 동작

1. `SelfAssessmentController`에서 결과 제출 시 `saveAndMonitor()` 호출
2. 저장 **전** 직전 동일 `typeKey` 결과 조회 (저장 후 조회 시 현재 결과가 반환되는 문제 방지)
3. `AssessmentResult` 저장 후 이전 결과와 레벨 순서 비교 (`LEVEL_ORDER` 맵 활용)
4. 변화량·고위험 여부에 따라 `UserAlert` 생성 및 저장
5. 생성된 `UserAlert`를 반환 → 결과 화면 배너에 표시

모든 알림은 `notification_enabled` 값과 무관하게 `user_alerts` 테이블에 저장된다. `notifyNewNotice()`도 `notification_enabled`를 체크하지 않으며 `ADMIN` 역할 제외 전체 사용자에게 발송된다.

---

## 환경 설정

### 운영 (Oracle)
```properties
spring.jpa.hibernate.ddl-auto=none        # 스키마 자동 변경 없음
spring.sql.init.mode=never                # data.sql 미사용
```
DB 초기화 순서: `sql/ORACLE_SETUP.sql` → `sql/ASSESSMENT_SEED.sql`

### 로컬 (`--spring.profiles.active=local`)
```properties
spring.datasource.url=jdbc:h2:mem:mindlink
spring.jpa.hibernate.ddl-auto=create-drop  # User.java 기반 자동 생성
spring.h2.console.path=/h2-console
```

### 주요 application.properties

```properties
server.port=8081
app.upload.dir=uploads

# 파일 업로드 한도
spring.servlet.multipart.max-file-size=100MB
spring.servlet.multipart.max-request-size=110MB

# AI 위로 편지 한도
care-report.daily-limit=3

# OpenAI (위로 편지)
openai.api.key=${OPENAI_API_KEY:OPENAI_KEY_NOT_SET}
openai.model=gpt-4o-mini
openai.timeout.seconds=120

# Google Gemini (도서 추천)
gemini.api.key=${GEMINI_API_KEY:NOT_SET}

# 3D 클러스터링
chat.cluster.k=6
chat.cluster.weight.stress=1.0
chat.cluster.weight.depression=1.2
chat.cluster.weight.anxiety=1.0
chat.cluster.kmeans.max-iterations=500
chat.cluster.kmeans.restarts=10
chat.cluster.kmeans.tolerance=0.0001
chat.cluster.viz.cache-seconds=30
chat.cluster.viz.max-points=500
chat.cluster.recompute.batch-size=200
chat.cluster.seed-on-startup=false
chat.cluster.seed.persona-types=30
chat.cluster.seed.variants-per-persona=7
```

### 외부 API 키 (`.env` 파일)

| 키 | 용도 |
|----|------|
| `NAVER_API_CLIENT_ID` / `NAVER_API_CLIENT_SECRET` | 네이버 도서 검색 |
| `NAVER_OPENAPI_CLIENT_ID` / `NAVER_OPENAPI_CLIENT_SECRET` | 네이버 지역검색 (상담소 찾기) |
| `GEMINI_API_KEY` | Google Gemini (AI 도서 추천·감정 분석) |
| `OPENAI_API_KEY` | OpenAI gpt-4o-mini (AI 위로 편지) |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Oracle 접속 정보 |
