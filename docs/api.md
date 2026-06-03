# API

REST(JSON) 엔드포인트 명세. 출처는 `@RestController` 클래스(`com.mindlink.**`)이며, 페이지(HTML) 라우트는 [frontend.md](frontend.md)를 참고하세요.

## 인증 규칙

- 세션 기반: 로그인 시 `session[SessionConst.LOGIN_USER_ID] = userId(Long)`.
- Spring Security는 전 경로 `permitAll` + CSRF 비활성 — 인증·권한은 각 컨트롤러가 세션으로 직접 확인.
- 비로그인 시 응답은 엔드포인트별로 상이(`401` / `204 No Content` / `{count:0}` 등) — 아래 표에 표기.

---

## 추천 (`/api/recommendations`) — `RecommendationController`

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/recommendations?emotion={E}&size={n}` | 불필요 | 감정별 DB 도서 조회 + Gemini 위로 멘트. `emotion` 기본 `NORMAL`, `size` 1~20(기본 5). DB 없으면 `source:"EMPTY"` |
| POST | `/api/recommendations/personalize` | 세션(선택) | **DB만 사용**(네이버 호출 없음). body `{message, emotion?}`. 키워드 기반 복수 감정 탐지 → 슬롯별 3권. 세션 ISBN 이력으로 중복 제거 |
| POST | `/api/recommendations/ai` | 세션(선택) | **3단계 파이프라인**(Gemini 분석 → DB+네이버 후보 수집 → Gemini 3권 확정). body `{message}`. 확정 도서는 비동기로 DB 캐시 |

응답 공통: `RecommendationResponse { emotion, comfortMessage, books[], source, ... }`.

## 상담소·예약 (`/api/counseling`) — `CounselingApiController`

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/counseling/centers?query={q}` | 불필요 | 네이버 지역검색으로 상담소 검색(키 미설정 시 더미). `CenterResponse[]` |
| POST | `/api/counseling/bookings` | 세션 | 예약 생성. `@Valid BookingRequest`(JSON) |
| GET | `/api/counseling/bookings/{id}` | 세션 | 예약 단건 조회(본인) |
| GET | `/api/counseling/bookings/me` | 세션 | 내 예약 목록 |
| POST | `/api/counseling/bookings/{id}/cancel` | 세션 | 예약 취소(`status=CANCELLED`) |

## 도서 리뷰 (`/api/reviews`) — `BookReviewController`

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/reviews?bookLink={url}` | 선택 | 해당 도서 리뷰 목록 + (로그인 시) 내 리뷰. `{reviews[], myReview}` |
| POST | `/api/reviews` | 세션(401) | upsert. body `{bookLink, bookTitle, rating(1~5), content}` |
| DELETE | `/api/reviews/{id}` | 세션 | 내 리뷰 삭제 |

## 추천 활동 (`/api/activities`) — `ActivityApiController`

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/activities` | 세션(204) | 활동 수행 로그 기록. body `{activityKey, payload?, moodScore?, durationSec?, programKey?}`. 잘못된 키는 400 |

## 정서 클러스터링 (`/api/chat-cluster`) — `ChatClusterApiController`

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/chat-cluster/visualization?includeSynthetic&includeReal&highlightCluster` | 불필요 | 3D 산점도용 점·centroid·축·가중치·통계 JSON |
| GET | `/api/chat-cluster/search?q={name}` | 불필요 | 실사용자 이름·라벨·이메일 부분검색(3D 맵 찾기) |
| GET | `/api/chat-cluster/my` | 세션(401) | 내 점·군집·또래·**동적 정서 유형**. `{hasProfile, me, clusterId, clusterMemberCount, samePersonaLabels[], clusterLabel, clusterDescription, summary}` |
| POST | `/api/chat-cluster/profile` | 세션(401) | 내 3축 점수 저장. body `{stressScore, depressionScore, anxietyScore}` → 가장 가까운 centroid로 cluster 배정 |
| POST | `/api/chat-cluster/recompute` | **ADMIN**(403) | 전체 K-Means 재계산 |

> `clusterLabel`/`clusterDescription`은 군집 평균좌표 기반으로 동적 분류(안정·회복 / 스트레스·우울·불안 우세 / 복합·소진형)되어, 재계산으로 cluster_id 의미가 바뀌어도 라벨이 유지됩니다.

## AI 위로 편지 (`/api/care-reports`) — `CareReportApiController`

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/api/care-reports` | 세션 | 내 보고서 요약 목록 (`SummaryResponse[]`) |
| GET | `/api/care-reports/{id}` | 세션 | 보고서 상세 (`DetailResponse`) |
| POST | `/api/care-reports/generate` | 세션 | 편지 생성. body `GenerateRequest{mood, recentHardship, concern, smallComfort, hopeForward, oneLineMessage?, assessments}`. 24h 내 `care-report.daily-limit`(기본 3) 초과 시 제한 |
| GET | `/api/care-reports/{id}/pdf` | 세션 | 보고서 PDF 다운로드 (`mindlink-letter-{yyyyMMdd}-{id}.pdf`) |

## 관리자 로그 (`/admin/logs`) — `AdminLogController`

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| GET | `/admin/logs/recent` | ADMIN | 최근 로그 JSON |
| GET | `/admin/logs/stream` | ADMIN | 실시간 로그 **SSE**(`text/event-stream`) |

---

## 페이지(MVC) 라우트 요약

HTML을 반환하는 컨트롤러(`@Controller`)는 [frontend.md](frontend.md)의 화면 맵을 참고하세요. 주요 prefix:
`/`(홈) · `/login` `/signup` `/logout` · `/self-assessment/**` · `/community/**` · `/notice/**` · `/counseling/**` · `/care-report/**` · `/activities/**` · `/recommendations` · `/alerts` · `/user/**` · `/admin/**`(ADMIN) · `/counselor/**`(COUNSELOR).
