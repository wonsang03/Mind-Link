# CD_DJ.md — 클래스 다이어그램 참고 문서

> 백엔드 개발자가 클래스 다이어그램(Class Diagram)을 작성할 때 참고하세요.  
> 패키지: `com.mindlink`

---

## 1. 패키지 구조

```
com.mindlink
├── config/
│   └── AppConfig               (BCryptPasswordEncoder Bean 등록)
├── controller/
│   ├── AuthController          (로그인, 회원가입, 로그아웃)
│   ├── CommunityController     (게시글, 댓글, 신고)
│   ├── CounselingController    (상담소 찾기, 예약)
│   ├── DiagnosisController     (자가진단 목록, 퀴즈, 결과)
│   ├── NoticeController        (공지사항 CRUD, ADMIN 권한 제어)
│   ├── PageController          (홈, 소개, AI케어, 추천)
│   └── UserController          (내 계정 조회·수정)
├── domain/
│   ├── User                    (Entity)
│   ├── UserRole                (Enum)
│   ├── Post                    (Entity)
│   ├── PostComment             (Entity)
│   ├── DiagnosisResult         (Entity)
│   ├── Notice                  (Entity)
│   ├── Attachment              (Entity — 게시글/댓글 첨부파일, FileType·TargetType 내부 enum 포함)
│   └── Report                  (Entity)
├── dto/
│   ├── LoginRequest
│   ├── SignupRequest
│   ├── LoginResponse
│   ├── UserResponse
│   ├── UserUpdateRequest
│   ├── NoticeRequest
│   ├── DiagnosisSubmitRequest
│   ├── DiagnosisResultResponse
│   ├── AiReportResponse
│   ├── PostRequest
│   ├── PostResponse            (attachments: List<AttachmentResponse> 포함)
│   ├── CommentRequest
│   ├── CommentResponse         (attachments: List<AttachmentResponse> 포함)
│   ├── AttachmentResponse
│   ├── CounselingCenterResponse
│   └── ReportRequest
├── external/
│   ├── OpenAiClient            (AI API 호출 클라이언트 틀)
│   └── MapApiClient            (지도/공공데이터 API 호출 클라이언트 틀)
├── repository/
│   ├── UserRepository
│   ├── PostRepository
│   ├── CommentRepository
│   ├── AttachmentRepository
│   ├── NoticeRepository
│   ├── DiagnosisRepository
│   └── ReportRepository
├── service/
│   ├── AuthService
│   ├── UserService
│   ├── CommunityService
│   ├── FileStorageService      (파일 업로드·조회·삭제, 로컬 디스크 저장)
│   ├── NoticeService
│   ├── DiagnosisService
│   └── AiReportService
└── web/
    ├── SessionConst
    └── CurrentUserAdvice
```

---

## 2. 주요 클래스 목록 및 역할

### domain/

| 클래스 | 종류 | 역할 |
|--------|------|------|
| `User` | Entity | 사용자 계정 정보 (이름, 이메일, 비밀번호, 등급) |
| `UserRole` | Enum | 사용자 등급 (USER, COUNSELOR, ADMIN) |
| `Post` | Entity | 커뮤니티 게시글 (제목, 내용, 작성자, 카테고리, 좋아요) |
| `PostComment` | Entity | 게시글 댓글 (내용, 작성자, 연결 게시글) |
| `DiagnosisResult` | Entity | 자가진단 결과 (검사유형, 점수, 등급, 작성자) |
| `Notice` | Entity | 공지사항 (카테고리, 제목, 요약, 본문, 등록일) |
| `Attachment` | Entity | 첨부파일 (원본파일명, 저장파일명, URL, 파일유형, 크기, 대상유형, 대상ID) |
| `Attachment.FileType` | 내부 Enum | 파일 유형 (IMAGE, VIDEO, LINK) |
| `Attachment.TargetType` | 내부 Enum | 첨부 대상 유형 (POST, COMMENT) |
| `Report` | Entity | 신고 (신고자, 대상유형, 대상ID, 사유) |

### repository/

| 인터페이스 | 대상 Entity | 주요 메서드 |
|-----------|------------|------------|
| `UserRepository` | `User` | `findByEmail`, `existsByEmail` |
| `PostRepository` | `Post` | `findAllByOrderByCreatedAtDesc`, `findByCategoryOrderByCreatedAtDesc` |
| `CommentRepository` | `PostComment` | 기본 CRUD |
| `AttachmentRepository` | `Attachment` | `findByTargetTypeAndTargetId`, `deleteByTargetTypeAndTargetId` (`@Modifying @Query`) |
| `NoticeRepository` | `Notice` | `findAllByOrderByCreatedAtDesc` |
| `DiagnosisRepository` | `DiagnosisResult` | `findByUserIdOrderByCreatedAtDesc`, `findByTestTypeOrderByCreatedAtDesc` |
| `ReportRepository` | `Report` | `findByTargetTypeAndTargetId`, `findByReporterIdOrderByCreatedAtDesc` |

### service/

| 클래스 | 주요 메서드 | 설명 |
|--------|-----------|------|
| `AuthService` | `signup`, `login` | 회원가입, 로그인 검증 |
| `UserService` | `signup`, `login`, `findById`, `findAll`, `changeRole`, `updateProfile` | 사용자 관리 전반 |
| `CommunityService` | `findAll`, `findById`, `create`, `updatePost`, `addComment`, `like`, `deletePost`, `deleteComment`, `report` | 게시글/댓글/신고 핵심 로직 + 파일 저장 연동 |
| `FileStorageService` | `saveFiles`, `saveLinks`, `deleteByTarget`, `deleteById`, `findByTarget` | 로컬 디스크 파일 저장·삭제·조회, UUID 파일명 생성, 확장자·크기 검증, URL 링크 저장 |
| `NoticeService` | `findAll`, `findById`, `create`, `update`, `delete` | 공지사항 CRUD |
| `DiagnosisService` | `evaluate`, `findByUser` | 자가진단 점수 계산, 결과 저장, 위험군 분류 |
| `AiReportService` | `generateReport` | 자가진단 결과 기반 AI 추천 메시지 생성 |

### controller/

| 클래스 | URL 매핑 | 주요 엔드포인트 |
|--------|---------|---------------|
| `AuthController` | `/login`, `/logout`, `/signup` | 로그인, 로그아웃, 회원가입 |
| `CommunityController` | `/community/**` | 게시글 CRUD (수정 포함), 댓글 CRUD, 신고, 파일 첨부 처리 |
| `NoticeController` | `/notice/**` | 공지 목록·상세 (전체), 작성·수정·삭제 (ADMIN only) |
| `DiagnosisController` | `/self-assessment/**` | 자가진단 목록, 퀴즈, 결과 |
| `CounselingController` | `/counseling/**` | 상담소 목록, 예약 |
| `UserController` | `/user/**` | 내 계정 조회 (`GET /me`), 수정 폼 (`GET /me/edit`), 수정 처리 (`POST /me/edit`) |
| `PageController` | `/`, `/info`, `/ai-care`, `/recommendations` | 정적 페이지 (공지 제거됨) |

### external/

| 클래스 | 역할 | 상태 |
|--------|------|------|
| `OpenAiClient` | OpenAI API 호출 | 틀만 구현 (API Key 필요) |
| `MapApiClient` | 지도/공공데이터 API 호출 | 더미 데이터 반환 (API Key 필요) |

---

## 3. 주요 필드 상세

### User
```
id          : Long          (PK, AUTO)
name        : String        (NOT NULL, max 50)
email       : String        (NOT NULL, UNIQUE, max 100)
password    : String        (NOT NULL, BCrypt 암호화)
role        : UserRole      (NOT NULL, DEFAULT USER)
createdAt   : LocalDateTime (AUTO)
```

### UserRole (Enum)
```
USER      // 일반 사용자 (회원가입 기본값)
COUNSELOR // 상담사 (DB 또는 관리자 기능으로 변경)
ADMIN     // 관리자 (DB 또는 관리자 기능으로 변경)
```

### Post
```
id        : Long          (PK, AUTO)
author    : String        (NOT NULL, max 50)
title     : String        (NOT NULL, max 200)
content   : String        (NOT NULL, TEXT)
category  : String        (NOT NULL, max 30)
likes     : int           (DEFAULT 0)
createdAt : LocalDateTime (AUTO)
comments  : List<PostComment> (OneToMany, CASCADE ALL)
```

### PostComment
```
id        : Long          (PK, AUTO)
post      : Post          (ManyToOne, LAZY)
author    : String        (NOT NULL, max 50)
content   : String        (NOT NULL, TEXT)
createdAt : LocalDateTime (AUTO)
```

### DiagnosisResult
```
id        : Long          (PK, AUTO)
user      : User          (ManyToOne, LAZY, nullable)
testType  : String        (NOT NULL, max 30) // depression, anxiety, stress, burnout
score     : int           (NOT NULL)
level     : String        (NOT NULL, max 20) // 정상, 경미, 중등도, 심각
createdAt : LocalDateTime (AUTO)
```

### Notice
```
id        : Long          (PK, AUTO)
category  : String        (NOT NULL, max 30)  // 예: 중요, 서비스, 점검
title     : String        (NOT NULL, max 200)
summary   : String        (NOT NULL, max 500)
content   : String        (NOT NULL, TEXT)
createdAt : LocalDateTime (AUTO)
```

### NoticeRequest (DTO)
```
category : String  (@NotBlank, @Size(max=30))
title    : String  (@NotBlank, @Size(max=200))
summary  : String  (@NotBlank, @Size(max=500))
content  : String  (@NotBlank)
```

### Attachment
```
id               : Long                   (PK, AUTO)
originalFileName : String                 (NOT NULL, max 255)   // 원본 파일명 (LINK 타입: URL 값)
storedFileName   : String                 (NOT NULL, max 255)   // 저장 파일명 (UUID.ext) / LINK 타입: ""
fileUrl          : String                 (NOT NULL, max 500)   // /uploads/{storedFileName} 또는 외부 URL
fileType         : Attachment.FileType    (NOT NULL, ENUM: IMAGE, VIDEO, LINK)
fileSize         : long                   // bytes (LINK 타입: 0)
targetType       : Attachment.TargetType  (NOT NULL, ENUM: POST, COMMENT)
targetId         : Long                   (NOT NULL)            // Post.id 또는 PostComment.id
createdAt        : LocalDateTime          (AUTO)
```

### Report
```
id         : Long              (PK, AUTO)
reporter   : User              (ManyToOne, LAZY, NOT NULL)
targetType : Report.TargetType (NOT NULL, ENUM: POST, COMMENT)
targetId   : Long              (NOT NULL)
reason     : String            (NOT NULL, TEXT)
createdAt  : LocalDateTime     (AUTO)
```

---

## 4. 클래스 간 관계 (의존 방향)

```
Controller → Service → Repository → Domain(Entity)

AuthController      → UserService         → UserRepository       → User
CommunityController → CommunityService    → PostRepository       → Post
                                          → CommentRepository    → PostComment
                                          → ReportRepository     → Report
                                          → FileStorageService   → AttachmentRepository → Attachment
                    → FileStorageService  (직접 조회: detail 화면 첨부파일 목록)
NoticeController    → NoticeService       → NoticeRepository     → Notice
                    → UserService         → UserRepository       → User (ADMIN 검사)
DiagnosisController → DiagnosisService    → DiagnosisRepository  → DiagnosisResult
                    → UserService         → UserRepository       → User
UserController      → UserService         → UserRepository       → User
AiReportService     → (OpenAiClient)      (미연결)
CounselingController → (MapApiClient)     (미연결)
```

---

## 5. Entity 간 관계 (ERD 참고)

```
User    1 : N   Post             (Post.author 는 User.name 문자열 참조)
User    1 : N   PostComment      (PostComment.author 는 User.name 문자열 참조)
User    1 : N   DiagnosisResult  (DiagnosisResult.user → User, nullable)
User    1 : N   Report           (Report.reporter → User)
Post    1 : N   PostComment      (PostComment.post → Post, CASCADE ALL)
Post    1 : N   Attachment       (targetType=POST, targetId=Post.id — 외래키 없음, 논리적 관계)
PostComment 1:N Attachment       (targetType=COMMENT, targetId=PostComment.id — 외래키 없음, 논리적 관계)
Post    1 : N   Report           (targetType=POST, targetId=Post.id)
PostComment 1:N Report           (targetType=COMMENT, targetId=PostComment.id)
Notice              (독립 Entity, User와 직접 연관 없음 — 작성자 정보 미저장)
```

> **참고:** `Post`와 `PostComment`의 작성자는 현재 `User.name` 문자열로 저장됩니다.  
> 향후 `User` 엔티티와 외래키로 직접 연결하는 구조로 개선할 수 있습니다.  
> `Notice`는 작성자 정보를 별도로 저장하지 않습니다 — ADMIN 검사는 세션 기반으로 처리합니다.

---

## 6. 내 정보 조회 / 수정 흐름

### 조회 흐름
```
[헤더 "내 정보" 버튼] → GET /user/me
  → UserController.me(session, model)
      → UserService.findById(uid)
          → UserRepository.findById(uid) → User 엔티티
      → new UserResponse(user) → model("user", userResponse)
      → return "user/me"
  → Thymeleaf: templates/user/me.html
      표시 항목: 이름 / 이메일 / 계정 등급 / 가입일

비로그인 접근 시: redirect:/login
```

### 수정 흐름
```
["회원 정보 수정" 버튼] → GET /user/me/edit
  → UserController.editForm(session, model)
      → UserService.findById(uid) → User
      → new UserUpdateRequest(name, email) → model("updateForm", ...)
      → return "user/edit"
  → Thymeleaf: templates/user/edit.html
      수정 가능: 이름, 이메일
      읽기 전용: 등급, 가입일

[저장 버튼] → POST /user/me/edit
  → UserController.edit(@Valid updateForm, bindingResult)
      → 검증 오류 시: "user/edit" 재표시
      → UserService.updateProfile(uid, name, email)
          → 이메일 중복 검사 (본인 제외)
          → user.setName(name); user.setEmail(email);
          → @Transactional 자동 flush → DB 반영
      → 중복 이메일 시: bindingResult 오류 → "user/edit" 재표시
      → 성공 시: redirect:/user/me (flash 메시지 포함)
```

### UserResponse 필드

```
id        : Long          (내부 식별용, 화면 비표시)
name      : String        (이름)
email     : String        (이메일)
role      : UserRole      (계정 등급 - USER/COUNSELOR/ADMIN)
createdAt : LocalDateTime (가입일)
```

### UserUpdateRequest 필드

```
name  : String  (@NotBlank, @Size(max=50))
email : String  (@NotBlank, @Email)
```

### UserRole → 화면 표시 매핑

| enum 값 | 화면 표시 | CSS 클래스 |
|---------|---------|-----------|
| `USER` | 일반 사용자 | `tag` |
| `COUNSELOR` | 상담사 | `tag tag-secondary` |
| `ADMIN` | 관리자 | `tag tag-primary` |

---

## 7. 공지사항 CRUD 및 권한 제어 흐름

### 조회 흐름 (전체 공개)
```
GET /notice      → NoticeController.list()
                     → NoticeService.findAll()
                         → NoticeRepository.findAllByOrderByCreatedAtDesc()
                     → model("notices", List<Notice>)
                     → return "notice"
                 → Thymeleaf: notice.html
                     → ADMIN 로그인 시: "공지 작성" 버튼 렌더링
                     → 비로그인·일반 사용자: 버튼 미표시

GET /notice/{id} → NoticeController.detail()
                 → Thymeleaf: notice-detail.html
                     → ADMIN 로그인 시: 수정/삭제 버튼 렌더링
```

### 작성 흐름 (ADMIN only)
```
["공지 작성" 버튼] → GET /notice/new
  → NoticeController.newForm()
      → isAdmin(session) 검사
          → false: redirect:/notice (flash: "관리자만 공지를 작성할 수 있습니다.")
          → true: model("noticeForm", new NoticeRequest())
      → return "notice-form"

[등록 버튼] → POST /notice
  → NoticeController.create(@Valid form, bindingResult)
      → isAdmin 재검사
      → 검증 오류 시: "notice-form" 재표시
      → NoticeService.create(form) → NoticeRepository.save(notice)
      → redirect:/notice/{id} (flash: "공지가 등록되었습니다.")
```

### 수정 흐름 (ADMIN only)
```
[수정 버튼] → GET /notice/{id}/edit
  → NoticeController.editForm()
      → isAdmin 검사 → false: redirect:/notice/{id}
      → NoticeService.findById(id) → 기존 데이터 폼에 채움
      → return "notice-form" (isEdit=true)

[수정 완료 버튼] → POST /notice/{id}/edit
  → NoticeController.edit()
      → NoticeService.update(id, form)
          → notice.set*(…) → @Transactional flush → DB 반영
      → redirect:/notice/{id}
```

### 삭제 흐름 (ADMIN only)
```
[삭제 버튼] → POST /notice/{id}/delete
  → NoticeController.delete()
      → isAdmin 검사 → false: redirect:/notice/{id}
      → NoticeService.delete(id) → NoticeRepository.deleteById(id)
      → redirect:/notice
```

### isAdmin 헬퍼 (NoticeController 내부)
```java
private boolean isAdmin(HttpSession session) {
    Object userId = session.getAttribute(LOGIN_USER_ID);
    if (!(userId instanceof Long uid)) return false;
    return userService.findById(uid)
        .map(u -> u.getRole() == UserRole.ADMIN)
        .orElse(false);
}
```

---

## 8. 클래스 다이어그램 작성 시 참고사항

- `UserRole`은 `User`에 `@Enumerated(EnumType.STRING)`으로 매핑
- `User` 1 : 1 `UserResponse` 관계 (생성자 변환: `new UserResponse(user)`)
- `UserUpdateRequest`는 `UserController`에서만 사용되는 수정 요청 DTO
- `UserService.updateProfile()`은 `@Transactional` — flush 시 JPA가 자동으로 UPDATE 쿼리 실행
- `CurrentUserAdvice`는 `@ControllerAdvice`로 모든 뷰에 `loginUser`(User 엔티티) 주입 → 헤더 "내 정보" 버튼 표시 여부 제어
- "내 정보" 버튼은 `layout.html` 헤더에서 `th:if="${loginUser != null}"` 조건으로 표시
- 비밀번호 변경 기능은 미구현 → 추후 `UserUpdateRequest`에 `currentPassword`, `newPassword` 필드 추가로 확장 가능
- `Report.TargetType`은 `Report` 내부 중첩 enum
- `PostCommentRepository`는 `@NoRepositoryBean`으로 마킹된 레거시 별칭 → 다이어그램에서 생략 가능
- `PostService`는 `CommunityService`를 상속하는 deprecated 래퍼 → 다이어그램에서 생략 가능
- `LoginForm`, `SignupForm`은 각각 `LoginRequest`, `SignupRequest`를 상속하는 deprecated 클래스 → 다이어그램에서 생략 가능
- `DiagnosisController`가 `@RequestMapping("/self-assessment")`를 사용 (프론트 URL 호환성 유지)
- `AuthService`와 `UserService` 모두 회원가입/로그인 기능을 가짐 (`AuthController`는 `UserService` 사용)
- `Notice`는 독립 Entity — `User`와 외래키 없이 세션 기반 ADMIN 검사로만 권한 제어
- `PageController`에서 `/notice`, `/notice/{id}`가 제거됨 — `NoticeController`로 이관
- 공지 ADMIN 버튼 표시 조건: Thymeleaf `th:if="${loginUser != null and loginUser.role.name() == 'ADMIN'}"`
- `Attachment`는 `Post`, `PostComment`와 JPA 외래키 관계 없음 — `targetType` + `targetId`로 논리적 연결 (단일 테이블로 게시글·댓글 첨부파일 모두 관리)
- `FileStorageService`는 파일 저장 경로를 `System.getProperty("user.dir")/uploads/`로 결정 — 실행 디렉토리 기준 절대 경로
- `AppConfig`에 `WebMvcConfigurer` 빈이 `/uploads/**` → 로컬 `uploads/` 폴더 매핑 (정적 리소스 서빙)
- 파일 업로드 흐름: 프론트 `<input type="file" name="files">` → `CommunityController` (`MultipartFile[] files`) → `CommunityService.create()/addComment()/updatePost()` → `FileStorageService.saveFiles()` → 디스크 저장 + `AttachmentRepository.save()` → `fileUrl=/uploads/{uuid}.{ext}`
- URL 첨부 흐름: 프론트 `<input type="text" name="linkUrls">` → `CommunityController` (`String[] linkUrls`) → `CommunityService` → `FileStorageService.saveLinks()` → `AttachmentRepository.save()` (fileType=LINK, storedFileName="", fileUrl=입력한 URL)
- LINK 타입 `Attachment`는 디스크 파일 없음 — `deleteByTarget()`/`deleteById()` 호출 시 `storedFileName`이 빈 문자열이면 파일 삭제 건너뜀
- `AttachmentResponse`에 `isYouTube()`, `getYouTubeEmbedUrl()` 메서드 — Thymeleaf `th:if="${a.youTube}"` 조건으로 YouTube embed `<iframe>` 또는 링크 카드 분기 표시
- YouTube URL 패턴: `youtube.com/watch?v={ID}` 또는 `youtu.be/{ID}` → embed URL: `https://www.youtube.com/embed/{ID}`
- `detail.html`의 `renderYoutubeInContent(elem)` JS 함수: 본문/댓글 `<p>` 텍스트에서 YouTube URL 위치를 정규식으로 파악 → URL 이전/이후 텍스트를 `<span>`으로, YouTube URL 자체는 `<iframe>`으로 DOM 재구성 (URL 위치 순서 보장, 텍스트 노출 없음)
  - 보안: `youtube.com` / `youtu.be` 도메인만 iframe 변환 허용, 기타 URL은 텍스트 그대로 유지
  - YouTube URL이 없는 경우 DOM 변경 없이 원본 `th:text` 렌더링 유지
  - 게시글 본문: `id="postContent"` `<p>` 단일 구조 (별도 embed 컨테이너 없음)
  - 댓글 본문: `class="comment-content"` `<p>` → JS로 querySelectorAll 일괄 처리
- URL 첨부 입력 칸 제거: `new.html` / `edit.html` / `detail.html` 댓글 폼에서 별도 URL 입력 섹션 및 관련 JS(`addLink`, `removeLink`, `previewLinks`, `addCommentLink`, `removeCommentLink`) 전부 제거
- Oracle DB 연동 SQL 문서: `docs/LoginCommunity.md` 참고 (users / posts / post_comments / reports / notices / attachments CREATE TABLE, DROP 순서, 테스트 INSERT, application.properties 예시 포함)
- 게시글/댓글 삭제 시 `FileStorageService.deleteByTarget()`을 먼저 호출해 파일 정리 후 Entity 삭제
- `detail()` 컨트롤러에서 `postAttachments` (List) 와 `commentAttachments` (Map&lt;Long, List&gt;)를 모델에 추가 → Thymeleaf에서 `${postAttachments}`, `${commentAttachments[c.id]}`로 접근
