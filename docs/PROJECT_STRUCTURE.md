# Mind-Link 프로젝트 전체 구조

발표·보고서용 요약. 상세 서버 문서는 [backend.md](backend.md), DB는 [sql/README.md](../sql/README.md).

---

## 1. 시스템 개요

**Mind-Link(마음이음)** 은 Spring Boot 기반 **서버 사이드 렌더링(SSR)** 웹 애플리케이션이다.  
브라우저 → **Thymeleaf** 화면 + **REST API** → **Controller** → **Service** → **JPA** → **Oracle**(기본) / **H2**(`local` 프로필) 구조를 따른다.

| 항목 | 내용 |
|------|------|
| 진입점 | `com.mindlink.MindLinkApplication` |
| 기본 패키지 | `com.mindlink` |
| 포트 | 8081 |
| 설정 | `.env` + `application.properties` |
| 인증 | **HttpSession** + **BCrypt** (역할: USER / COUNSELOR / ADMIN) |
| Security 필터 | `permitAll` — 로그인·권한은 **컨트롤러에서 직접** 검사 |

---

## 2. 계층 구조 (Layered Architecture)

```
┌─────────────────────────────────────────────────────────────┐
│  Presentation                                               │
│  templates/*.html  +  static/css, static/js                  │
│  fragments/layout, admin-layout, counselor-layout             │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│  Controller (MVC + REST)                                    │
│  *Controller — 페이지   *ApiController / *RestController — API│
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│  Service — 비즈니스 로직, 트랜잭션                           │
│  + feature 패키지: care, recommendation, chatcluster        │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│  Repository (Spring Data JPA)                               │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│  Oracle (운영·팀)  │  H2 in-memory (local 프로필)              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  External — Naver Open API, OpenAI, Google Gemini             │
│  external/OpenAiClient, recommendation/client, Naver*       │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 프로젝트 루트 폴더 구조 (사진 대신 붙여넣기용)

> `target/`(빌드 결과), `.git/`, `logs/*.gz` 등은 발표·보고서에서 **생략**해도 됩니다.

```
Mind-Link/  (프로젝트 루트 · 통합/)
│
├── pom.xml                          # Maven · Spring Boot 4.0.6
├── mvnw, mvnw.cmd                   # Maven Wrapper
├── README.md
├── .env.example                     # 환경변수 예시 (실제 .env는 Git 제외)
│
├── docs/                            # 프로젝트 문서
│   ├── backend.md, frontend.md, db.md, api.md
│   ├── PROJECT_STRUCTURE.md         # (이 파일) 전체 구조
│   └── HANDOFF_CLUSTER_PERSONALIZATION.md
│
├── sql/                             # Oracle 스크립트 (수동 실행)
│   ├── README.md
│   ├── 00_INSTALL_ALL.sql           # 원샷 설치
│   ├── 01_schema/
│   │   └── ORACLE_SETUP.sql         # DDL + 기본 시드
│   ├── 02_features/
│   │   ├── USERS_PROFILE.sql
│   │   ├── PRIVACY_CONSENT.sql
│   │   ├── ASSESSMENT_SEED.sql      # 자가진단 문항
│   │   ├── MONITORING.sql           # 검사이력·알림
│   │   ├── CARE_REPORT.sql          # 위로 편지
│   │   ├── ACTIVITY_LOG.sql
│   │   └── CHAT_CLUSTERING.sql    # 정서 클러스터
│   ├── 03_optional/
│   │   └── PROVERBS_SEED.sql
│   └── archive/                     # 마이그레이션·복구용
│
├── src/
│   ├── main/
│   │   ├── java/com/mindlink/
│   │   │   ├── MindLinkApplication.java
│   │   │   ├── config/              # Security, AppConfig, Naver, DotEnv, LogMasking
│   │   │   ├── controller/          # MVC·REST (로그인, 커뮤니티, admin 등)
│   │   │   ├── service/             # 공통 비즈니스 로직
│   │   │   ├── domain/              # JPA 엔티티
│   │   │   ├── repository/          # JPA Repository
│   │   │   ├── dto/                 # 요청·응답 DTO
│   │   │   ├── web/                 # 세션·공통 뷰·요청 로그 필터
│   │   │   ├── external/            # OpenAiClient
│   │   │   ├── care/                # AI 위로 편지·PDF
│   │   │   ├── recommendation/      # 도서 추천 (Gemini, Naver)
│   │   │   │   ├── client/
│   │   │   │   ├── domain/
│   │   │   │   ├── dto/
│   │   │   │   ├── repository/
│   │   │   │   ├── service/
│   │   │   │   └── web/
│   │   │   └── chatcluster/         # K-Means·3축 프로필·3D API
│   │   │
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-local.properties   # H2 로컬 프로필
│   │       ├── logback-spring.xml
│   │       ├── static/
│   │       │   ├── css/             # style, admin, activities, cluster-viz
│   │       │   └── js/              # activities, admin-logs, cluster-viz-three
│   │       └── templates/         # Thymeleaf 화면
│   │           ├── fragments/       # layout, admin-layout, counselor-layout
│   │           ├── home.html, login.html, signup.html
│   │           ├── self-assessment/ # list, quiz, result
│   │           ├── community/       # list, detail, new, edit
│   │           ├── counseling/      # list, booking
│   │           ├── recommendations.html
│   │           ├── care-report/     # wizard, list, detail
│   │           ├── alerts.html
│   │           ├── notice.html, notice-detail, notice-form.html
│   │           ├── user/            # me, edit
│   │           ├── activity/        # run, history
│   │           ├── admin/           # dashboard, users, posts, cluster-viz, logs …
│   │           ├── counselor/       # dashboard, high-risk, bookings …
│   │           └── error/           # 400, 403, 404, 500 …
│   │
│   └── test/java/com/mindlink/      # 단위·통합 테스트
│
├── uploads/                         # 커뮤니티 첨부파일 (실행 시 생성)
└── logs/
    └── mindlink.log                 # 서버 로그 (관리자 뷰어 연동)
```

### controller 패키지 (파일만 요약)

```
controller/
├── AuthController.java
├── PageController.java
├── SelfAssessmentController.java
├── CommunityController.java
├── NoticeController.java
├── CounselingController.java
├── CounselingApiController.java
├── AlertController.java
├── UserController.java
├── ActivityController.java
├── ActivityApiController.java
├── BookReviewController.java
├── AdminController.java
├── AdminLogController.java
└── CounselorController.java
```

---

## 4. 기능 모듈 ↔ URL (사용자·관리자)

| 영역 | 주요 URL | 담당 |
|------|----------|------|
| 홈·소개 | `/`, `/info` | `PageController` |
| 로그인·가입 | `/login`, `/signup` | `AuthController` |
| 자가진단 | `/self-assessment/**` | `SelfAssessmentController` |
| 알림 | `/alerts` | `AlertController` |
| 커뮤니티 | `/community/**` | `CommunityController` |
| 공지 | `/notice/**` | `NoticeController` |
| 상담소·예약 | `/counseling`, `/counseling/booking` | `CounselingController` |
| 맞춤 추천 | `/recommendations` | `PageController` |
| AI 위로 편지 | `/care-report/**` | `care/CareReportPageController` |
| 추천 활동 | `/activities/**` | `ActivityController` |
| 내 정보 | `/user/me` | `UserController` |
| 관리자 | `/admin/**` | `AdminController` |
| 상담사 | `/counselor/**` | `CounselorController` |

### REST API (일부)

| API | 용도 |
|-----|------|
| `POST /api/recommendations/ai` | 문장 기반 AI 도서 추천 (Gemini) |
| `GET /api/recommendations?emotion=` | 감정별 DB 도서 |
| `GET /api/counseling/centers` | 네이버 지역검색 상담소 |
| `POST /api/counseling/bookings` | 예약 |
| `POST /api/care-reports` | 위로 편지 생성 (OpenAI) |
| `GET /api/chat-cluster/**` | 클러스터 시각화·프로필 |
| `GET /admin/logs/**` | 로그 파일·SSE 실시간 |

---

## 5. 핵심 데이터 흐름

### 5.1 정서 프로필 · 맞춤 (플랫폼 공통)

```
자가진단 완료 (PHQ-9, GAD-7, PSS, CBI 등)
    → assessment_results 저장
    → UserAssessmentProfile (stress / depression / anxiety norm)
    → clusterId 부여 (K-Means, 페르소나+실사용자 공간)
    → 활용:
         · 커뮤니티: 카테고리·가중 정렬 (CommunityCategoryPreferenceService)
         · 추천 페이지: 기본 감정 탭 (resolveDominantEmotion)
         · (확장) 동일 cluster 인기 글·책 (ClusterContentService 등)
```

### 5.2 도서 추천 (2단계)

| 단계 | 입력 | 처리 |
|------|------|------|
| ① 초기 노출 | 검사 우세 감정 | `GET /api/recommendations?emotion=` → DB `recommendation_books` |
| ② AI 추천 | 사용자 문장 | Gemini 분석 → DB·네이버 후보 → Gemini 선별 |

### 5.3 상담소

```
검색어 → NaverLocalSearchClient (지역검색 + 이미지 검색)
    → CenterResponse (지도: mapx/mapy 좌표 우선)
    → 예약 → bookings 테이블
```

### 5.4 AI 위로 편지

```
위저드 입력 + (선택) 자가진단 스냅샷
    → CareContextAggregator → CareLetterService (OpenAI)
    → care_reports 저장 · PDF 다운로드
```

---

## 6. 외부 연동

| 서비스 | 용도 | 설정 키 |
|--------|------|---------|
| 네이버 지역검색 | 상담소 목록 | `NAVER_API_*` / `NAVER_OPENAPI_*` |
| 네이버 도서검색 | AI 추천 후보 | `NAVER_API_*` |
| 네이버 이미지검색 | 상담소 썸네일 | 동일 |
| Google Gemini | 도서 추천 AI | `GEMINI_API_KEY` |
| OpenAI | 위로 편지 | `OPENAI_API_KEY` |

---

## 7. DB · SQL

- JPA `ddl-auto=none` — 스키마는 **SQL 수동 실행**
- 권장: `sql/00_INSTALL_ALL.sql` (APP_USER)
- 주요 테이블: `users`, `posts`, `post_comments`, `notices`, `bookings`, `assessment_*`, `user_assessment_profiles`, `user_alerts`, `care_reports`, `recommendation_books`, …

---

## 8. 역할별 화면

| 역할 | 주요 메뉴 |
|------|-----------|
| **USER** | 자가진단, 커뮤니티, 추천, 상담소, 알림, 편지, 내 정보 |
| **COUNSELOR** | `/counselor` 대시보드, 고위험·예약·게시글 |
| **ADMIN** | `/admin` 대시보드, 유저·게시글·공지·알림·클러스터 3D·로그·SQL 콘솔 |

---

## 9. 발표 슬라이드용 한 장 요약

**아키텍처:** SSR(MVC) + REST API + Oracle + 3종 외부 AI  

**차별:** 자가진단 → **정서 프로필** → 커뮤니티·추천·알림·편지 **맞춤** + 관리자 **군집 3D**  

**패키지:** `controller` / `service` / `care` / `recommendation` / `chatcluster`

---

## 10. 관련 문서

| 문서 | 내용 |
|------|------|
| [README.md](../README.md) | 실행·기능 요약 |
| [backend.md](backend.md) | 서버 상세 |
| [HANDOFF_CLUSTER_PERSONALIZATION.md](HANDOFF_CLUSTER_PERSONALIZATION.md) | 군집·맞춤 개선 작업 |
| [sql/README.md](../sql/README.md) | Oracle 설치 순서 |
