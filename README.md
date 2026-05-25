# Mind-Link (마음이음)

목포대학교 웹프로그래밍2 · 5조 팀 프로젝트  
정서 케어 플랫폼 — 자가진단, 상담소 찾기, 커뮤니티, AI 도서 추천 등을 한 곳에서 제공합니다.

**저장소**: [wonsang03/Mind-Link](https://github.com/wonsang03/Mind-Link)

---

## 기술 스택

| 구분 | 내용 |
|------|------|
| Backend | Java 17, Spring Boot 4, Spring MVC, JPA |
| DB | **Oracle** (운영·팀 개발 기준), H2는 로컬 테스트용 |
| UI | Thymeleaf, 정적 CSS |
| 인증 | HttpSession + BCrypt (Spring Security 필터 미사용) |
| 외부 API | 네이버 검색(지역·도서·이미지), OpenAI(종합 보고서), Google Gemini(도서 추천) |

---

## 빠른 시작

### 1. 환경 변수

`.env.example`을 복사해 `.env`를 만들고 값을 채웁니다.

```bash
cp .env.example .env   # Windows: copy .env.example .env
```

| 변수 | 용도 |
|------|------|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Oracle 접속 |
| `NAVER_API_CLIENT_ID`, `NAVER_API_CLIENT_SECRET` | 도서·지역·이미지 검색 |
| `NAVER_OPENAPI_CLIENT_*` | (선택) 지역검색만 별도 키 |
| `GEMINI_API_KEY` | AI 맞춤 **도서 추천** |
| `OPENAI_API_KEY` | AI 종합 **보고서(위로 편지)** |

네이버 개발자센터 앱에서 **검색 API** 사용을 켜야 상담소·도서 검색이 동작합니다.

### 2. DB 스크립트 (Oracle)

순서대로 SQL Developer 등에서 실행합니다. 전체 순서·기존 DB 업그레이드·트러블슈팅은 [sql/README.md](sql/README.md) 참고.

1. [01_schema/ORACLE_SETUP.sql](sql/01_schema/ORACLE_SETUP.sql) — 기본 스키마·시드(관리자·공지·커뮤니티·추천도서·예약 등)  
2. [02_features/USERS_PROFILE.sql](sql/02_features/USERS_PROFILE.sql) — 내 정보 컬럼(nickname·region·phone·알림 수신·프로필 이미지)
3. [02_features/ASSESSMENT_SEED.sql](sql/02_features/ASSESSMENT_SEED.sql) — 자가진단 문항·점수 구간
4. [02_features/MONITORING.sql](sql/02_features/MONITORING.sql) — 검사 이력·알림(`assessment_results`, `user_alerts`)·댓글 답글
5. [02_features/CARE_REPORT.sql](sql/02_features/CARE_REPORT.sql) — AI 위로 편지(`care_reports`)
6. [02_features/CHAT_CLUSTERING.sql](sql/02_features/CHAT_CLUSTERING.sql) — 정서 클러스터(`user_assessment_profiles` + 210 페르소나), 클러스터 기능 사용 시
7. [03_optional/CHAT_CLUSTER_REAL_USER_SEED.sql](sql/03_optional/CHAT_CLUSTER_REAL_USER_SEED.sql) — (선택) 데모/개발용 사용자 시드

> 알림(`user_alerts`)의 모든 컬럼(`title`·`link_url`·`related_post_id`·`related_comment_id`·`notice_id`)과 9종 `alert_type`은 **`MONITORING.sql` 하나에 포함**됩니다. 별도 패치 SQL은 없습니다. 반드시 **`APP_USER`**(=`.env`의 `DB_USERNAME`)로 실행하세요.

### 3. 서버 실행

```bash
./mvnw spring-boot:run
```

브라우저: **http://localhost:8081** (`server.port=8081`)

---

## 기능 요약

| 메뉴 | 경로 | 상태 | 설명 |
|------|------|------|------|
| 홈 / 소개 | `/` | ✅ | 랜딩. `/info`는 홈 서비스 소개(`/#service-intro`)로 리다이렉트 |
| 로그인·회원가입 | `/login`, `/signup` | ✅ | 세션 기반, 등급 `USER` / `COUNSELOR` / `ADMIN` |
| 자가진단 | `/self-assessment/**` | ✅ | PHQ-9, GAD-7, PSS, CBI — 로그인 시 결과 저장 |
| 알림 | `/alerts` | ✅ | 고위험·악화·개선·맞춤 추천·커뮤니티·공지·관리자 메시지 |
| 상담소 찾기 | `/counseling` | ✅ | 네이버 **지역검색** API, 지도는 좌표 우선·없으면 장소명 검색 |
| 상담 예약 | `/counseling/booking` | ✅ | `bookings` 테이블 저장 |
| 커뮤니티 | `/community/**` | ✅ | 게시글·댓글·답글·좋아요·신고, 이미지/동영상 첨부, 본문 YouTube embed |
| 공지 | `/notice/**` | ✅ | ADMIN만 작성·수정·삭제 |
| 내 정보 | `/user/me`, `/user/me/edit` | ✅ | 프로필(닉네임·지역·연락처)·프로필 이미지·알림 수신 설정 |
| AI 맞춤 추천 | `/recommendations` | ✅ | Gemini + 네이버 도서 검색 (`POST /api/recommendations/ai`) |
| AI 정서 케어 (위로 편지) | `/care-report` · `/ai-care` | ✅ | 위저드 → OpenAI 장문 편지 + PDF |
| 관리자 | `/admin/**` | ✅ | 대시보드·유저 관리·게시글·공지·알림 발송·정서 클러스터(3D)·SQL 콘솔 (ADMIN 전용) |

---

## 주요 URL

### 화면

| 기능 | Method | URL |
|------|--------|-----|
| 회원가입 | GET/POST | `/signup` |
| 로그인 / 로그아웃 | GET·POST / POST | `/login`, `/logout` |
| 내 정보 | GET | `/user/me` |
| 정보 수정 | GET/POST | `/user/me/edit` |
| 공지 목록·상세 | GET | `/notice`, `/notice/{id}` |
| 공지 작성·수정·삭제 | GET/POST | `/notice/new`, `/notice/{id}/edit`, `/notice/{id}/delete` *(ADMIN)* |
| 커뮤니티 | GET/POST | `/community`, `/community/{id}`, … |
| 자가진단 | GET/POST | `/self-assessment`, `/self-assessment/{typeKey}`, `…/result` |
| 알림 | GET/POST | `/alerts`, `/alerts/read-all`, `/alerts/{id}/delete` |
| 상담소·예약 | GET/POST | `/counseling`, `/counseling/booking` |
| 관리자 | GET/POST | `/admin`, `/admin/users`, `/admin/posts`, `/admin/notices`, `/admin/alerts`, `/admin/cluster`, `/admin/sql` *(ADMIN)* |

### REST API (일부)

| 기능 | Method | URL |
|------|--------|-----|
| AI 도서 추천 | POST | `/api/recommendations/ai` |
| 감정별 도서 목록 | GET | `/api/recommendations?emotion=…` |
| 상담소 검색 | GET | `/api/counseling/centers?query=…` |
| 예약 | POST/GET | `/api/counseling/bookings`, `/bookings/me`, … |
| 도서 리뷰 | GET/POST | `/api/reviews` |

상세 명세: [docs/api.md](docs/api.md)

---

## 알림 (`/alerts`)

- **모니터링 알림** — 자가진단 결과 기반: 고위험·악화는 **위험(빨강 톤)**, 개선·맞춤 추천은 긍정 톤
- **일반 알림** — 공지·커뮤니티 댓글/답글·**관리자 메시지**: 위험 알림과 구분되는 **일반(초록·벨 톤)**
- 알림에 `link_url`이 있으면 관련 글·공지로 바로 이동합니다.

### 관리자 알림 발송 (`/admin/alerts`, ADMIN)

- **제목**(선택) + **본문**(필수)으로 특정 회원 또는 전체에게 `ADMIN_MESSAGE` 알림을 보냅니다.
- **「바로가기 링크 넣기」를 체크했을 때만** `link_url`이 저장되어 알림에 링크가 표시됩니다.

---

## AI 맞춤 도서 추천 (요약)

`POST /api/recommendations/ai` — body: `{ "message": "..." }`

1. **Gemini** — 사용자 문장에서 감정 태그·검색어·요약 추출  
2. **서버** — DB + 네이버 도서 API로 후보 수집  
3. **Gemini** — 후보 목록에서 최대 3권 선별·이유 생성 → 필요 시 `recommendation_books`에 반영  

키 미설정 시 단계별 fallback으로 서버는 기동·동작합니다.

---

## 상담소 · 지도

- 목록: `NaverLocalSearchClient` → 네이버 지역검색 OpenAPI  
- 썸네일: 상위 N건에 이미지 검색 API 보조  
- **지도 보기**: API `mapx`/`mapy`(WGS84×10⁷)가 있으면 해당 좌표로 열고, 없으면 **장소명만** 검색 (이름+주소 조합은 사용하지 않음)

---

## 팀 통합 이력 (참고)

| 담당 | 반영 내용 |
|------|-----------|
| 서상원 | Oracle, AI 맞춤 추천, `ORACLE_SETUP.sql` |
| 김동주 | 로그인·커뮤니티·공지·첨부파일 |
| 김지훈 | DB 자가진단, `ASSESSMENT_SEED.sql` |
| 윤아연 | 상담소 검색·예약 API |

구 더미 코드(`MapApiClient`, `Diagnosis*` 등)는 제거되었습니다.

---

## 리팩토링 노트 (care 패키지)

- `CareLetterAiRouter` · `CareLetterOpenAiClient` · `CareLetterGeminiClient` · `CareLetterAiResult` · `CareLetterPromptBuilder` → **`CareLetterService` 하나로 통합**
- Gemini 백업 경로 제거 — 위로 편지는 **OpenAI 단일 백엔드**. 도서 추천(Gemini)은 그대로 유지
- 사용처가 없던 `CareDailyInput` 엔티티·리포지토리·저장 로직 삭제 (테이블 DDL 은 legacy 로 표시)
- 개발용 `/api/test/openai` 엔드포인트(`OpenAiTestController`) 제거 · `care.llm.provider`·`CARE_LLM_PROVIDER`·`gemini.model` 설정 정리
- DTO 7개 클래스 → **record** 전환, 컨트롤러 공통 `currentUser/excerpt` → `CareWebSupport` 로 추출

---

## 문서

| 문서 | 내용 |
|------|------|
| [docs/frontend.md](docs/frontend.md) | 화면·템플릿·CSS |
| [docs/backend.md](docs/backend.md) | 서버·설정 |
| [docs/db.md](docs/db.md) | DB 연동 |
| [docs/api.md](docs/api.md) | REST API |

---

## 관리자

`role = 'ADMIN'`인 계정으로 로그인하면 `/admin`에서 대시보드·유저 관리·게시글·공지·알림 발송·정서 클러스터(3D)·SQL 콘솔을 사용할 수 있습니다. 초기 관리자·샘플 데이터는 `01_schema/ORACLE_SETUP.sql` 시드에 포함됩니다. 등급을 직접 부여하려면:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';
COMMIT;
```

---

## 로컬 H2 (선택)

Oracle 없이 화면만 볼 때는 `application.properties` 프로필에 따라 H2·`data.sql`을 사용할 수 있습니다.  
팀 기본 환경은 Oracle이며, `spring.sql.init.mode=never`로 앱 기동 시 SQL 자동 실행은 끄는 설정이 일반적입니다.

---

## 추후 작업

- 게시글 페이지네이션  
- 커뮤니티 작성자 — 현재 `posts.author` 문자열, `User` FK 정리  
- 비밀번호 변경 기능  

---

## 프로젝트 구조 (요약)

```
src/main/java/
  com.mindlink/
    MindLinkApplication.java # Spring Boot 진입점
    config/                  # SecurityConfig, DotEnvLoader 등
    controller/              # MVC·REST
    service/                 # 비즈니스 로직 (NaverLocalSearchClient 등)
    domain/                  # JPA 엔티티
    repository/
    dto/
    care/                    # AI 종합 보고서(위로 편지)
    chatcluster/             # 정서 3D 클러스터링
    recommendation/          # AI 도서 추천 (enum·domain·repository·dto·client·service·web)
sql/
  README.md                  # 실행 순서·업그레이드·트러블슈팅 가이드
  01_schema/                 # ORACLE_SETUP.sql (전체 DDL + 시드)
  02_features/               # USERS_PROFILE · ASSESSMENT_SEED · MONITORING · CARE_REPORT · CHAT_CLUSTERING
  03_optional/               # CHAT_CLUSTER_REAL_USER_SEED
  archive/                   # 마이그레이션·복구 전용 (MONITORING_FIX/REBUILD, COMMUNITY_CATEGORY_MIGRATE)
src/main/resources/templates/   # Thymeleaf 화면 (admin/ 포함)
```
