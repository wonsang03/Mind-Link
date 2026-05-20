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
| 외부 API | 네이버 검색(지역·도서·이미지), Google Gemini |

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
| `GEMINI_API_KEY` | AI 맞춤 도서 추천 |

네이버 개발자센터 앱에서 **검색 API** 사용을 켜야 상담소·도서 검색이 동작합니다.

### 2. DB 스크립트 (Oracle)

순서대로 SQL Developer 등에서 실행합니다.

1. [docs/LoginCommunity.md](docs/LoginCommunity.md) — 기본 테이블 DDL  
2. [sql/ORACLE_SETUP.sql](sql/ORACLE_SETUP.sql) — 스키마 보정, 시드(관리자·공지·커뮤니티·추천도서·예약 등)  
3. [sql/ASSESSMENT_SEED.sql](sql/ASSESSMENT_SEED.sql) — 자가진단 문항·점수 구간

### 3. 서버 실행

```bash
./mvnw spring-boot:run
```

브라우저: **http://localhost:8081** (`server.port=8081`)

---

## 기능 요약

| 메뉴 | 경로 | 상태 | 설명 |
|------|------|------|------|
| 홈 / 소개 | `/`, `/info` | ✅ | 랜딩·소개 |
| 로그인·회원가입 | `/login`, `/signup` | ✅ | 세션 기반, 등급 `USER` / `COUNSELOR` / `ADMIN` |
| 자가진단 | `/self-assessment/**` | ✅ | PHQ-9, GAD-7, PSS, CBI — Oracle 문항 DB |
| 상담소 찾기 | `/counseling` | ✅ | 네이버 **지역검색** API, 지도는 좌표 우선·없으면 장소명 검색 |
| 상담 예약 | `/counseling/booking` | ✅ | `bookings` 테이블 저장 |
| 커뮤니티 | `/community/**` | ✅ | 게시글·댓글·좋아요·신고, 이미지/동영상 첨부, 본문 YouTube embed |
| 공지 | `/notice/**` | ✅ | ADMIN만 작성·수정·삭제 |
| 내 정보 | `/user/me` | ✅ | 조회·이름/이메일 수정 (비밀번호 변경 미구현) |
| AI 맞춤 추천 | `/recommendations` | ✅ | Gemini + 네이버 도서 검색 (`POST /api/recommendations/ai`) |
| AI 정서 케어 | `/ai-care` | 🔶 데모 | 키워드 기반 챗봇 UI (실제 LLM API 미연동) |

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
| 상담소·예약 | GET/POST | `/counseling`, `/counseling/booking` |

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

구 더미 코드(`MapApiClient`, `Diagnosis*` 등)는 제거되었습니다. 정리 규칙: [docs/AGENT_PROMPT_DUMMY_CLEANUP.md](docs/AGENT_PROMPT_DUMMY_CLEANUP.md)

---

## 문서

| 문서 | 내용 |
|------|------|
| [docs/frontend.md](docs/frontend.md) | 화면·템플릿·CSS |
| [docs/backend.md](docs/backend.md) | 서버·설정 |
| [docs/db.md](docs/db.md) | DB 연동 |
| [docs/api.md](docs/api.md) | REST API |
| [docs/LoginCommunity.md](docs/LoginCommunity.md) | 로그인·커뮤니티 DDL |

---

## 관리자 등급 변경

관리자 UI는 없습니다. Oracle에서 직접 수정합니다.

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@example.com';
```

초기 관리자·샘플 데이터는 `sql/ORACLE_SETUP.sql` 시드에 포함됩니다.

---

## 로컬 H2 (선택)

Oracle 없이 화면만 볼 때는 `application.properties` 프로필에 따라 H2·`data.sql`을 사용할 수 있습니다.  
팀 기본 환경은 Oracle이며, `spring.sql.init.mode=never`로 앱 기동 시 SQL 자동 실행은 끄는 설정이 일반적입니다.

---

## 추후 작업

- `/ai-care` — OpenAI 또는 Gemini 실제 대화 API 연동  
- 관리자 화면 — 등급 변경, 신고 목록  
- 게시글 페이지네이션, 작성자–`User` FK 정리  

---

## 프로젝트 구조 (요약)

```
src/main/java/
  com.example.demo/          # Spring Boot 진입점, AI 추천(recommendation)
  com.mindlink/
    controller/              # MVC·REST
    service/                 # 비즈니스 로직 (NaverLocalSearchClient 등)
    domain/                  # JPA 엔티티
    repository/
    dto/
sql/
  ORACLE_SETUP.sql
  ASSESSMENT_SEED.sql
src/main/resources/templates/   # Thymeleaf 화면
```
