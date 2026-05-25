# 백엔드

Spring Boot 기반 MVC 서버. Oracle DB(운영) / H2(로컬) 이중 환경을 지원한다.

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| 프레임워크 | Spring Boot 3.x |
| 언어 | Java 17+ |
| ORM | Spring Data JPA (Hibernate) |
| DB (운영) | Oracle (`jdbc:oracle:thin:@localhost:1521/FREEPDB1`) |
| DB (로컬) | H2 인메모리 (`spring-boot --spring.profiles.active=local`) |
| 뷰 | Thymeleaf |
| 보안 | Spring Security (전 경로 permitAll, CSRF 비활성) + 세션 기반 인증 |
| 파일 업로드 | Spring Multipart → 로컬 `uploads/` 디렉토리 |

---

## 패키지 구조

```
com.mindlink
├── config/          # AppConfig, NaverProperties
├── controller/      # HTTP 요청 처리
├── domain/          # JPA 엔티티
├── dto/             # 요청·응답 DTO
├── repository/      # Spring Data JPA 인터페이스
├── service/         # 비즈니스 로직
└── web/             # CurrentUserAdvice, SessionConst

com.example.demo
└── config/          # SecurityConfig
    recommendation/  # 도서 추천 (AI, 별도 모듈)
```

---

## 인증 · 세션

- 로그인 성공 시 `session.setAttribute(SessionConst.LOGIN_USER_ID, user.getId())` 로 `Long` ID 저장
- 각 컨트롤러에서 `session.getAttribute(SessionConst.LOGIN_USER_ID)` → `instanceof Long uid` 패턴으로 인증 확인
- `CurrentUserAdvice` (`@ControllerAdvice`) 가 모든 뷰에 `loginUser` (User 엔티티), `currentUri` 를 자동 주입
- Spring Security는 CSRF 비활성 / 전 경로 permitAll 설정 — 접근 제어는 컨트롤러에서 수동 처리

---

## 컨트롤러

### AuthController
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/login` | 로그인 폼 |
| POST | `/login` | 이메일+비밀번호 인증, 세션 저장 후 `/` 리다이렉트 |
| POST | `/logout` | 세션 무효화 |
| GET | `/signup` | 회원가입 폼 |
| POST | `/signup` | 회원 생성, 이메일 중복·비밀번호 불일치 검증 |

### UserController (`/user`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/user/me` | 내 계정 정보 조회 |
| GET | `/user/me/edit` | 프로필 수정 폼 |
| POST | `/user/me/edit` | 프로필 저장 (이미지 업로드 포함, 기존 이미지 자동 삭제) |

### CommunityController (`/community`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/community` | 게시글 목록 (카테고리 필터, 키워드 검색) |
| GET | `/community/{id}` | 게시글 상세 (첨부파일·댓글 첨부파일 포함) |
| GET | `/community/new` | 글 작성 폼 (로그인 필요) |
| POST | `/community` | 게시글 생성 (파일·링크 첨부 가능) |
| GET | `/community/{id}/edit` | 글 수정 폼 (작성자 본인만) |
| POST | `/community/{id}/edit` | 게시글 수정 (첨부파일 삭제·추가) |
| POST | `/community/{id}/delete` | 게시글 삭제 (작성자 본인만) |
| POST | `/community/{id}/like` | 좋아요 |
| POST | `/community/{id}/comments` | 댓글 작성 |
| POST | `/community/{postId}/comments/{commentId}/delete` | 댓글 삭제 (작성자 본인만) |
| POST | `/community/{id}/report` | 게시글 신고 |
| POST | `/community/{postId}/comments/{commentId}/report` | 댓글 신고 |

커뮤니티 카테고리: `전체 / 스트레스 관리 / 경험 공유 / 함께 해요 / 질문과 답변 / 추천 및 후기`

### SelfAssessmentController (`/self-assessment`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/self-assessment` | 검사 유형 목록 |
| GET | `/self-assessment/{typeKey}` | 문항 퀴즈 화면 |
| POST | `/self-assessment/{typeKey}/result` | 점수 계산 후 결과 화면 |

지원 typeKey: `depression` (PHQ-9) / `anxiety` (GAD-7) / `stress` (PSS-10) / `burnout` (CBI)

번아웃은 `part 1`(개인 번아웃)·`part 2`(업무 번아웃) 점수를 각각 평균으로 계산한다.

### CounselingController (`/counseling`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/counseling` | 상담소 목록 (네이버 지역검색 API 또는 더미) |
| GET | `/counseling/booking` | 예약 폼 (센터 정보 쿼리파라미터 수신) |
| POST | `/counseling/booking` | 예약 저장 |
| GET | `/counseling/bookings/{id}` | 예약 완료 상세 |

### NoticeController (`/notice`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/notice` | 공지 목록 |
| GET | `/notice/{id}` | 공지 상세 |
| GET | `/notice/new` | 공지 작성 폼 (ADMIN 전용) |
| POST | `/notice` | 공지 생성 |
| GET | `/notice/{id}/edit` | 공지 수정 폼 (ADMIN 전용) |
| POST | `/notice/{id}/edit` | 공지 수정 |
| POST | `/notice/{id}/delete` | 공지 삭제 |

---

## 서비스

| 서비스 | 주요 메서드 |
|--------|------------|
| `UserService` | `signup`, `login`, `findById`, `updateProfile`, `changeRole` |
| `FileStorageService` | `saveFiles`, `saveLinks`, `deleteByTarget`, `saveProfileImage`, `deleteProfileImage` |
| `CommunityService` | `findAll`, `findById`, `create`, `updatePost`, `deletePost`, `addComment`, `deleteComment`, `like`, `report` |
| `AssessmentService` | `findAll`, `findByTypeKey`, `evaluate` |
| `CounselingService` | `searchCenters`, `createBooking`, `findBooking`, `isExternalApiConfigured` |
| `NoticeService` | `findAll`, `findById`, `create`, `update`, `delete` |

---

## 도메인 모델

| 엔티티 | 테이블 | 비고 |
|--------|--------|------|
| `User` | `users` | `UserRole` ENUM (USER / ADMIN), 프로필 확장 컬럼 포함 |
| `Post` | `posts` | 커뮤니티 게시글, 작성자·카테고리·좋아요 수 |
| `PostComment` | `post_comments` | 게시글 댓글 |
| `Attachment` | `attachments` | 게시글·댓글 첨부 (IMAGE / VIDEO / LINK), `target_type` + `target_id` 로 다형 연관 |
| `Report` | `reports` | 게시글·댓글 신고, `TargetType` ENUM |
| `Booking` | `bookings` | 상담 예약, 센터 정보 + 사용자 정보 저장 |
| `Notice` | `notices` | 공지사항 |
| `AssessmentType` | `assessment_types` | 진단 유형 (`type_key` UK) |
| `AssessmentQuestion` | `assessment_questions` | 진단 문항, `reversed`(역채점) · `part`(번아웃 파트) |
| `AssessmentChoice` | `assessment_choices` | 선택지 및 점수 |
| `ScoreRange` | `score_ranges` | 점수 구간별 결과 레벨·메시지 |

### User 프로필 확장 컬럼

`ORACLE_SETUP.sql` 이후 `ASSESSMENT_SEED.sql` 에서 추가한다 (`ddl-auto=none` 이므로 수동 실행 필요).

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `nickname` | VARCHAR2(100) | 선택 |
| `region` | VARCHAR2(100) | 선택 |
| `notification_enabled` | NUMBER(1) DEFAULT 0 | 0/1 체크 제약 |
| `phone` | VARCHAR2(20) | 선택 |
| `profile_image_url` | VARCHAR2(500) | `/uploads/{uuid}.ext` 형태 |

---

## 파일 업로드

- 저장 경로: 프로젝트 루트 `uploads/` (설정: `app.upload.dir=uploads`)
- URL 경로: `/uploads/{uuid}.ext` → Spring이 정적 리소스로 서빙
- 파일 제한:

| 종류 | 허용 확장자 | 최대 크기 |
|------|------------|----------|
| 이미지 (게시글·댓글) | jpg, jpeg, png, gif, webp | 10 MB |
| 동영상 (게시글·댓글) | mp4, webm | 100 MB |
| 프로필 사진 | jpg, jpeg, png, gif, webp | 5 MB |
| 링크 | http:// 또는 https:// 로 시작하는 URL | — |

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
spring.jpa.hibernate.ddl-auto=create-drop # 앱 시작 시 User.java 기반 자동 생성
spring.h2.console.path=/h2-console
```
H2 환경에서는 SQL 파일 실행 불필요.

### 외부 API 키 (`.env` 파일)
| 키 | 용도 |
|----|------|
| `NAVER_API_CLIENT_ID` / `NAVER_API_CLIENT_SECRET` | 네이버 도서 검색 |
| `NAVER_OPENAPI_CLIENT_ID` / `NAVER_OPENAPI_CLIENT_SECRET` | 네이버 지역검색 (상담소 찾기) |
| `GEMINI_API_KEY` | Google Gemini (AI 도서 추천) |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Oracle 접속 정보 |
