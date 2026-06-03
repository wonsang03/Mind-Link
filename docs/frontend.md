# 프론트엔드

서버 사이드 렌더링(Thymeleaf) 기반 MVC + 일부 화면의 클라이언트 JS(fetch, Three.js). 정적 자원은 `src/main/resources/static/`, 화면은 `src/main/resources/templates/`.

## 레이아웃 프래그먼트

| 프래그먼트 | 용도 |
|------------|------|
| `fragments/layout.html` | 일반 사용자 공통 헤더/푸터/head. `header`, `footer`, `head(title)` 프래그먼트 제공 |
| `fragments/admin-layout.html` | 관리자(`/admin/**`) 전용 레이아웃·사이드바 |
| `fragments/counselor-layout.html` | 상담사(`/counselor/**`) 전용 레이아웃 |

> `CurrentUserAdvice`(`@ControllerAdvice`)가 모든 뷰에 `loginUser`, `currentUri`, `unreadAlertCount`를 자동 주입 → 헤더 로그인 상태·알림 배지에 사용.

## 정적 자원

| 파일 | 용도 |
|------|------|
| `css/style.css` | 전역 스타일(사용자 화면) |
| `css/admin.css` | 관리자 화면 |
| `css/cluster-viz.css` | 3D 클러스터 맵 |
| `css/activities.css` | 추천 활동 화면 |
| `js/cluster-viz-three.js` | Three.js 3D 산점도 뷰어(importmap CDN으로 three 로드). `/api/chat-cluster/*` 호출 |
| `js/activities.js` | 추천 활동 실행·로그(`POST /api/activities`) |

## 화면 맵 (경로 → 템플릿 → 컨트롤러)

### 공통·인증
| 경로 | 템플릿 | 컨트롤러 |
|------|--------|----------|
| `/` 홈(명언+공지5+게시글5 미리보기) | `home.html` | `PageController` |
| `/info` → `/#service-intro` 리다이렉트 | — | `PageController` |
| `/login`, `/signup`, `/logout` | `login.html`, `signup.html` | `AuthController` |

### 자가진단
| 경로 | 템플릿 | 컨트롤러 |
|------|--------|----------|
| `/self-assessment` 목록 | `self-assessment/list.html` | `SelfAssessmentController` |
| `/self-assessment/{typeKey}` 검사 | `self-assessment/quiz.html` | 〃 |
| `POST /self-assessment/{typeKey}/result` 결과 | `self-assessment/result.html` | 〃 |

> 결과 페이지에는 로그인+군집 프로필이 있을 때 **"내 정서 유형" 카드**(`clusterType`: 유형명·설명·또래 페르소나 칩·또래 수·추천 링크)가 노출된다.

### 커뮤니티
| 경로 | 템플릿 | 컨트롤러 |
|------|--------|----------|
| `/community` 목록(카테고리·검색·추천 카테고리 상단) | `community/list.html` | `CommunityController` |
| `/community/{id}` 상세(댓글·답글·좋아요·신고) | `community/detail.html` | 〃 |
| `/community/new`, `/community/{id}/edit` | `community/new.html`, `community/edit.html` | 〃 |
| `/community/cluster-viz` → `/admin/cluster` 리다이렉트(일반 노출 차단) | `community/cluster-viz.html`(현재 미사용) | `ChatClusterVizController` |

### 공지
| `/notice`, `/notice/{id}`, `/notice/new`, `/notice/{id}/edit` | `notice.html`, `notice-detail.html`, `notice-form.html` | `NoticeController`(작성·수정·삭제는 ADMIN) |

### 상담소·예약
| `/counseling` 검색 | `counseling/list.html` | `CounselingController` |
| `/counseling/booking` 예약 | `counseling/booking.html`, `counseling/booking-complete.html` | 〃 |
| `/counseling/bookings/{id}` | (상세) | 〃 |

### AI 위로 편지
| `/care-report`, `/care-report/wizard` 위저드 | `care-report/wizard.html` | `CareReportPageController` |
| `/care-report/list`, `/care-report/{id}` | `care-report/list.html`, `care-report/detail.html` | 〃 |
| `/ai-care` → `/care-report/wizard` 리다이렉트 | (`ai-care.html`은 레거시) | `PageController` |

### 추천·활동
| `/recommendations` 맞춤 추천 | `recommendations.html` | `PageController` |
| `/activities/{key}` 활동 실행 | `activity/run.html` | `ActivityController` |
| `/activities/history` 이력 | `activity/history.html` | 〃 |

### 내 정보·알림
| `/user/me`, `/user/me/edit` | `user/me.html`, `user/edit.html` | `UserController` |
| `/alerts` 알림함(읽음·삭제) | `alerts.html` | `AlertController` |

### 관리자 (`/admin/**`, ADMIN)
| 화면 | 템플릿 |
|------|--------|
| 대시보드 | `admin/dashboard.html` |
| 사용자 목록·상세 | `admin/users.html`, `admin/user-detail.html` |
| 게시글 관리 | `admin/posts.html`, `admin/post-new.html`, `admin/post-edit.html` |
| 공지 관리 | `admin/notices.html`, `admin/notice-form.html` |
| 알림 발송 | `admin/alerts.html` |
| 모니터링 | `admin/monitoring.html` |
| SQL 콘솔 | `admin/sql.html` |
| 로그 뷰어(SSE) | `admin/logs.html` |
| 3D 클러스터 맵 | `admin/cluster-viz.html` |

### 상담사 (`/counselor/**`, COUNSELOR)
| 화면 | 템플릿 |
|------|--------|
| 대시보드 | `counselor/dashboard.html` |
| 게시글 관리 | `counselor/posts.html` |
| 고위험 사용자 | `counselor/high-risk.html` |
| 알림 발송 | `counselor/alerts.html` |

### 오류 페이지
`error/400·403·404·405·500.html`, `error/error.html`.

## 클라이언트 동작 메모

- **3D 클러스터 맵**(`cluster-viz-three.js`): importmap으로 `three@0.170.0` CDN 로드. `/api/chat-cluster/visualization`(점), `/my`(내 위치), `/search`(실사용자 검색, ADMIN 화면), `/recompute`(ADMIN) 호출. 드래그 회전·휠 줌·검색 강조.
- **추천 활동**(`activities.js`): 활동 수행 시 `POST /api/activities`로 로그 전송(비로그인은 204로 무시).
- **알림 배지**: 현재 `CurrentUserAdvice.unreadAlertCount`로 **페이지 로드 시점** 표시(폴링 미적용).
