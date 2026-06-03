# Mind-Link (마음이음)

목포대학교 웹프로그래밍2 · 5조 팀 프로젝트  
정서 케어 플랫폼 — 자가진단, 상담 예약, 커뮤니티, AI 도서·위로 편지, 정서 클러스터링 등을 한 곳에서 제공합니다.

**저장소**: [wonsang03/Mind-Link](https://github.com/wonsang03/Mind-Link)  
**기본 브랜치**: `main` (팀 통합) · 개인 작업: `서상원`, `김동주`, `김지훈`, `윤아연` 등

---

## 기술 스택

| 구분 | 내용 |
|------|------|
| Backend | Java 17, Spring Boot 4, Spring MVC, JPA |
| DB | **Oracle** (운영·팀 개발 기준), H2는 로컬 테스트용 |
| UI | Thymeleaf, 정적 CSS/JS |
| 인증 | HttpSession + BCrypt (`USER` / `COUNSELOR` / `ADMIN`) |
| 로그 | Logback → `logs/mindlink.log`, 관리자 실시간 뷰어(`/admin/logs`) |
| 외부 API | 네이버(지역·도서·이미지), OpenAI(위로 편지), Google Gemini(도서 추천) |

---

## 빠른 시작

### 1. 환경 변수

`.env.example`을 복사해 `.env`를 만들고 값을 채웁니다.

```bash
cp .env.example .env   # Windows: copy .env.example .env
```

| 변수 | 용도 |
|------|------|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Oracle 접속 (`DB_USERNAME`은 보통 `APP_USER`) |
| `NAVER_API_CLIENT_ID`, `NAVER_API_CLIENT_SECRET` | 도서·지역·이미지 검색 |
| `NAVER_OPENAPI_CLIENT_*` | (선택) 지역검색만 별도 키 |
| `GEMINI_API_KEY` | AI 맞춤 **도서 추천** |
| `OPENAI_API_KEY` | AI **위로 편지**·종합 보고서 |

네이버 개발자센터 앱에서 **검색 API** 사용을 켜야 상담소·도서 검색이 동작합니다.

### 2. DB 스크립트 (Oracle)

**APP_USER**(= `.env`의 `DB_USERNAME`)로 접속해 `sql/` 폴더에서 **원샷 스크립트 한 번**이면 아래 필수 항목이 순서대로 적용됩니다. 상세·업그레이드·트러블슈팅: [sql/README.md](sql/README.md)

```sql
SQL> @00_INSTALL_ALL.sql
```

개별 실행 순서:

| 순서 | 파일 | 필수 | 내용 |
|------|------|------|------|
| 1 | [01_schema/ORACLE_SETUP.sql](sql/01_schema/ORACLE_SETUP.sql) | ✅ | 기본 스키마 DDL + 시드(관리자·예약·추천도서 등, 커뮤니티 더미 없음) |
| 2 | [02_features/USERS_PROFILE.sql](sql/02_features/USERS_PROFILE.sql) | ✅ | 프로필 컬럼(닉네임·지역·연락처·알림·프로필 이미지) |
| 3 | [02_features/PRIVACY_CONSENT.sql](sql/02_features/PRIVACY_CONSENT.sql) | ✅ | 민감정보(자가진단) 동의 컬럼 |
| 4 | [02_features/ASSESSMENT_SEED.sql](sql/02_features/ASSESSMENT_SEED.sql) | ✅ | 자가진단 문항·점수 구간 |
| 5 | [02_features/MONITORING.sql](sql/02_features/MONITORING.sql) | ✅ | 검사 이력·알림(9종)·댓글 답글 |
| 6 | [02_features/CARE_REPORT.sql](sql/02_features/CARE_REPORT.sql) | ✅ | AI 위로 편지(`care_reports`) |
| 7 | [02_features/ACTIVITY_LOG.sql](sql/02_features/ACTIVITY_LOG.sql) | ✅ | 추천 활동 수행 기록 |
| 8 | [02_features/CHAT_CLUSTERING.sql](sql/02_features/CHAT_CLUSTERING.sql) | 클러스터 사용 시 | 정서 프로필 + 210 페르소나 시드 |
| (선택) | [03_optional/PROVERBS_SEED.sql](sql/03_optional/PROVERBS_SEED.sql) | 선택 | 홈·커뮤니티·추천 화면 명언 |

> `00_INSTALL_ALL.sql`은 `@@` 상대경로로 위 항목을 호출하므로 반드시 `sql/` 폴더(또는 절대경로) 기준으로 실행하세요. 공지·게시글·데모 사용자 등 더미 데이터는 시드하지 않습니다.

> 알림(`user_alerts`) 컬럼·9종 `alert_type`은 **`MONITORING.sql`에 통합**되어 있습니다. 별도 패치 SQL은 없습니다.

### 3. 서버 실행

```bash
./mvnw spring-boot:run
```

브라우저: **http://localhost:8081** (`server.port=8081`)

---

## 기능 요약

### 일반 사용자 (`USER`)

| 메뉴 | 경로 | 설명 |
|------|------|------|
| 홈 / 소개 | `/`, `/info` | 랜딩·서비스 소개. 이용 가이드는 **로그인 + 첫 방문 시 1회** 자동 노출 |
| 로그인·회원가입 | `/login`, `/signup` | 세션 인증, 가입 시 민감정보 처리 동의 |
| 개인정보 처리방침 | `/privacy` | 약관·동의 안내 |
| 자가진단 | `/self-assessment/**` | PHQ-9, GAD-7, PSS, CBI — 동의 시 결과 저장·알림 연동 |
| 알림 | `/alerts` | 고위험·악화·개선·추천·커뮤니티·공지·관리자 메시지 |
| 상담소 찾기 | `/counseling` | 네이버 지역검색, 지도(좌표 우선) |
| 상담 예약 | `/counseling/booking` | `bookings` 저장, **내 정보**에서 예약 목록·취소 |
| 커뮤니티 | `/community/**` | 글·댓글·답글·좋아요·신고·첨부·YouTube embed. 정렬 **인기순(맞춤)/최신순/좋아요순**, 상단 "나와 비슷한 분들이 많이 본 글" |
| 공지 | `/notice/**` | 목록·상세 (작성은 ADMIN) |
| 내 정보 | `/user/me`, `/user/me/edit` | 프로필·이미지·알림 설정·민감정보 동의 변경 |
| AI 맞춤 추천 | `/recommendations` | **오늘의 문장**(Gemini, 위기 시 지원 안내 우선) + 도서 **맞춤순/인기순(별점)** 정렬·기본 8권, 추천 활동 |
| AI 정서 케어 | `/care-report`, `/ai-care` | 위저드 → OpenAI 위로 편지 + PDF. 기존 편지 있으면 **"내가 받은 편지 보기"** 바로가기 |
| 정서 클러스터 | `/cluster` | 3D 시각화·유사 사용자(프로필·자가진단 연동) |

### 상담사 (`COUNSELOR`)

| 메뉴 | 경로 | 설명 |
|------|------|------|
| 대시보드 | `/counselor` | 상담사 홈 |
| 커뮤니티 관리 | `/counselor/posts` | 게시글 조회 |
| 예약 관리 | `/counselor/bookings` | 예약 확인·상태 변경 |
| 고위험 모니터링 | `/counselor/high-risk` | 고위험 검사 결과 |
| 알림 | `/counselor/alerts` | 발송·조회 |

### 관리자 (`ADMIN`)

| 메뉴 | 경로 | 설명 |
|------|------|------|
| 대시보드 | `/admin` | 통계·요약 |
| 모니터링 | `/admin/monitoring` | 검사 이력·고위험 확인 |
| 정서 클러스터 | `/admin/cluster` | 3D 클러스터·재계산 + **실사용자 정서 유형 5분류 분포**(일반/스트레스/우울/불안/복합) |
| 회원 관리 | `/admin/users` | 목록·상세 |
| 예약 관리 | `/admin/bookings` | 전체 예약 조회 |
| 게시글·공지 | `/admin/posts`, `/admin/notices` | CRUD |
| 알림 발송 | `/admin/alerts` | 전체/개별 `ADMIN_MESSAGE` |
| SQL 콘솔 | `/admin/sql` | 읽기 전용 쿼리(개발·운영 보조) |
| 서버 로그 | `/admin/logs` | `logs/` 파일 tail·SSE·ACCESS 로그 묶기 |

---

## 주요 URL

### 화면 (일부)

| 기능 | Method | URL |
|------|--------|-----|
| 회원가입 / 로그인 | GET·POST | `/signup`, `/login`, `/logout` |
| 내 정보·예약 | GET | `/user/me` |
| 민감정보 동의 | POST | `/user/me/sensitive-consent` |
| 자가진단 | GET/POST | `/self-assessment`, `/self-assessment/{typeKey}`, `…/result` |
| 상담·예약 | GET/POST | `/counseling`, `/counseling/booking` |
| 관리자 | GET/POST | `/admin/**` |
| 상담사 | GET/POST | `/counselor/**` |

### REST API (일부)

| 기능 | Method | URL |
|------|--------|-----|
| AI 도서 추천 | POST | `/api/recommendations/ai` |
| 감정별 도서 | GET | `/api/recommendations?emotion=…` |
| 상담소 검색 | GET | `/api/counseling/centers?query=…` |
| 예약 | POST/GET | `/api/counseling/bookings`, `/bookings/me`, `/bookings/{id}/cancel` |
| 추천 활동 기록 | POST | `/api/activities` |
| 도서 리뷰 | GET/POST | `/api/reviews` |
| 정서 클러스터 | GET/POST | `/api/cluster/**` |
| 관리자 로그 | GET | `/admin/logs/files`, `/recent`, `/stream` (SSE) |

상세 명세: [docs/api.md](docs/api.md) (로컬 문서, 팀 발표·PPT용으로 갱신 중)

---

## 알림 (`/alerts`)

- **모니터링 알림** — 자가진단 기반: 고위험·악화(위험 톤), 개선·맞춤 추천(긍정 톤)
- **일반 알림** — 공지·댓글/답글·관리자 메시지(일반 톤)
- `link_url`이 있으면 관련 화면으로 바로 이동

**관리자 발송** (`/admin/alerts`): 제목(선택)+본문(필수). 「바로가기 링크 넣기」 체크 시에만 `link_url` 저장.

---

## AI · 상담 · 클러스터 (요약)

**도서 추천** — `POST /api/recommendations/ai`  
Gemini(감정·검색어) → DB·네이버 후보 → Gemini 3권 선별. 키 없으면 단계별 fallback.

**위로 편지** — OpenAI 단일 백엔드 (`care` 패키지). PDF 다운로드 지원.

**상담소 지도** — API `mapx`/`mapy` 우선, 없으면 장소명만 검색.

**정서 클러스터** — 자가진단 완료 시 `user_assessment_profiles` 동기화, 관리자 화면에서 K-means 재계산 가능.

**추천 활동** — 호흡·감사 일기 등 완료 시 `POST /api/activities` → `activity_log` (비로그인은 204).

---

## 팀 담당·통합 (참고)

| 담당 | 주요 반영 |
|------|-----------|
| 서상원 | Oracle·AI 도서 추천·종합 보고서·`ORACLE_SETUP`·상담 예약(관리자/상담사/내정보)·관리자 로그 뷰어 |
| 김동주 | 로그인·커뮤니티·공지·첨부·개인정보 동의·추천 활동 |
| 김지훈 | 자가진단·`ASSESSMENT_SEED` |
| 윤아연 | 상담소 검색·예약 API |

구 더미 코드(`MapApiClient`, `Diagnosis*` 등)는 제거되었습니다.

---

## 문서

| 문서 | 내용 |
|------|------|
| [sql/README.md](sql/README.md) | SQL 실행 순서·업그레이드·트러블슈팅 |
| [docs/frontend.md](docs/frontend.md) | 화면·템플릿·CSS |
| [docs/backend.md](docs/backend.md) | 서버·설정 |
| [docs/db.md](docs/db.md) | DB 연동 |
| [docs/api.md](docs/api.md) | REST API 명세 |

---

## 관리자 계정

`role = 'ADMIN'`인 계정으로 로그인하면 `/admin`에서 대시보드·유저 관리·게시글·공지·알림 발송·정서 클러스터(3D)·SQL 콘솔을 사용할 수 있습니다. 초기 관리자 계정은 `01_schema/ORACLE_SETUP.sql` 시드에 포함됩니다(공지·게시글 등 커뮤니티 더미는 시드하지 않음). 등급을 직접 부여하려면:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';
COMMIT;
```

상담사 UI는 `role = 'COUNSELOR'` 입니다.

---

## 로컬 H2 (선택)

Oracle 없이 화면만 볼 때 H2 프로필을 쓸 수 있습니다. 팀 기본은 Oracle이며 `spring.sql.init.mode=never`로 앱 기동 시 SQL 자동 실행은 하지 않습니다.

---

## Git 브랜치 (팀 작업)

```text
main          ← 팀 통합 (배포·데모 기준)
서상원 / 김동주 / 김지훈 / 윤아연  ← 개인 기능 개발 후 main에 merge
```

개인 브랜치에서 작업 → `main` fast-forward 또는 merge → `git push origin main`

---

## 추후 작업

- 게시글 페이지네이션
- 커뮤니티 `posts.author` → `User` FK 정리
- 비밀번호 변경
- `docs/api.md` 원격 동기화(선택)

---

## 프로젝트 구조 (요약)

```text
src/main/java/com/mindlink/
  MindLinkApplication.java
  config/           SecurityConfig, DotEnvLoader, LogMaskingConverter …
  controller/       MVC·REST (admin, counselor, counseling, community …)
  service/          비즈니스 로직
  domain/           JPA 엔티티
  repository/
  care/             AI 위로 편지·PDF
  chatcluster/      정서 3D 클러스터링
  recommendation/   AI 도서 추천
src/main/resources/
  templates/        Thymeleaf (admin/, counselor/, user/ …)
  static/css|js/    style.css, admin.css, admin-logs.js, activities.js …
sql/
  README.md
  00_INSTALL_ALL.sql  원샷 설치 (필수 항목을 @@ 로 순차 호출)
  01_schema/        ORACLE_SETUP.sql
  02_features/      USERS_PROFILE, PRIVACY_CONSENT, ASSESSMENT_SEED,
                    MONITORING, CARE_REPORT, ACTIVITY_LOG, CHAT_CLUSTERING
  03_optional/      PROVERBS_SEED
  archive/          마이그레이션·복구 전용
logs/               mindlink.log (gitignore, 관리자 뷰어 대상)
```
