# DB

**Oracle**에 어떤 데이터를 어떻게 저장할지(표 이름, 항목, 관계, 접속 방법)를 정리합니다.
JPA는 `ddl-auto=none` 이므로 테이블·시드는 아래 SQL 스크립트를 **수동 실행**합니다.

## 실행 방법

- **원샷 설치(권장)**: [sql/00_INSTALL_ALL.sql](../sql/00_INSTALL_ALL.sql) — `@@` 상대경로로 필수 6개 스크립트를 순차 호출. `sql/` 폴더에서 **APP_USER** 로 실행.
- **개별 실행 순서**: `01_schema/ORACLE_SETUP.sql` → `02_features/USERS_PROFILE.sql` → `ASSESSMENT_SEED.sql` → `MONITORING.sql` → `CARE_REPORT.sql` → `CHAT_CLUSTERING.sql` → (선택) `03_optional/PROVERBS_SEED.sql`
- 전체 순서·업그레이드·트러블슈팅: [sql/README.md](../sql/README.md)

> 반드시 `.env` 의 `DB_USERNAME`(기본 `APP_USER`)으로 접속해 실행하세요. 다른 계정으로 돌리면 앱이 보는 스키마에 반영되지 않아 `ORA-00904` 등이 발생합니다.

## `01_schema/ORACLE_SETUP.sql`

- **역할**: 기본 테이블 DDL(빈 스키마일 때만 생성) + 컬럼 길이·CLOB·`search_keyword` 보정 + `book_reviews` 생성 + **기본 관리자 계정 MERGE**(`admin@mindlink.com` / `admin1234`) + `recommendation_books` 초기 데이터(전량 재적재, 교보문고 링크 세트).
- **시드 정책**: 공지·게시글·댓글·데모 사용자 같은 **커뮤니티 더미 데이터는 시드하지 않습니다** — 빈 테이블로 시작합니다. (유지되는 시드: 관리자 계정, `recommendation_books`, 자가진단 마스터, 클러스터 210 합성 페르소나)
- **선행 조건**: 없음. 빈 스키마에서도 섹션 0이 `users`·`posts`·`notices` 등 기본 테이블을 먼저 생성합니다.

## 테이블 ↔ 기능 ↔ 생성 스크립트

| 테이블 | 기능 | 스크립트 |
|--------|------|----------|
| `users` | 인증·프로필·역할(role) | ORACLE_SETUP + USERS_PROFILE |
| `posts`, `post_comments`, `attachments`, `reports` | 커뮤니티(글·댓글·답글·첨부·신고) | ORACLE_SETUP + MONITORING(`parent_comment_id`) |
| `notices` | 공지 | ORACLE_SETUP |
| `bookings` | 상담 예약 | ORACLE_SETUP |
| `book_reviews`, `recommendation_books` | 도서 리뷰·추천 | ORACLE_SETUP |
| `assessment_types`, `assessment_questions`, `assessment_choices`, `score_ranges` | 자가진단 문항·구간 | ASSESSMENT_SEED |
| `assessment_results`, `user_alerts` | 검사 이력·알림 | MONITORING |
| `care_reports` | AI 위로 편지 | CARE_REPORT |
| `activity_log` | 추천 활동 수행 이력 | ACTIVITY_LOG |
| `user_assessment_profiles` | 정서 클러스터(+210 합성 페르소나) | CHAT_CLUSTERING |
| `proverbs` | 명언·속담 | (선택) PROVERBS_SEED |

> `chat_rooms` / `chat_room_members`(미사용 오픈채팅 예비 테이블)는 Java 코드에서 쓰지 않아 DDL에서 제거되었습니다. 향후 클러스터 매칭방은 별도 정의 예정.

## `recommendation_books`

- **emotion**: 도서 감정 태그(`DEPRESSION`, `STRESS`, `ANXIETY`, `LETHARGY`, `RELATIONSHIP`, `NORMAL`). AI 맞춤 추천(`POST /api/recommendations/ai`)으로 확정되면 Gemini가 권별로 판정한 값으로 INSERT/UPDATE됩니다.
- **search_keyword**: 네이버·AI 검색에 사용된 검색어 문자열(최대 500자). ORACLE_SETUP **1번(스키마 보정) 구간**에서 컬럼이 없을 때만 `ALTER`로 추가합니다.
- **isbn**: UNIQUE. 네이버 캐시 도서 식별용(없을 수 있음).
