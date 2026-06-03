# Mind-Link — 정서 프로필·군집 맞춤 기능 핸드오프

Claude(또는 다른 AI)에게 **설계 + 구현**을 맡길 때 이 파일의 **「복사용 프롬프트」** 와 `@` 첨부 파일을 함께 전달하세요.

---

## 작업 정리 (한눈에)

### 목표

**정서 프로필 기반 맞춤 케어** — 자가진단 3축으로 개인 맞춤은 유지하고, **K-Means 군집(`clusterId`)** 을 커뮤니티·도서·관리자에 **실제 연결**한다. 페르소나 210명은 **3D 시각화만**.

### 하지 않는 것

- A/B·노출 로그 테이블
- `posts.user_id` FK 마이그레이션
- care 편지·자가진단 채점 전면 수정
- 「프로필만으로 AI 도서 정확 매칭」

### 작업 목록

| ID | 우선 | 작업 | 산출물 | 건드릴 곳(대략) |
|----|------|------|--------|----------------|
| **C-1** | P0 | 같은 `cluster_id` **실사용자** 인기 게시글 Top 3~5, 커뮤니티 상단 노출 | Service, `community/list.html` | `CommunityController`, 신규 `ClusterContentService` 등 |
| **E-1** | P0 | 관리자 **실사용자만** cluster별 인원·평균 S/D/A | API + admin UI | `ChatClusterApiController`, `admin/cluster` 또는 dashboard |
| **C-2** | P1 | AI 도서 후보에 **cluster 인기 ISBN** merge | `RecommendationService` | `recommendation/service` |
| **A** | P2 | 문구·README 「검사 결과 기반 맞춤」 통일 | 텍스트 | `README`, `community/list`, `recommendations` |
| **E-2** | P2 | 설계서 + 한계·향후 | `docs/PERSONALIZATION_DESIGN.md` | docs 신규 |
| **F** | P2 | 용어 표 (설계서 §8) | 표 1개 | 위 설계서 |

### 유지 (이미 됨 · 손대지 말 것)

| 기능 | 방식 |
|------|------|
| 커뮤니티 맞춤 | 3축 norm → 카테고리·가중 정렬 (`CommunityCategoryPreferenceService`) |
| 추천 첫 화면 | 우세 감정 → DB 도서·활동 (`PageController` + `recommendations.html`) |
| AI 도서 | 문장 → Gemini (`POST /api/recommendations/ai`) |
| 3D·유형 라벨 | K-Means + 페르소나 (`/admin/cluster`) |

### 완료 기준 (체크)

- [ ] 로그인+프로필 있으면 커뮤니티에 「유형 인기 글」 보임
- [ ] 관리자에서 페르소나 제외 cluster 통계 보임
- [ ] (P1) AI 추천 후보에 cluster ISBN 반영
- [ ] `PERSONALIZATION_DESIGN.md` + 용어 표
- [ ] `./mvnw compile` 통과

### C-1 시 주의

`posts`에 `user_id` 없음 → `author` = `users.name` 조인으로 작성자↔프로필 연결, 불가 시 설계서에 한계 기록.

### 실행 순서

```
E-1 + C-1 (병렬 가능) → C-2 → A + PERSONALIZATION_DESIGN.md
```

---

## 복사용 프롬프트 (Claude에 붙여넣기)

```
당신은 Mind-Link(마음이음) Spring Boot 프로젝트의 시니어 백엔드·설계 담당입니다.
목표: 「정서 프로필 기반 맞춤 케어」를 강화하고, K-Means 군집을 **콘텐츠 노출**과 **관리자 통계**에 실제로 연결합니다.

## 반드시 읽을 파일 (@ 첨부)
- README.md
- docs/backend.md
- sql/README.md
- docs/HANDOFF_CLUSTER_PERSONALIZATION.md (이 파일)
- com.mindlink.service.CommunityCategoryPreferenceService
- com.mindlink.chatcluster.* (ClusterProfileService, ClusterKMeansEngine, UserAssessmentProfile)
- com.mindlink.controller.CommunityController
- com.mindlink.recommendation.service.RecommendationService
- com.mindlink.controller.PageController (/recommendations 기본 감정)
- templates/community/list.html
- templates/recommendations.html
- templates/admin/cluster-viz.html, AdminController

## 확정 설계 방침 (변경 금지)
1. **대외 명칭**: 「정서 프로필 기반 맞춤 케어」. 「채팅 클러스터링 AI 추천 엔진」같은 과장 금지.
2. **개인화 범위**: 커뮤니티만이 아니라 **플랫폼 전체** — 자가진단→프로필→커뮤니티·추천·알림·케어편지·관리 3D.
3. **도서 추천 2단계 (버그 아님, 유지)**  
   - ① **초기 노출**: 로그인 사용자 `resolveDominantEmotion` → GET `/api/recommendations?emotion=` → DB `recommendation_books` + 활동 RANK (프로필 기반).  
   - ② **정밀 추천**: POST `/api/recommendations/ai` — **사용자 문장** + Gemini + 네이버 후보. 프로필만으로 도서 정확 매칭 시도하지 말 것.
4. **페르소나 210명**: `is_synthetic=1` — **3D·K-Means 시각화·알고리즘 데모 전용**. 추천·집단 통계·보고서 수치에는 **실사용자(user_id NOT NULL)** 만 사용.
5. **이번 스코프 밖**: A/B 테스트, impression 로그 테이블, 대규모 리팩터, care 편지 파이프라인 변경, posts.user_id FK 마이그레이션(별도 과제).

## 구현 우선순위

### P0 — C-1: 동일 군집 인기 게시글 (커뮤니티)
- 로그인 + `user_assessment_profiles.cluster_id` 있을 때
- **같은 cluster_id** 를 가진 **실사용자**가 작성한 게시글(또는 조회 가능한 범위) 중 인기 Top 3~5
- `community/list.html` 상단 블록: 「{clusterLabel} 유형에서 많이 본 글」
- **주의**: `posts` 테이블은 `author`(문자열)만 있고 `user_id` FK 없음 → `users.name` 등과 매칭하거나, 설계서에 한계 명시. 불가 시 시드/데모용 fallback 문서화.

### P0 — E-1: 관리자 실사용자 cluster 통계
- `/admin` 또는 `/admin/cluster` 에 **실사용자만** 집계: cluster별 인원, 평균 stress/depression/anxiety norm
- 페르소나 제외 (`user_id IS NULL` 또는 `is_synthetic=1` 제외)
- API: `GET /api/chat-cluster/stats/real` (이름은 프로젝트 컨벤션에 맞게)

### P1 — C-2: cluster 인기 ISBN → AI 도서 후보
- `RecommendationService` AI 2단계(DB+네이버 후보 수집) 시
- 동일 `cluster_id` 실사용자들과 연관된 `recommendation_books` 또는 최근 AI 추천 ISBN 빈도 상위 N건을 후보에 merge
- 기존 문장 기반 Gemini 흐름은 유지

### P2 — A: 명칭·문구 정리
- README, community/list, recommendations 상단 문구를 「검사 결과 기반 맞춤」에 맞게 통일
- 금지 예: 「실시간 AI 클러스터 추천」

### P2 — E-2 + F: 산출 문서
- `docs/PERSONALIZATION_DESIGN.md` 신규 작성 (아래 목차 준수)
- **용어 표** 1개 포함 (대외 용어 / 코드 / 사용자 체감 / 비고)
- **한계 및 향후**: 실사용자 표본 제한, 페르소나=시각화, 집단 협업 추천은 파일럿 후

## PERSONALIZATION_DESIGN.md 목차
1. 개요·목적 (정서 프로필 기반 맞춤 케어)
2. 데이터 흐름 (자가진단 → UserAssessmentProfile → clusterId)
3. 플랫폼별 개인화 표 (커뮤니티 / 추천 / 알림 / 편지 / 관리자)
4. 도서 추천 2단계 설계 (초기 vs AI)
5. 군집(K-Means) 역할 vs 3축 규칙 역할 (역할 분리 다이어그램)
6. C-1·C-2·E-1 상세 (시퀀스·API·쿼리 의사코드)
7. 페르소나 vs 실사용자 정책
8. 용어 표
9. 한계 및 향후 과제

## 기술 제약
- Java 17, Spring Boot, JPA, Oracle, `ddl-auto=none` — 스키마 변경 시 sql/02_features 에 멱등 SQL 추가
- DB 실행 계정: APP_USER (sql/README.md)
- 서버 포트 8081
- 기존 `CommunityCategoryPreferenceService` 3축 규칙 **유지** (cluster는 **추가** 레이어)
- 컴파일: `./mvnw compile` 통과

## 산출물 체크리스트
- [ ] C-1 UI + Service + (필요 시) Repository 쿼리
- [ ] E-1 관리자 통계 UI 또는 API
- [ ] C-2 RecommendationService 후보 merge (가능 시)
- [ ] docs/PERSONALIZATION_DESIGN.md
- [ ] README 한 줄 목적 문구 갱신 (선택)
- [ ] 변경 요약(한국어) 10줄 이내

## 작업 후 보고 형식
1. 변경 파일 목록
2. C-1/E-1/C-2 각각 동작 설명 3문장
3. posts.author 한계 및 우회 방법
4. 테스트 시나리오 5개 (로그인/비로그인/프로필 없음/프로필 있음/관리자)
```

---

## 용어 표 (보고서·설계서 공통)

| 대외 용어 | 코드·데이터 | 사용자 체감 | 비고 |
|-----------|-------------|-------------|------|
| 정서 프로필 | `UserAssessmentProfile`, S/D/A norm | 맞춤 카테고리·첫 추천 | **개인화 핵심** |
| 맞춤 케어 | 알림·편지·노출 전반 | 나에게 맞는 글·책·안내 | 플랫폼 통칭 |
| 정서 군집 | `clusterId`, K-Means | 유형명·3D | 집단·시각화 |
| 페르소나 | `is_synthetic`, user_id null | 3D 점 분포 | **시각화만** |
| 초기 도서 추천 | `GET /api/recommendations?emotion=` | 페이지 진입 시 3권 | 프로필 우세 축 |
| AI 도서 추천 | `POST /api/recommendations/ai` | 문장 입력 후 | 문장 기반 |
| 유형 인기 글 | C-1 (구현) | 「비슷한 유형에서 본 글」 | cluster 기반 |

---

## 현재 코드 스냅샷 (2026-05 기준)

| 기능 | 구현 | clusterId 사용 |
|------|------|----------------|
| 3축 → 커뮤니티 카테고리·정렬 | ✅ | ❌ (norm 규칙) |
| 추천 페이지 기본 감정 | ✅ | ❌ |
| AI 도서 | ✅ 문장 | ❌ |
| K-Means + 3D | ✅ | 시각화 |
| 자가진단 결과 유형 | ✅ | 라벨 |
| 동일 cluster 인기글 | ❌ | P0 |
| cluster → AI ISBN | ❌ | P1 |
| 실사용자 cluster 통계 | ❌ | P0 |

---

## posts.author 이슈 (C-1 필수 인지)

`Post` 엔티티에 **`user_id` 없음**, `author` 문자열만 존재.  
C-1 구현 시: `users.name` = `posts.author` 조인, 또는 당분간 **전체 인기글 + cluster 필터 불가** 시 설계서에 명시.

---

## 관련 문서

- [AGENT_PROMPT_REPORT.md](AGENT_PROMPT_REPORT.md) — 팀플 보고서 작성용
- [backend.md](backend.md) — 서버 전체
