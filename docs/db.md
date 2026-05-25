# DB

**Oracle**에 어떤 데이터를 어떻게 저장할지(표 이름, 항목, 관계, 접속 방법)를 이 문서에 정리합니다.

## 수동 SQL (통합)

- **경로**: [sql/ORACLE_SETUP.sql](../sql/ORACLE_SETUP.sql)
- **역할**: 컬럼 길이·CLOB·`search_keyword` 보정, `users`(role 포함)·`book_reviews` 생성, **관리자·공지·커뮤니티 샘플**(김동주 `data.sql`과 동일), `recommendation_books` 초기 데이터(기본은 전량 재적재). 가벼운 20권 시드는 같은 파일 맨 아래 주석 블록.
- **선행 조건**: 없음. 빈 스키마에서도 `ORACLE_SETUP.sql` 섹션 0이 `posts`·`notices` 등 기본 테이블을 먼저 생성합니다. 전체 실행 순서는 [sql/README.md](../sql/README.md) 참고.

## `recommendation_books`

- **emotion**: 도서에 부여된 감정 태그(`DEPRESSION`, `STRESS`, …). AI 맞춤 추천(`POST /api/recommendations/ai`)으로 확정되면 Gemini가 권별로 판정한 값으로 INSERT/UPDATE됩니다.
- **search_keyword**: 네이버·AI 검색에 사용된 검색어 문자열(최대 500자). 위 통합 스크립트 **1번 구간**에서 없을 때만 `ALTER`로 추가합니다.
