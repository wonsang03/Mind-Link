# Mind-Link
목포대 웹프2 5조 팀플

## 초기 생성 설정(Spring Initializr)
- Project: `Maven`
- Language: `Java`
- Spring Boot: `4.0.6`
- Group: `com.example`
- Artifact: `demo`
- Package Name: `com.example.demo`
- Packaging: `jar`
- Java: `17`
- Configuration: `Properties`

## 의존성 구성과 역할
- `Spring Web`: REST API 및 웹 요청/응답 처리(MVC 기반)
- `Lombok`: 반복적인 getter/setter/constructor 코드 축약
- `Spring Boot DevTools`: 개발 중 자동 재시작 및 편의 기능 지원
- `Spring Data JPA`: 데이터베이스 접근 계층(JPA/Hibernate) 구성
- `Oracle Vector Database`: Oracle 벡터 저장소 연동(임베딩 저장/검색)
- `Spring Security`: 인증/인가 및 보안 필터 체인 구성
- `OpenAI`: OpenAI 모델 연동(LLM/임베딩 호출)
- `Validation`: 요청 DTO/엔티티 입력값 검증
- `Thymeleaf`: 서버 사이드 HTML 템플릿 렌더링

> **DB는 Oracle을 사용합니다.** (팀 개발·운영 기준)

## 오류났던부분

처음에는 `pom.xml`에 **라이브러리(의존성)만 받아 둔 상태**였고,  
`application.properties` **설정 파일은 거의 비어 있었습니다.**

그래서 프로그램을 켤 때 Spring Boot가 이렇게 반응했습니다.

- JPA를 쓰겠다고 했는데 → **DB가 어디 있는지** 모름 → 오류
- Security를 넣었는데 → **누구에게 열어 줄지** 모름 → 막히거나 오류
- OpenAI를 넣었는데 → **API 키가 없음** → 오류

그래서 **일단 내 PC에서만** 돌아가게 아래를 임시로 넣었습니다.

| 임시로 넣은 것 | 왜 넣었는지 |
|----------------|-------------|
| H2 + DB 설정 | Oracle 연결 전, 로컬에서만 실행 테스트 |
| Thymeleaf | 통합해 둔 HTML 화면 보이게 |
| Security 전체 허용 | 로그인 설정 전에 화면부터 확인 |
| Spring AI 자동 설정 끄기 | API 키 없이도 서버가 켜지게 |

Oracle·OpenAI는 나중에 `application.properties`에 **팀에서 쓸 값**을 넣을 예정

## 로컬에서 실행

```bash
./mvnw spring-boot:run
```

브라우저: **http://localhost:8080**

## 문서 (팀원 작성용)

| 문서 | 설명 |
|------|------|
| [docs/frontend.md](docs/frontend.md) | 화면(UI) 템플릿·CSS |
| [docs/backend.md](docs/backend.md) | 서버 로직·설정 |
| [docs/db.md](docs/db.md) | Oracle DB 스키마·연동 |
| [docs/api.md](docs/api.md) | REST API 명세 |
| [docs/LoginCommunity.md](docs/LoginCommunity.md) | Oracle DB CREATE TABLE SQL (로그인/커뮤니티 관련 전체 테이블) |

## 현재 상태
- Spring Initializr 프로젝트 골격 및 기본 의존성 적용 완료
- 프론트엔드(Thymeleaf 템플릿·CSS) `main` 브랜치 통합 완료
- 로컬 실행용 임시 설정(H2, Security, Thymeleaf 등) 반영 완료
- Oracle DB·OpenAI 연동 설정은 추후 `application.properties`에 추가 예정

---

## 구현

### 로그인 / 회원가입
- `POST /signup` — 이름, 이메일, 비밀번호로 회원가입 (기본 등급: `USER`)
- `POST /login` — 이메일/비밀번호 로그인, 세션 발급
- `POST /logout` — 세션 무효화
- 비밀번호: `BCryptPasswordEncoder`로 암호화 저장
- 인증 방식: `HttpSession` 기반 (Spring Security 필터 없이 직접 검증)

### 사용자 등급 구조
- `UserRole` enum: `USER` (기본값), `COUNSELOR`, `ADMIN`
- 회원가입 시 항상 `USER`로 고정
- 등급 변경은 DB에서 `role` 컬럼 직접 수정 또는 `UserService.changeRole()` 호출

### 공지사항 기능
- 공지 목록 (`GET /notice`), 상세 조회 (`GET /notice/{id}`)
- 공지 작성 (`GET /notice/new`, `POST /notice`) — **ADMIN 전용**
- 공지 수정 (`GET /notice/{id}/edit`, `POST /notice/{id}/edit`) — **ADMIN 전용**
- 공지 삭제 (`POST /notice/{id}/delete`) — **ADMIN 전용**
- 비로그인 / USER / COUNSELOR가 작성·수정·삭제 URL 접근 시 `/notice`로 리다이렉트
- 공지 목록·상세의 작성/수정/삭제 버튼은 ADMIN 로그인 상태에서만 렌더링
- 초기 샘플 공지 3건 `data.sql`로 자동 삽입

### 커뮤니티 기능
- 게시글 목록 (`GET /community`), 상세 조회 (`GET /community/{id}`)
- 게시글 작성 (`POST /community`), 수정 (`GET/POST /community/{id}/edit`), 삭제 (`POST /community/{id}/delete`)
- 댓글 작성/삭제, 좋아요
- 게시글 신고 (`POST /community/{id}/report`)
- 댓글 신고 (`POST /community/{postId}/comments/{commentId}/report`)
- 카테고리 필터 및 제목/내용 검색 지원

### 게시글 이미지/동영상/URL 첨부
- 게시글 작성·수정 시 이미지(jpg/jpeg/png/gif/webp) 또는 동영상(mp4/webm) 파일 첨부 (여러 개)
- 이미지 최대 10MB / 동영상 최대 100MB — 초과 또는 허용 외 확장자는 서버에서 거부
- 업로드된 파일은 서버 로컬 `uploads/` 폴더에 UUID 파일명으로 저장, DB에는 경로만 저장
- 파일은 `/uploads/{uuid}.{ext}` URL로 접근 (Spring 정적 리소스 핸들러)
- 첨부된 이미지는 게시글 상세 화면에서 `<img>` 태그로 표시, 클릭 시 라이트박스 확대
- 첨부된 동영상은 `<video controls>` 태그로 인라인 재생
- URL 첨부 가능 (YouTube 등) — 여러 개 입력 가능, 입력 시 실시간 미리보기 제공
  - YouTube URL(`youtube.com/watch?v=`, `youtu.be/` 형식) → `<iframe>` 임베드로 표시
  - 일반 URL → 클릭 가능한 링크 카드로 표시

### 본문 내 YouTube URL 인라인 렌더링
- 게시글·댓글 본문에 YouTube URL을 직접 입력하면 상세 화면에서 URL 텍스트는 표시되지 않고 그 위치에 `<iframe>` 영상 플레이어로 렌더링
- 지원 URL 형식: `youtube.com/watch?v=`, `youtu.be/`, `youtube.com/embed/`
- 순서 보장: YouTube URL이 있던 위치 그대로 영상이 삽입됨 (본문 맨 위/아래 고정 아님)
- 보안: youtube.com / youtu.be 도메인만 허용, 기타 URL은 일반 텍스트로 유지 (외부 iframe 차단)
- 게시글 본문(`#postContent`) + 댓글 본문(`.comment-content`) 동일 적용
- YouTube URL이 없으면 DOM 변경 없이 원본 `th:text` 렌더링 유지
- 작성·수정 폼의 내용 라벨에 "YouTube 등 영상 링크를 본문에 포함하면 자동으로 재생 가능하게 표시됩니다" 안내 문구
- **별도 "URL 첨부" 입력 칸 제거** — 게시글 작성·수정 폼, 댓글 작성 폼 모두 해당

### 댓글 이미지/동영상/URL 첨부
- 댓글 작성 시 이미지/동영상 파일 첨부 가능 (동일한 형식·크기 제한 적용)
- 댓글 작성 시 URL 첨부 가능 (YouTube embed 또는 링크 카드)
- 댓글 본문 내 URL 자동 감지 및 영상 embed (게시글과 동일)
- 댓글 목록 내 각 댓글 하단에 첨부 이미지/동영상/링크 표시
- 댓글 삭제 시 연결된 첨부파일(파일·링크 모두)도 DB에서 함께 삭제
- 게시글 삭제 시 게시글 첨부파일 + 모든 댓글 첨부파일 일괄 삭제

### 자가진단 기능
- 진단 목록 / 퀴즈 / 결과 처리 (`/self-assessment/**`)
- `DiagnosisService`에서 점수 계산, 위험군 분류, 결과 DB 저장 (로그인 사용자만)
- 등급: 정상(≤4) / 경미(≤9) / 중등도(≤14) / 심각(15+)

### 상담소 기능
- 상담소 목록 조회 (`GET /counseling`) — 더미 데이터 기반 카드형 목록 표시
- 센터 유형별 필터 및 키워드 검색 지원
- `MapApiClient`를 통해 공공데이터/지도 API 연동 예정 (현재 더미 반환)

### 내 계정 조회
- `GET /user/me` — 로그인한 사용자 정보 조회 (`UserResponse` 반환)
- 헤더 우측 "내 정보" 버튼 — 로그인 상태에서만 표시, `/user/me`로 이동
- 내 정보 화면 표시 항목: 이름, 이메일, 계정 등급, 가입일
- 계정 등급 표시 기준: `USER` → 일반 사용자 / `COUNSELOR` → 상담사 / `ADMIN` → 관리자
- 비로그인 상태에서 접근 시 `/login`으로 리다이렉트

### 회원 정보 수정
- `GET /user/me/edit` — 회원 정보 수정 폼 (이름, 이메일 입력란 표시)
- `POST /user/me/edit` — 이름, 이메일 수정 처리 후 `/user/me`로 리다이렉트
- 등급 수정 불가 (수정 폼에 읽기 전용으로 표시)
- 이메일 중복 시 폼 재표시 및 오류 메시지 표시
- 수정 완료 시 내 정보 화면에 플래시 메시지 표시
- 비밀번호 변경은 미구현 (추후 확장 가능 구조)

---

## 추가

### 새로 생성한 패키지
- `com.mindlink.external` — 외부 API 클라이언트

### 새로 추가한 Domain (Entity / Enum)
| 파일 | 설명 |
|------|------|
| `domain/UserRole.java` | 사용자 등급 Enum (USER, COUNSELOR, ADMIN) |
| `domain/DiagnosisResult.java` | 자가진단 결과 Entity |
| `domain/Report.java` | 신고 Entity |
| `domain/Notice.java` | 공지사항 Entity (category, title, summary, content, createdAt) |
| `domain/Attachment.java` | 첨부파일 Entity (originalFileName, storedFileName, fileUrl, fileType, fileSize, targetType, targetId) — `FileType`: IMAGE / VIDEO / **LINK** |

### 새로 추가한 Repository
| 파일 | 설명 |
|------|------|
| `repository/CommentRepository.java` | 댓글 JPA 저장소 |
| `repository/DiagnosisRepository.java` | 자가진단 결과 JPA 저장소 |
| `repository/ReportRepository.java` | 신고 JPA 저장소 |
| `repository/NoticeRepository.java` | 공지사항 JPA 저장소 (`findAllByOrderByCreatedAtDesc`) |
| `repository/AttachmentRepository.java` | 첨부파일 JPA 저장소 (`findByTargetTypeAndTargetId`, `deleteByTargetTypeAndTargetId`) |

### 새로 추가한 DTO
`LoginRequest`, `SignupRequest`, `LoginResponse`, `UserResponse`,  
`DiagnosisSubmitRequest`, `DiagnosisResultResponse`, `AiReportResponse`,  
`PostRequest`, `PostResponse`, `CommentRequest`, `CommentResponse`,  
`CounselingCenterResponse`, `ReportRequest`, `UserUpdateRequest`, `NoticeRequest`,  
`AttachmentResponse`

### 새로 추가한 Service
| 파일 | 설명 |
|------|------|
| `service/AuthService.java` | 회원가입/로그인 전담 서비스 |
| `service/CommunityService.java` | 게시글/댓글/신고 핵심 로직 |
| `service/DiagnosisService.java` | 점수 계산, 결과 저장, 위험군 분류 |
| `service/AiReportService.java` | AI 추천 메시지 생성 (현재 더미 응답) |
| `service/NoticeService.java` | 공지사항 CRUD (findAll, findById, create, update, delete) |
| `service/FileStorageService.java` | 파일 업로드·조회·삭제 (로컬 디스크 저장, UUID 파일명, 확장자·크기 검증) + URL 링크 저장(`saveLinks`) |

### 새로 추가한 Controller
| 파일 | 설명 |
|------|------|
| `controller/DiagnosisController.java` | 자가진단 요청 처리 (`/self-assessment/**`) |
| `controller/UserController.java` | 내 계정 조회 및 수정 (`/user/**`) |
| `controller/NoticeController.java` | 공지사항 CRUD (`/notice/**`), ADMIN 권한 제어 포함 |

### 새로 추가한 Template
| 파일 | 설명 |
|------|------|
| `templates/user/edit.html` | 회원 정보 수정 페이지 (이름/이메일 수정 폼, 등급·가입일 읽기 전용 표시) |
| `templates/notice-form.html` | 공지 작성·수정 공용 폼 (ADMIN 전용) |
| `templates/community/edit.html` | 게시글 수정 폼 (기존 첨부파일 유지/삭제, 새 파일 추가) |

### 새로 추가한 External
| 파일 | 설명 |
|------|------|
| `external/OpenAiClient.java` | OpenAI API 호출 클라이언트 (틀) |
| `external/MapApiClient.java` | 지도/공공데이터 API 호출 클라이언트 (더미 데이터) |

---

## 수정

| 파일 | 수정 내용 | 이유 |
|------|---------|------|
| `domain/User.java` | `UserRole role` 필드 추가 (`@Enumerated(STRING)`, 기본값 `USER`) | 계정 등급 구조 구현 |
| `service/UserService.java` | `UserRole.USER` 기본값 명시, `findAll()` / `changeRole()` 추가 | 관리자 기능 연결 지원 |
| `service/PostService.java` | `CommunityService` 상속 래퍼로 교체 (deprecated), `FileStorageService` 파라미터 추가 | `CommunityService`로 대체, 기존 참조 호환성 유지 |
| `controller/AuthController.java` | `LoginForm` → `LoginRequest`, `SignupForm` → `SignupRequest` | 요청 클래스명 통일 |
| `controller/CommunityController.java` | 신고 엔드포인트 추가, `FileStorageService` 주입, 파일 첨부 파라미터 추가, 게시글 수정 엔드포인트 추가 | 파일 업로드 기능 통합 |
| `service/CommunityService.java` | `FileStorageService` 주입, `create`/`addComment`에 파일 저장 로직 추가, `updatePost` 신규, `deletePost`/`deleteComment`에 첨부파일 정리 추가 | 파일 업로드 기능 통합 |
| `dto/PostResponse.java` | `List<AttachmentResponse> attachments` 필드 추가 | 첨부파일 목록 응답 포함 |
| `dto/CommentResponse.java` | `List<AttachmentResponse> attachments` 필드 추가 | 첨부파일 목록 응답 포함 |
| `config/AppConfig.java` | `/uploads/**` 정적 리소스 핸들러 (`WebMvcConfigurer`) 추가 | 업로드 파일 URL 접근 지원 |
| `resources/application.properties` | `spring.servlet.multipart.*` 설정, `app.upload.dir=uploads` 추가 | 멀티파트 업로드 활성화 |
| `templates/community/new.html` | `enctype="multipart/form-data"`, 파일 input 추가, JS 미리보기 추가 | 게시글 파일 첨부 UI |
| `templates/community/detail.html` | 첨부파일 표시 영역(img/video), 수정 버튼, 댓글 폼 파일 input, 라이트박스 추가 | 첨부파일 표시 및 댓글 파일 첨부 UI |
| `controller/SelfAssessmentController.java` | `@Controller` 제거, deprecated 처리 | `DiagnosisController`와의 URL 충돌 방지 |
| `repository/PostCommentRepository.java` | `@NoRepositoryBean` + `CommentRepository` 상속으로 교체 | `CommentRepository`로 대체, Spring bean 중복 방지 |
| `dto/LoginForm.java` | `LoginRequest` 상속 + deprecated 처리 | 레거시 호환성 유지 |
| `dto/SignupForm.java` | `SignupRequest` 상속 + deprecated 처리 | 레거시 호환성 유지 |
| `templates/fragments/layout.html` | 헤더에 "내 정보" 버튼 추가 (로그인 상태에서만 표시) | 내 정보 페이지 접근 진입점 제공 |
| `templates/user/me.html` | 회원 번호 제거, 게시글 작성 버튼 → 회원 정보 수정 버튼으로 교체, 수정 완료 플래시 메시지 추가 | 내 정보 화면 구성 요구사항 반영 |
| `controller/UserController.java` | `GET/POST /user/me/edit` 엔드포인트 추가 | 회원 정보 수정 API 구현 |
| `service/UserService.java` | `updateProfile(id, name, email)` 메서드 추가 | 이름/이메일 수정 및 이메일 중복 검사 |
| `controller/PageController.java` | 공지사항 관련 엔드포인트 및 하드코딩 데이터 전체 제거 | `NoticeController`로 이관 |
| `templates/notice.html` | ADMIN 전용 "공지 작성" 버튼 추가, 데이터 모델 `Map` → `Notice` 엔티티로 교체 | 공지 DB 이관 및 권한 제어 반영 |
| `templates/notice-detail.html` | ADMIN 전용 수정/삭제 버튼 추가, 날짜 포맷 `#temporals`로 교체 | 공지 DB 이관 및 권한 제어 반영 |
| `templates/community/list.html` | 글쓰기 버튼 아이콘 `color: currentColor` 추가, 텍스트 `<span>` 감싸기 | 아이콘·텍스트 색상 및 비율 통일 |
| `resources/data.sql` | 샘플 공지 3건 INSERT 추가 | 서버 재시작 후에도 초기 데이터 표시 |
| `domain/Attachment.java` | `FileType` enum에 `LINK` 추가 | URL 첨부 타입 지원 |
| `dto/AttachmentResponse.java` | `isYouTube()`, `getYouTubeEmbedUrl()` 메서드 추가 | Thymeleaf에서 YouTube embed URL 추출 |
| `service/FileStorageService.java` | `saveLinks(urls, targetType, targetId)` 추가, `deleteFile()` null/빈 storedFileName 처리 | URL 링크 저장 및 LINK 타입 삭제 시 파일 삭제 방지 |
| `service/CommunityService.java` | `create`, `updatePost`, `addComment`에 `linkUrls` 파라미터 추가 및 `saveLinks()` 호출 | URL 첨부 기능 통합 |
| `controller/CommunityController.java` | `create`, `edit`, `addComment` 엔드포인트에 `linkUrls` 파라미터 추가 | URL 첨부 폼 데이터 수신 |
| `templates/community/new.html` | URL 첨부 입력 섹션 추가 (동적 추가/삭제, 실시간 미리보기) | URL 첨부 UI |
| `templates/community/edit.html` | URL 첨부 입력 섹션 추가, 기존 링크 첨부파일 표시 | URL 첨부 UI |
| `templates/community/detail.html` | LINK 타입 첨부파일 표시 (YouTube iframe embed / 일반 링크 카드), 댓글 폼에 URL 입력 추가 | URL 첨부 표시 UI |
| `templates/community/detail.html` | `autoEmbed`(하단 추가 방식) → `renderYoutubeInContent`(위치 기반 인라인 교체)로 교체, URL 첨부 입력 칸·관련 DOM 제거 | YouTube 인라인 렌더링 |
| `templates/community/new.html` | URL 첨부 입력 섹션 및 관련 JS 제거 | URL 첨부 칸 제거 |
| `templates/community/edit.html` | URL 첨부 입력 섹션 및 관련 JS 제거 | URL 첨부 칸 제거 |
| `docs/LoginCommunity.md` | Oracle DB CREATE TABLE SQL 신규 작성 (users/posts/post_comments/reports/notices/attachments) | Oracle DB 연동 문서 |

---

## 실행 방법

```bash
./mvnw spring-boot:run
```

- 서비스: `http://localhost:8080`
- H2 Console: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:mem:mindlink`
  - User Name: `sa` / Password: (공백)

### 주요 테스트 API

| 기능 | Method | URL |
|------|--------|-----|
| 회원가입 | GET/POST | `/signup` |
| 로그인 | GET/POST | `/login` |
| 로그아웃 | POST | `/logout` |
| 내 계정 조회 | GET | `/user/me` |
| 회원 정보 수정 폼 | GET | `/user/me/edit` |
| 회원 정보 수정 처리 | POST | `/user/me/edit` |
| 공지사항 목록 | GET | `/notice` |
| 공지사항 상세 | GET | `/notice/{id}` |
| 공지 작성 폼 | GET | `/notice/new` *(ADMIN)* |
| 공지 등록 | POST | `/notice` *(ADMIN)* |
| 공지 수정 폼 | GET | `/notice/{id}/edit` *(ADMIN)* |
| 공지 수정 처리 | POST | `/notice/{id}/edit` *(ADMIN)* |
| 공지 삭제 | POST | `/notice/{id}/delete` *(ADMIN)* |
| 커뮤니티 목록 | GET | `/community` |
| 게시글 상세 | GET | `/community/{id}` |
| 게시글 작성 폼 | GET | `/community/new` |
| 게시글 작성 | POST | `/community` |
| 게시글 수정 폼 | GET | `/community/{id}/edit` |
| 게시글 수정 처리 | POST | `/community/{id}/edit` |
| 게시글 신고 | POST | `/community/{id}/report` |
| 자가진단 목록 | GET | `/self-assessment` |
| 자가진단 결과 | POST | `/self-assessment/{testId}/result` |
| 상담소 목록 | GET | `/counseling` |

---

## 주의사항

### API Key 설정 (`application.properties`에 추가)
```properties
# OpenAI API
openai.api.key=sk-xxxx
openai.model=gpt-4o-mini

# 지도/공공데이터 API
map.api.key=YOUR_KEY
map.api.url=YOUR_URL
```
미설정 시에도 앱은 정상 실행됩니다 (더미 데이터 반환).

### 등급 변경 방법 (관리자 화면 미구현)
H2 Console 또는 DB에서 직접 수정:
```sql
UPDATE users SET role = 'COUNSELOR' WHERE email = 'example@email.com';
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@email.com';
```

### 추후 보완 필요 항목
- `CounselingController` — `MapApiClient` 실제 API 연동
- `AiReportService` — `OpenAiClient` 실제 OpenAI 연동
- 관리자 화면 — 사용자 등급 변경, 신고 목록 관리
- `Post`, `PostComment` 작성자를 `User` 외래키로 직접 연결
- 게시글 목록 페이지네이션

> 클래스 다이어그램 작성은 `CD_DJ.md` 파일을 참고하세요.
