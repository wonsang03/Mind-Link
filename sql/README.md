# Mind-Link · Oracle SQL 실행 가이드

**실행 계정**: 반드시 **`APP_USER`** (= `.env`의 `DB_USERNAME`)로 접속해 실행하세요.
`SYSTEM` 등 다른 계정으로 돌리면 앱이 보는 스키마에 반영되지 않아 `ORA-00904` 같은 오류가 납니다.

**전제**: JPA는 `ddl-auto=none` — 테이블·데이터는 아래 스크립트를 SQL Developer / SQL\*Plus에서 **수동 실행**합니다. 빈 스키마여도 `01_schema/ORACLE_SETUP.sql` 섹션 0이 기본 테이블을 생성합니다.

## 0. 서버 띄우기 전 — DB 초기화·전체 설치 (권장)

개발 DB를 **처음부터** 맞추거나 스키마가 꼬였을 때 (`ORA-00904` 등):

| 목적 | 파일 | 설명 |
|------|------|------|
| **초기화 + 재설치 한 번에** | `00_FRESH_INSTALL.sql` | DROP 전체 테이블 → 8단계 설치 + 명언 시드 |
| 테이블만 삭제 | `00_RESET_ALL.sql` | 데이터·테이블 전부 DROP |
| 설치만 (테이블 있으면 멱등 스킵) | `00_INSTALL_ALL.sql` | 8단계 순차 `@@` 호출 |

```sql
-- SQL Developer / SQL*Plus — 반드시 APP_USER 로 sql/ 폴더에서
SQL> @00_FRESH_INSTALL.sql
```

실행 후 **Spring Boot 재시작** → `http://localhost:8081` (`.env`의 `DB_*`와 동일 계정).

## 폴더 구조

```
sql/
├── README.md                              # 이 파일 — 유일한 실행 가이드
├── 00_FRESH_INSTALL.sql                   # RESET + INSTALL 원샷
├── 00_RESET_ALL.sql                       # 앱 테이블 전부 DROP (개발용)
├── 00_INSTALL_ALL.sql                     # 원샷 설치 (필수 8개 + PROVERBS)
├── 01_schema/
│   └── ORACLE_SETUP.sql                   # DDL + 추천도서 시드 (회원·커뮤니티 시드 없음)
├── 02_features/
│   ├── USERS_PROFILE.sql                  # users 프로필 컬럼 (멱등 ALTER)
│   ├── PRIVACY_CONSENT.sql                # sensitive_data_consent (민감정보 동의)
│   ├── ASSESSMENT_SEED.sql                # 자가진단 문항·점수 구간
│   ├── MONITORING.sql                     # assessment_results + user_alerts(전체) + post_comments.parent_comment_id
│   ├── CARE_REPORT.sql                    # AI 위로 편지
│   ├── ACTIVITY_LOG.sql                   # 추천 활동 수행 기록
│   ├── CHAT_CLUSTERING.sql                # 정서 클러스터 + 210 페르소나
│   └── CLEAR_RUNTIME_DATA.sql             # 공지·커뮤니티·회원 DELETE
├── 03_optional/
│   └── PROVERBS_SEED.sql                  # (선택) 명언·속담 데이터
└── archive/                               # 평소 실행 안 함 (마이그레이션·복구·진단 전용)
    ├── MONITORING_FIX.sql
    ├── MONITORING_REBUILD.sql
    ├── COMMUNITY_CATEGORY_MIGRATE.sql
    └── dev/
        └── USER_ALERTS_VERIFY.sql         # (선택) user_alerts 컬럼·접속 계정 확인 쿼리
```

> 알림(`user_alerts`)의 모든 컬럼·제약은 **`MONITORING.sql` 하나**에 포함되어 있습니다. 별도 패치 파일은 없습니다.

## 1. 신규 설치

### 가장 쉬운 방법 — 초기화 후 전체 설치 (권장)

`sql/` 폴더에서 **APP_USER** 로:

```sql
SQL> @00_FRESH_INSTALL.sql
```

이미 빈 DB이거나 테이블만 추가하면:

```sql
SQL> @00_INSTALL_ALL.sql
```

> `@@` 상대경로이므로 `sql/` 폴더(또는 `@C:\...\sql\00_FRESH_INSTALL.sql` 절대경로) 기준으로 실행하세요. `00_INSTALL_ALL.sql` 은 **PRIVACY_CONSENT · ACTIVITY_LOG · PROVERBS** 까지 포함합니다.

### 개별 실행 (순서대로)

| 순서 | 파일 | 필수 | 비고 |
|------|------|------|------|
| 1 | `01_schema/ORACLE_SETUP.sql` | ✅ | users · posts · post_comments · attachments · reports · notices · bookings · book_reviews · recommendation_books DDL + 관리자 계정 · 추천도서 시드 (커뮤니티 더미 없음) |
| 2 | `02_features/USERS_PROFILE.sql` | ✅ | 내 정보 컬럼: nickname, region, phone, notification_enabled, profile_image_url |
| 3 | `02_features/PRIVACY_CONSENT.sql` | ✅ | `users.sensitive_data_consent` (자가진단 민감정보 동의) |
| 4 | `02_features/ASSESSMENT_SEED.sql` | ✅ | 자가진단(PHQ-9 / GAD-7 / PSS-10 / CBI) 문항·점수 구간 |
| 5 | `02_features/MONITORING.sql` | ✅ | 검사 이력(`assessment_results`) · 알림(`user_alerts` 전체 컬럼·9종 CHECK) · 댓글 답글(`post_comments.parent_comment_id`) |
| 6 | `02_features/CARE_REPORT.sql` | ✅ | AI 종합 보고서(`care_reports`) — `care_daily_inputs`는 legacy 호환 DDL |
| 7 | `02_features/ACTIVITY_LOG.sql` | ✅ | 추천 활동(`activity_log`) |
| 8 | `02_features/CHAT_CLUSTERING.sql` | 클러스터 사용 시 | `user_assessment_profiles` + 210 페르소나 시드 |
| (선택) | `03_optional/PROVERBS_SEED.sql` | 선택 | `proverbs` 테이블·명언 시드 |
| 9 | `02_features/CLEAR_RUNTIME_DATA.sql` | ✅ (INSTALL 끝) | 공지·커뮤니티·회원·이력 삭제 |

> 요약: **01 → … → CHAT_CLUSTERING → PROVERBS → `CLEAR_RUNTIME_DATA`**.
> `00_INSTALL_ALL.sql` **마지막 단계**에서 **공지·커뮤니티(글·댓글·첨부·신고)·회원·검사이력·알림·예약** 등을 **전부 DELETE** 합니다. 관리자 계정도 시드하지 않습니다(회원가입 후 `role='ADMIN'` 부여).

### 데이터만 비우기 (스키마 유지)

```sql
SQL> @02_features/CLEAR_RUNTIME_DATA.sql
```

## 2. 기존 DB 업그레이드

이미 운영 중인 스키마라면 빠진 부분만 멱등 실행하면 됩니다(모든 스크립트는 `user_tables` / `user_tab_columns` 체크로 반복 실행 안전).

- **알림 컬럼/타입이 빠졌거나 구버전인 경우** → `02_features/MONITORING.sql` **재실행**. 누락된 컬럼(`link_url`, `title`, `related_post_id`, `related_comment_id`, `notice_id`)·`alert_type` CHECK(9종)·`post_comments.parent_comment_id`가 한 번에 보강됩니다. (구 `USER_ALERTS_EXTEND*` / `USER_ALERTS_TITLE` 패치 파일은 모두 MONITORING.sql에 통합되어 사라졌습니다.)
- **프로필 컬럼이 없는 경우** → `02_features/USERS_PROFILE.sql`.
- 구 카테고리(`스트레스 관리`, `경험 공유` 등)를 쓰던 DB → `archive/COMMUNITY_CATEGORY_MIGRATE.sql` 1회.

## 3. 트러블슈팅

| 증상 | 해결 (반드시 APP_USER로) |
|------|--------------------------|
| 알림 INSERT 시 `ORA-00904` (`RELATED_POST_ID`, `TITLE`, `LINK_URL` 등) | ① `02_features/MONITORING.sql` 재실행 → ② `archive/dev/USER_ALERTS_VERIFY.sql`로 컬럼·접속 계정 확인 → ③ 앱 재시작 |
| `assessment_results` 컬럼 꼬임 (ORA-00904/01400, `COMPLETED_AT`·`TYPE_NAME` 등) — **데이터 유지** | `archive/MONITORING_FIX.sql` → 앱 재시작 |
| 위가 반복됨 — **데이터 초기화 가능(개발 DB)** | `archive/MONITORING_REBUILD.sql`(DROP) → `02_features/MONITORING.sql`(재생성) → 앱 재시작 |

> `ORA-00904`가 계속 나면 십중팔구 **다른 계정(SYSTEM 등)으로 실행**한 경우입니다. `USER_ALERTS_VERIFY.sql` 첫 쿼리로 `SESSION_USER`가 `APP_USER`인지 확인하세요.

## 4. 구 파일명 → 신 경로 매핑

| 구 파일 | 신 위치 |
|---------|---------|
| `ORACLE_SETUP.sql` | `01_schema/ORACLE_SETUP.sql` |
| `USERS_PROFILE_SETUP.sql` | `02_features/USERS_PROFILE.sql` |
| `ASSESSMENT_SEED.sql` | `02_features/ASSESSMENT_SEED.sql` |
| `MONITORING_SETUP.sql` + `ALERTS_EXTEND.sql` | `02_features/MONITORING.sql` (**통합**) |
| `USER_ALERTS_EXTEND.sql` / `USER_ALERTS_EXTEND_PLAIN.sql` / `USER_ALERTS_TITLE.sql` | `02_features/MONITORING.sql`에 **통합 (파일 삭제)** |
| `USER_ALERTS_VERIFY.sql` | `archive/dev/USER_ALERTS_VERIFY.sql` |
| `CARE_REPORT.sql` | `02_features/CARE_REPORT.sql` |
| `CHAT_CLUSTERING.sql` | `02_features/CHAT_CLUSTERING.sql` |
| `CHAT_CLUSTER_REAL_USER_SEED.sql` | **삭제됨** (데모 사용자 더미 시드 제거) |
| `MONITORING_FIX.sql` / `MONITORING_REBUILD.sql` | `archive/` |
| `COMMUNITY_CATEGORY_MIGRATE.sql` | `archive/COMMUNITY_CATEGORY_MIGRATE.sql` |

## 5. 테이블 ↔ 기능 매핑

| 테이블 | 기능 | 생성 스크립트 |
|--------|------|---------------|
| `users` | 인증·프로필·등급(role) | ORACLE_SETUP + USERS_PROFILE |
| `posts`, `post_comments`, `attachments`, `reports` | 커뮤니티(글·댓글·답글·첨부·신고) | ORACLE_SETUP + MONITORING(답글 컬럼) |
| `notices` | 공지 | ORACLE_SETUP |
| `bookings` | 상담 예약 | ORACLE_SETUP |
| `book_reviews`, `recommendation_books` | 도서 리뷰·추천 | ORACLE_SETUP |
| `assessment_types`, `assessment_questions`, `assessment_score_ranges` | 자가진단 문항 | ASSESSMENT_SEED |
| `assessment_results`, `user_alerts` | 검사 이력·알림(모니터링) | MONITORING |
| `care_reports` | AI 위로 편지 | CARE_REPORT |
| `user_assessment_profiles` | 정서 클러스터(+페르소나 시드) | CHAT_CLUSTERING |
| `activity_log` | 추천 활동(호흡·감사일기 등) 완료 기록 | ACTIVITY_LOG |
| `proverbs` | 화면별 랜덤 명언 | PROVERBS_SEED (테이블+시드) |

## 6. JPA 엔티티 ↔ 테이블 (주요)

| 테이블 | 엔티티 (`com.mindlink...`) | 주의 |
|--------|----------------------------|------|
| `assessment_results` | `domain.AssessmentResult` | `score_level`→`level`, `is_high_risk`→`highRisk`(0/1), `type_key`→`typeKey` |
| `user_alerts` | `domain.UserAlert` | `alert_type` 9종, `is_read`→`read`(0/1) |
| `users` | `domain.User` | 프로필: USERS_PROFILE · 동의: PRIVACY_CONSENT |
| `activity_log` | `domain.ActivityLog` | `activity_key`, JSON `payload` |
| `proverbs` | `domain.Proverb` | page: HOME / COMMUNITY / RECOMMENDATIONS |
| `posts` / `post_comments` | `domain.Post` / `domain.PostComment` | `author`는 **문자열**(User FK 아님), 답글은 `parent_comment_id` |
| `care_reports` | `care.*` | `care_daily_inputs`는 앱 미사용 |
| `user_assessment_profiles` | `chatcluster.UserAssessmentProfile` | stress/depression/anxiety norm |

### `user_alerts` 컬럼 용도

| 컬럼 | 용도 |
|------|------|
| `alert_type` | `DETERIORATION`, `HIGH_RISK`, `RECOMMEND`, `IMPROVEMENT`, `IMPROVEMENT_MIN`, `POST_COMMENT`, `COMMENT_REPLY`, `NOTICE`, `ADMIN_MESSAGE` |
| `title` | 관리자 알림 **제목**(선택). 모니터링 알림은 보통 비움 |
| `message` | 알림 본문(CLOB) |
| `link_url` | 바로가기 URL — 공지·커뮤니티 자동 링크, 관리자 알림은 **체크 시에만** 채움 |
| `related_post_id`, `related_comment_id`, `notice_id` | 연관 글/댓글/공지 ID |

## 7. 관리자 등급 부여

관리자 UI(`/admin/**`)는 `role = 'ADMIN'`만 접근 가능합니다. **기본 관리자 시드는 없습니다.** 회원가입 후:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = '본인이_가입한_이메일';
COMMIT;
```
