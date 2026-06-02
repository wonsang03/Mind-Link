# 마음이음 백엔드 문서

> **Spring Boot 3 기반 MVC 서버** — Oracle DB(운영) / H2(로컬) 이중 환경  
> 최신화: 2026-06-02

---

## 목차

1. [기술 스택](#기술-스택)
2. [아키텍처 개요](#아키텍처-개요)
3. [프로젝트 구조](#프로젝트-구조)
4. [인증 · 세션](#인증--세션)
5. [사용자 역할](#사용자-역할)
6. [핵심 기능](#핵심-기능)
   - [자가진단](#1-자가진단-self-assessment)
   - [커뮤니티 게시판](#2-커뮤니티-게시판)
   - [상담소 찾기 & 예약](#3-상담소-찾기--예약)
   - [AI 위로 편지](#4-ai-위로-편지-carereport)
   - [AI 도서 추천](#5-ai-도서-추천-recommendation)
   - [정서 3D 클러스터링](#6-정서-3d-클러스터링-chatcluster)
   - [추천 활동 일지](#7-추천-활동-일지-activitylog)
   - [관리자 기능](#8-관리자-기능)
7. [알림 · 모니터링](#알림--모니터링)
8. [개인정보 보호](#개인정보-보호)
9. [파일 업로드](#파일-업로드)
10. [도메인 모델](#도메인-모델)
11. [서비스 레이어 전체 목록](#서비스-레이어-전체-목록)
12. [환경 설정](#환경-설정)

---

## 기술 스택

| 구분 | 기술 | 버전 / 비고 |
|------|------|------------|
| **프레임워크** | Spring Boot | 3.x |
| **언어** | Java | 17+ |
| **ORM** | Spring Data JPA (Hibernate) | Oracle Dialect |
| **DB (운영)** | Oracle | `jdbc:oracle:thin:@localhost:1521/FREEPDB1` |
| **DB (로컬)** | H2 인메모리 | `--spring.profiles.active=local` |
| **뷰 엔진** | Thymeleaf | 서버사이드 렌더링 |
| **보안** | Spring Security | 전 경로 permitAll · CSRF 비활성 · 세션 기반 인증 |
| **AI — 위로 편지** | OpenAI | `gpt-4o-mini` |
| **AI — 도서 추천·감정 분석** | Google Gemini | `gemini-2.0-flash` |
| **AI — 클러스터링** | Apache Commons Math | K-Means++ |
| **외부 검색** | 네이버 OpenAPI | 도서 검색 · 지역검색 |
| **파일 업로드** | Spring Multipart | 로컬 `uploads/` 디렉토리 |
| **실시간 로그** | Server-Sent Events (SSE) | 관리자 로그 뷰어 |

---

## 아키텍처 개요

```
브라우저 (Thymeleaf SPA)
        │  HTTP/SSE
        ▼
┌─────────────────────────────────────────────────────┐
│               Spring Boot 애플리케이션               │
│                                                     │
│  Controller (MVC + REST)                            │
│       │ ControllerAdvice (loginUser / currentUri)   │
│       ▼                                             │
│  Service Layer  ────────────► External APIs         │
│  (비즈니스 로직)               - OpenAI (편지)       │
│       │                       - Gemini (추천·감정)  │
│       ▼                       - 네이버 (검색)       │
│  Repository (JPA)                                   │
│       │                                             │
│       ▼                                             │
│  Oracle DB  ◄── ddl-auto=none (수동 SQL 마이그레이션) │
└─────────────────────────────────────────────────────┘
```

### 요청 흐름

```
HTTP 요청
  → SecurityFilter (permitAll, CSRF off)
  → CurrentUserAdvice (@ModelAttribute)
      - loginUser, currentUri, unreadAlertCount → 전역 주입
  → Controller (세션 직접 확인 → 미인증 시 리다이렉트)
  → Service (@Transactional)
  → Repository (JPA)
  → Thymeleaf 렌더링 → HTML 응답
```

---

## 프로젝트 구조

```
com.mindlink
├── config/             설정 (Security, App, DotEnv, NaverProperties)
├── controller/         HTTP 컨트롤러 (MVC 페이지 + REST API 혼재)
├── domain/             JPA 엔티티
├── dto/                요청·응답 DTO, 폼 바인딩 객체
├── repository/         Spring Data JPA 인터페이스
├── service/            핵심 비즈니스 로직 (트랜잭션 경계)
├── web/                ControllerAdvice, 세션 상수
├── external/           OpenAI API 클라이언트
├── care/               AI 위로 편지 서브패키지
├── recommendation/     AI 도서 추천 서브패키지
└── chatcluster/        정서 3D 클러스터링 서브패키지
```

### config/ 패키지

| 클래스 | 역할 |
|--------|------|
| `SecurityConfig` | Spring Security — 전 경로 permitAll, CSRF 비활성, `/uploads/**` 허용 |
| `AppConfig` | BCryptPasswordEncoder · RestClient(3s/5s 타임아웃) 빈, `/uploads/**` 정적 핸들러 등록 |
| `DotEnvLoader` | `.env` 파일 → 시스템 프로퍼티 주입 (API 키 관리) |
| `NaverProperties` | 네이버 API Client-ID / Secret 바인딩 |

### care/ 서브패키지

| 클래스 | 역할 |
|--------|------|
| `CareReportPageController` | 위저드·목록·상세 MVC 페이지 |
| `CareReportApiController` | 생성·조회·PDF 다운로드 REST |
| `CareReportService` | 생성 파이프라인 총괄, 일일 한도 관리 |
| `CareLetterService` | GPT-4o-mini 프롬프트 구성·호출, Fallback 편지 |
| `CareContextAggregator` | 위저드 입력 + 자가진단 → 스냅샷 JSON (최대 8000자), Risk Level 판정 |
| `CareSafetyFilter` | 입력 안전 필터(위기 키워드·PII 마스킹) + 출력 안전 필터(의료 표현 완화·핫라인 부착) |
| `CareReportPdfBuilder` | OpenPDF 기반 PDF 생성, CJK 폰트(HYSMyeongJo), CRISIS 핫라인 박스 포함 |

### recommendation/ 서브패키지

| 클래스 | 역할 |
|--------|------|
| `RecommendationController` | REST 엔드포인트 3종 |
| `RecommendationService` | 감정 카테고리 매핑, DB 조회, Gemini 위로 멘트 |
| `GeminiClient` | Google Gemini API 호출 (감정 탐지·도서 선별·위로 멘트) |
| `NaverBookApiClient` | 네이버 도서 검색 API (정렬·시작점을 달리해 2회 호출) |
| `EmotionCategory` | `DEPRESSION / STRESS / ANXIETY / LETHARGY / RELATIONSHIP / NORMAL` |

### chatcluster/ 서브패키지

| 클래스 | 역할 |
|--------|------|
| `ChatClusterApiController` | REST (시각화 데이터·개인 클러스터 조회·전체 재계산) |
| `ClusterKMeansEngine` | K-Means++ 알고리즘 (가중 유클리드 거리) |
| `ClusterProfileService` | UserAssessmentProfile 저장·갱신, 실시간 클러스터 배정, 전체 백필 |
| `ClusterVisualizationService` | 시각화 데이터 집계 및 캐싱 (30초) |
| `UserAssessmentProfile` | 클러스터링용 엔티티 (stress/depression/anxiety 정규화 점수) |

---

## 인증 · 세션

```java
// 로그인 성공
session.setAttribute(SessionConst.LOGIN_USER_ID, user.getId()); // Long ID 저장

// 각 컨트롤러에서 인증 확인 패턴
Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
if (!(userId instanceof Long uid)) {
    ra.addFlashAttribute("flash", "로그인이 필요합니다.");
    return "redirect:/login";
}
```

- **`CurrentUserAdvice`** (`@ControllerAdvice`) — 모든 Thymeleaf 뷰에 자동 주입

| 모델 속성 | 내용 |
|----------|------|
| `loginUser` | `User` 엔티티 (미로그인 시 `null`) |
| `currentUri` | 현재 요청 URI (헤더 active 처리) |
| `unreadAlertCount` | 읽지 않은 알림 수 |

- **Spring Security**: CSRF 비활성 / 전 경로 permitAll — 접근 제어는 **컨트롤러에서 수동 처리**

---

## 사용자 역할

| 역할 | 설명 | 주요 권한 |
|------|------|----------|
| `USER` | 일반 사용자 (가입 기본값) | 자가진단·커뮤니티·예약·AI 기능 전체 |
| `COUNSELOR` | 상담사 | 상담사 패널 — 미배정 예약 수락, 담당 예약 상태 변경, 고위험 사용자 알림 수신 |
| `ADMIN` | 관리자 | 사용자·게시글·공지 관리, SQL 콘솔, 클러스터 재계산, 실시간 서버 로그 뷰어 |

---

## 핵심 기능

### 1. 자가진단 (Self-Assessment)

> PHQ-9(우울) · GAD-7(불안) · PSS-10(스트레스) · CBI(번아웃) 4종 표준 심리 검사

**처리 흐름**

```
사용자 문항 응답 제출
  → AssessmentService.evaluate()
      - 역채점 문항(reversed=true) 처리
      - CBI: 파트1(개인) / 파트2(업무) 분리 계산
      - ScoreRange 테이블 → level · highRisk 결정

  → MonitoringService.saveAndMonitor()
      - 저장 전 직전 동일 typeKey 결과 조회 (현재 결과 오염 방지)
      - AssessmentResult 저장
      - highRisk=true → HIGH_RISK 알림 즉시 생성
      - 이전 결과 있을 경우: LEVEL_ORDER 맵으로 비교
          → DETERIORATION / IMPROVEMENT / IMPROVEMENT_MIN / RECOMMEND 알림

  → ClusterProfileService.mergeAssessmentScore()
      - stress / depression / anxiety 축 점수 정규화·갱신
      - nearestClusterId()로 K-Means 클러스터 실시간 배정
```

---

### 2. 커뮤니티 게시판

> 카테고리별 게시글 · 댓글 · 좋아요 · 신고 · 감정 기반 카테고리 추천

**카테고리**: 전체 / 스트레스 / 우울 / 불안 / 인간관계 / 일상·기타

**핵심 로직**

```
게시글 목록 조회
  → CommunityService.findAll(category)
  → 키워드 검색(q): 컨트롤러에서 in-memory 필터 (title/content 대소문자 무관)
  → CommunityCategoryPreferenceService.resolvePreferredCategories()
      - UserAssessmentProfile norm 점수 → 최대값 축 기준 1~2순위 카테고리 추론
      - 추천 카테고리를 목록 상단 고정 표시

댓글 알림 흐름 (UserNotificationService.onPostComment)
  - 답글(parentComment 있음) → 부모 댓글 작성자에게 COMMENT_REPLY
  - 일반 댓글 → 게시글 작성자에게 POST_COMMENT
  - 같은 게시글에 댓글 단 다른 사용자 전체에게도 POST_COMMENT (중복 제외)
  ※ 본인 → 본인 알림 모두 제외
```

---

### 3. 상담소 찾기 & 예약

> 네이버 지역검색 API → 상담소 목록 · 예약 접수 · 상담사 배정

**검색 흐름**

```
CounselingService.searchCenters()
  - API 키 미설정 시 개발용 더미 데이터 반환
  - NaverLocalSearchClient
      - 쿼리에 "심리상담센터" 없으면 자동 추가
      - 상담 관련 항목 우선 정렬 (결과 우선순위 재배열)
      - 상위 6건 이미지 검색 API로 보강
```

**예약 상태 관리**

```
Status: REQUESTED → CONFIRMED → CANCELLED
         (신청)      (확정)       (취소)

상담사 배정 흐름
  - 미배정 예약(counselor=null) → 상담사 수락 → counselor_id 설정
  - 상담사는 본인 담당 예약의 상태만 변경 가능
  - 상담사 배정 해제 → 미배정 풀로 복귀
```

---

### 4. AI 위로 편지 (CareReport)

> 10단계 위저드 → GPT-4o-mini → 안전 필터 → 편지 저장 / PDF 다운로드

**위저드 단계**

| 단계 | 입력 | 설명 |
|------|------|------|
| 1 | `mood` | 현재 기분·정서 |
| 2–4 | PSS-10 / PHQ-9 / GAD-7 | 자가진단 3종 |
| 5 | `recentHardship` | 최근 어려운 일 |
| 6 | `concern` | 요즘 걱정거리 |
| 7 | `smallComfort` | 작은 위로가 되는 것 |
| 8 | `hopeForward` | 앞으로 바라는 점 |
| 9 | `oneLineMessage` | 하루 한마디 (선택) |
| 10 | — | 요약 확인 → 생성 요청 |

**생성 파이프라인**

```
① 필수 필드 검증 (mood, recentHardship, concern, smallComfort, hopeForward)

② 일일 한도 검사
   - 최근 24시간 내 care_reports 수 ≥ dailyLimit (기본 3) → RateLimitedException

③ 입력 정제 (CareSafetyFilter.sanitizeUserInput)
   - 필드별 최대 길이: mood 300자 / hardship·concern 700자 / comfort·hope 500자
   - 위기 표현 감지 → 원문을 "[위험 표현 포함 — 상담 안내 우선]"으로 교체
   - PII 마스킹 (전화번호·주민번호·이메일·주소)
   - 욕설 마스킹

④ 자가진단 채점 (stress / depression / anxiety, burnout 제외)

⑤ 스냅샷 JSON 생성 + Risk Level 판정 (CareContextAggregator)
   - CRISIS  : 위기 키워드 18개 감지 (자살·자해·죽고 싶 등)
   - ELEVATED: ELEVATED_KEYWORDS 감지 또는 자가진단 highRisk=true
   - NORMAL  : 위 조건 모두 해당 없음

⑥ GPT-4o-mini 호출 (CareLetterService)

⑦ 출력 안전 필터 (CareSafetyFilter.reviewGeneratedLetter)
   - 유해 표현(자해 방법·자살 권유) → Fallback 편지 반환
   - 의료 확정 진단 표현 → 완화 표현으로 교체
   - CRISIS 편지에 핫라인(1393·1577-0199) 누락 시 자동 부착

⑧ CareReport 저장 (letter_body + snapshot_json + risk_level + themes)
```

---

### 5. AI 도서 추천 (Recommendation)

> 감정 상태 → DB 도서 조회 + Gemini AI → 맞춤 도서 3권 + 위로 멘트

**엔드포인트별 로직**

| 엔드포인트 | 동작 | 외부 API |
|-----------|------|---------|
| `GET ?emotion=` | DB 도서 조회 → Gemini 위로 멘트 | Gemini |
| `POST /personalize` | 복수 감정 탐지 → DB 후보 → 쿼터 배분 3권 | Gemini |
| `POST /ai` | 3단계 파이프라인 | Gemini + 네이버 |

**`POST /ai` 3단계 파이프라인**

```
① Gemini: 사용자 메시지 → 감정·검색어·요약 판단

② 서버: DB 후보 + 네이버 도서 검색 (정렬·시작점·보조 검색어 바꿔 2회 호출)
   - 자해·위기 메시지 → 원문을 검색어에 섞지 않고 안전 키워드만 사용
   - 확정 도서 → 비동기로 DB 저장·갱신 (asyncCache)

③ Gemini: 후보 목록에서 3권 + 이유 확정
   - relevanceScore 필터로 수능·만화·잡학 등 부적합 도서 배제
```

**중복 제거**: 세션(`ml_ai_recent_isbns`)에 최대 40 ISBN 보관, 마지막 35개 활용

---

### 6. 정서 3D 클러스터링 (ChatCluster)

> 자가진단 점수(stress·depression·anxiety) 3개 축으로 K-Means 클러스터링 → Three.js 3D 시각화

**알고리즘 상세**

| 항목 | 내용 |
|------|------|
| 알고리즘 | K-Means++ (`commons-math3`) |
| 기본 K | 6 (`chat.cluster.k`) |
| 축 · 정규화 | PSS-10 /40 · PHQ-9 /27 · GAD-7 /21 → [0,1] |
| 가중치 | stress×√1.0 · **depression×√1.2** · anxiety×√1.0 (좌표에 √w 곱해 가중 유클리드 구현) |
| 재시작 | 10회 (`restarts`) — 매 실행마다 Silhouette 계수 계산 → 최고 점수 채택 |
| 안정화 | centroid (s+d+a) 합 오름차순 정렬 → 재시작마다 cluster_id 일관 유지 |
| 시각화 | Three.js 3D 산점도 — 실사용자 + 익명 페르소나 동시 표시 |

**실시간 업데이트**

```
자가진단 제출 시
  → ClusterProfileService.mergeAssessmentScore()
      - 해당 축 점수 갱신
      - nearestClusterId(): 전체 재계산 없이 현재 DB 프로필 centroid 직접 계산
                            → 가장 가까운 클러스터 즉시 배정

전체 재계산 (관리자 /api/chat-cluster/recompute)
  → KMeansPlusPlusClusterer로 전체 재실행 + Silhouette 최적화
```

---

### 7. 추천 활동 일지 (ActivityLog)

> 호흡·감사 일기·감정 체크인 등 추천 활동 수행 기록 — 활동 일지·연속일·여정 기반

**활동 종류**

| activityKey | 설명 |
|-------------|------|
| `breathing` | 호흡 훈련 |
| `gratitude` | 감사 일기 (항목 리스트 저장) |
| `checkin` | 감정 체크인 (emoji + mood, moodScore 1~5) |
| `grounding` | 그라운딩 |
| `thought_dump` | 생각 쏟아내기 |
| `small_action` | 작은 실천 |

**엔드포인트**

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/activities/{key}` | 활동 실행 페이지 |
| `GET` | `/activities/history` | 활동 일지 (로그인 필요) |

**저장 구조** (`activity_log` 테이블)

```
activityKey   활동 식별자 (40자)
payload       활동별 입력·결과 JSON (CLOB)
moodScore     감정 체크인 정서가 1~5 (추이 그래프용, 그 외 null)
durationSec   소요 시간(초, 선택)
programKey    여정(프로그램) 키 (선택)
createdAt     수행 시각
```

---

### 8. 관리자 기능

> 사용자 관리 · 게시글 관리 · 통계 대시보드 · SQL 콘솔 · 클러스터 재계산 · **실시간 서버 로그 뷰어**

**주요 기능**

| 기능 | 설명 |
|------|------|
| 사용자 관리 | 목록·상세 조회, 역할 변경, 탈퇴 처리 (게시글·댓글 author는 String FK 없어 보존) |
| 게시글 관리 | 강제 수정·삭제, 신고 처리 |
| 통계 대시보드 | 사용자(역할별)·게시글·댓글·신고·예약·공지 카운트 (`AdminService.stats()`) |
| SQL 콘솔 | 관리자 임의 SQL 실행. SELECT/WITH → 최대 500행, DML/DDL → 영향 행 수 *(운영 비활성화 권고)* |
| 클러스터 재계산 | 전체 AssessmentResult 재처리 → UserAssessmentProfile 재생성 → K-Means 전체 실행 |

**실시간 서버 로그 뷰어** (`AdminLogController`)

```
GET /admin/logs/files   — 로그 파일 목록 (현재 로그 + 롤링 .gz)
GET /admin/logs/recent  — 선택 파일 최근 N 라인 (기본 300줄)
GET /admin/logs/stream  — SSE 실시간 스트림
    - 800ms 폴링으로 신규 라인 감지 → 접속 중인 관리자 전체에 broadcast
    - 25초 간격 keep-alive ping
    - 파일 롤링(size 감소) 감지 시 position 초기화
```

---

## 알림 · 모니터링

### 알림 타입 (UserAlert.alertType)

| alertType | 설명 | 발생 위치 |
|-----------|------|----------|
| `HIGH_RISK` | 자가진단 고위험 기준 초과 | MonitoringService |
| `DETERIORATION` | 이전 대비 악화 | MonitoringService |
| `IMPROVEMENT` | 이전 대비 개선 (최저 수준 미도달) | MonitoringService |
| `IMPROVEMENT_MIN` | 최저 수준 도달·유지 | MonitoringService |
| `RECOMMEND` | 중간 상태 유지 → 콘텐츠 추천 | MonitoringService |
| `POST_COMMENT` | 내 게시글에 새 댓글 | UserNotificationService |
| `COMMENT_REPLY` | 내 댓글에 답글 | UserNotificationService |
| `NOTICE` | 새 공지사항 등록 (ADMIN 제외 전체 발송) | UserNotificationService |
| `ADMIN_MESSAGE` | 관리자 직접 발송 | AdminController |

### MonitoringService 동작 순서

```
1. SelfAssessmentController → saveAndMonitor() 호출
2. 저장 전 직전 동일 typeKey 결과 조회 (현재 결과 포함 오염 방지)
3. AssessmentResult 저장
4. LEVEL_ORDER 맵(문자열 레벨 → 숫자 순서)으로 이전/현재 비교
5. 변화량·고위험 여부 → UserAlert 생성·저장
6. 생성된 UserAlert 반환 → 결과 화면 배너 표시
```

> `notification_enabled` 값과 무관하게 `user_alerts` 테이블에 저장됨

---

## 개인정보 보호

개인정보보호법 제23조 준수 — 자가진단 결과는 **민감정보**로 분류, 별도 동의 후 저장

**동의 흐름**

```
회원가입 시
  - 이용약관 동의 (필수)
  - 개인정보 처리방침 동의 (필수)
  - 자가진단 결과 저장 동의 (선택) → sensitive_data_consent = true/false

자가진단 결과 저장
  - sensitiveDataConsent = true인 사용자만 AssessmentResult 저장
  - false인 경우 채점·표시는 하되 DB에 저장하지 않음

동의 철회 (내 정보 > 개인정보 설정)
  - sensitive_data_consent = false로 갱신
  - 기존 저장된 자가진단 결과 및 알림 삭제
  - 탈퇴 시 이름·이메일·전화번호 즉시 익명화
```

**로그 마스킹** (LogMaskingConverter)

- 접속 로그: IP 마지막 옥텟 마스킹 (`x.x.x.XXX`)
- 이메일·전화번호: 패턴 감지 후 마스킹 처리

---

## 파일 업로드

- 저장 경로: `uploads/` (`app.upload.dir=uploads`)
- URL: `/uploads/{uuid}.ext` → Spring 정적 리소스 서빙

| 종류 | 허용 확장자 | 최대 크기 |
|------|------------|----------|
| 게시글·댓글 이미지 | jpg, jpeg, png, gif, webp | 10 MB |
| 게시글·댓글 동영상 | mp4, webm | 100 MB |
| 프로필 사진 | jpg, jpeg, png, gif, webp | 5 MB |
| 링크 | `http://` · `https://` | — |

---

## 도메인 모델

### 전체 엔티티 목록

| 엔티티 | 테이블 | 주요 특징 |
|--------|--------|----------|
| `User` | `users` | `UserRole` ENUM · 프로필 확장 컬럼 · `sensitive_data_consent` |
| `Post` | `posts` | 커뮤니티 게시글 · category String · likes 카운트 |
| `PostComment` | `post_comments` | 중첩 답글 (`parent_comment_id`) |
| `Attachment` | `attachments` | IMAGE / VIDEO / LINK · `target_type` + `target_id` 다형 연관 |
| `Report` | `reports` | POST / COMMENT 신고 |
| `Booking` | `bookings` | `Status` ENUM · `counselor_id` FK (상담사 배정) |
| `Notice` | `notices` | category · summary · content |
| `AssessmentType` | `assessment_types` | `type_key` UK · description · duration |
| `AssessmentQuestion` | `assessment_questions` | `reversed`(역채점) · `part`(번아웃 파트) |
| `AssessmentChoice` | `assessment_choices` | 선택지 및 점수 |
| `ScoreRange` | `score_ranges` | 점수 구간별 레벨·메시지 |
| `AssessmentResult` | `assessment_results` | 자가진단 제출 이력 |
| `UserAlert` | `user_alerts` | 9가지 alertType · link_url · related FK들 |
| `CareReport` | `care_reports` | snapshot_json(CLOB) · letter_body(CLOB) · RiskLevel ENUM |
| `UserAssessmentProfile` | `user_assessment_profiles` | 클러스터링용 norm 점수 · cluster_id · is_synthetic |
| `ActivityLog` | `activity_log` | 추천 활동 수행 기록 · payload(CLOB) · moodScore · programKey |
| `BookReview` | `book_reviews` | rating 1~5 · upsert (user_id + book_link UK) |
| `RecommendationBook` | `recommendation_books` | EmotionCategory ENUM · isbn UK |

### User 프로필 컬럼 (USERS_PROFILE.sql 마이그레이션)

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `nickname` | VARCHAR2(100) | 선택 |
| `region` | VARCHAR2(100) | 선택 |
| `notification_enabled` | NUMBER(1) DEFAULT 0 | 0/1 CHECK 제약 |
| `phone` | VARCHAR2(20) | 선택 |
| `profile_image_url` | VARCHAR2(500) | `/uploads/{uuid}.ext` |
| `sensitive_data_consent` | NUMBER(1) DEFAULT 0 | 개인정보보호법 제23조 — 민감정보 별도 동의 |

### AssessmentResult 주요 컬럼

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `type_key` | VARCHAR(50) | `depression` / `anxiety` / `stress` / `burnout` |
| `score` | Integer | 전체 점수 (burnout은 null) |
| `score_level` | VARCHAR(30) | 예: 중등도 |
| `is_high_risk` | boolean | 고위험 여부 |
| `personal_score` / `personal_level` | Integer / VARCHAR | 번아웃 파트1(개인) |
| `work_score` / `work_level` | Integer / VARCHAR | 번아웃 파트2(업무) |
| `completed_at` | LocalDateTime | `@PrePersist` 자동 |

### CareReport 주요 컬럼

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `snapshot_json` | CLOB | 익명화된 위저드 입력 + 자가진단 결과 |
| `letter_body` | CLOB | AI 생성 편지 전문 |
| `risk_level` | ENUM | NORMAL / ELEVATED / CRISIS |
| `themes` | String | 쉼표 구분 감정 라벨 (예: `우울,불안`) |

### UserAssessmentProfile 주요 컬럼

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `persona_key` | VARCHAR(40) NOT NULL | 실사용자: `real_user_{id}` |
| `stress_norm` / `depression_norm` / `anxiety_norm` | Double | [0,1] 정규화 값 |
| `cluster_id` | Integer | K-Means 배정 번호 |
| `is_synthetic` | NUMBER | 0: 실사용자 · 1: 합성 페르소나 |

---

## 서비스 레이어 전체 목록

| 서비스 | 주요 책임 |
|--------|----------|
| `UserService` | 회원가입·로그인·프로필 조회·수정·역할 변경·탈퇴·민감정보 동의 토글 |
| `FileStorageService` | 파일·링크 저장·삭제, 프로필 이미지 교체 |
| `CommunityService` | 게시글·댓글 CRUD · 좋아요 · 신고 · 관리자 강제 수정·삭제 |
| `AssessmentService` | 검사 유형·문항 조회, 응답 채점, 역채점·파트 분리 |
| `CounselingService` | 상담소 검색(외부 API / 더미), 예약 생성·조회·취소, 상담사 배정·해제 |
| `NoticeService` | 공지 CRUD |
| `AdminService` | 통계 집계, 사용자 삭제 (예약·신고·리뷰 연쇄 삭제), 사용자별 게시글·예약 조회 |
| `MonitoringService` | 자가진단 저장 + 변화 감지 → UserAlert 생성, 알림 조회·읽음·삭제 |
| `UserNotificationService` | 댓글·공지·관리자 메시지 알림 생성 |
| `CommunityCategoryPreferenceService` | norm 점수 → 커뮤니티 추천 카테고리 1~2개 추론 |
| `CareReportService` | AI 편지 생성 총괄, 일일 한도 검사 |
| `CareLetterService` | GPT-4o-mini 프롬프트·호출·Fallback |
| `RecommendationService` | 감정 카테고리별 DB 도서 조회, Gemini 위로 멘트 |
| `ClusterProfileService` | UserAssessmentProfile 저장·갱신, 실시간 배정, 전체 백필 |
| `ClusterVisualizationService` | 시각화 데이터 집계·캐싱 |
| `ActivityService` | 추천 활동 수행 기록 저장·조회 (`recent`, `countTotal`, `countThisWeek`) |
| `BookReviewService` | 도서 리뷰 CRUD (upsert 방식) |
| `SqlConsoleService` | 관리자 임의 SQL 실행 (SELECT → 최대 500행 / DML → 영향 행 수) |
| `LogViewerService` | 로그 파일 목록 조회·tail, 안전한 경로 검증 |
| `NaverLocalSearchClient` | 네이버 지역검색 API · 쿼리 정규화 · 결과 정렬 · 이미지 보강 |

---

## 환경 설정

### DB 설정

| 환경 | 설정 |
|------|------|
| **운영 (Oracle)** | `ddl-auto=none` · 수동 SQL 마이그레이션 |
| **로컬 (H2)** | `--spring.profiles.active=local` · `ddl-auto=create-drop` · `/h2-console` |

**마이그레이션 실행 순서** (Oracle)

```
sql/01_schema/ORACLE_SETUP.sql         기본 테이블 + 샘플 시드
sql/02_features/USERS_PROFILE.sql      프로필 확장 컬럼
sql/02_features/PRIVACY_CONSENT.sql    민감정보 동의 컬럼
sql/02_features/ASSESSMENT_SEED.sql    자가진단 문항·선택지 데이터
sql/02_features/MONITORING.sql         모니터링·알림 테이블
sql/02_features/ACTIVITY_LOG.sql       활동 일지 테이블
sql/02_features/CARE_REPORT.sql        AI 편지 테이블
sql/02_features/CHAT_CLUSTERING.sql    클러스터링 테이블
```

### 주요 application.properties

```properties
server.port=8081
app.upload.dir=uploads

# AI — 위로 편지
openai.api.key=${OPENAI_API_KEY}
openai.model=gpt-4o-mini
openai.timeout.seconds=120
care-report.daily-limit=3          # 사용자당 24시간 생성 한도

# AI — 도서 추천
gemini.api.key=${GEMINI_API_KEY}

# 3D 클러스터링
chat.cluster.k=6
chat.cluster.weight.stress=1.0
chat.cluster.weight.depression=1.2
chat.cluster.weight.anxiety=1.0
chat.cluster.kmeans.max-iterations=500
chat.cluster.kmeans.restarts=10
chat.cluster.viz.cache-seconds=30
chat.cluster.viz.max-points=500

# 로그 롤링
logging.file.name=logs/mindlink.log
logging.logback.rollingpolicy.max-file-size=10MB
logging.logback.rollingpolicy.max-history=7
```

### 외부 API 키 (`.env`)

| 키 | 용도 |
|----|------|
| `NAVER_API_CLIENT_ID` / `NAVER_API_CLIENT_SECRET` | 네이버 도서 검색 |
| `NAVER_OPENAPI_CLIENT_ID` / `NAVER_OPENAPI_CLIENT_SECRET` | 네이버 지역검색 (상담소 찾기) |
| `GEMINI_API_KEY` | Google Gemini (도서 추천·감정 분석) |
| `OPENAI_API_KEY` | OpenAI gpt-4o-mini (AI 위로 편지) |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Oracle 접속 정보 |
