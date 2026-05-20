# DB

**Oracle**에 어떤 데이터를 어떻게 저장할지(표 이름, 항목, 관계, 접속 방법)를 이 문서에 정리합니다.

## `recommendation_books`

- **emotion**: 도서에 부여된 감정 태그(`DEPRESSION`, `STRESS`, …). AI 맞춤 추천(`POST /api/recommendations/ai`)으로 확정되면 Gemini가 권별로 판정한 값으로 INSERT/UPDATE됩니다.
- **search_keyword**: 네이버·AI 검색에 사용된 검색어 문자열(최대 500자). 스키마 보정 시 `ORACLE_FIX_SCHEMA.sql`의 `SEARCH_KEYWORD` 컬럼 추가 블록을 실행합니다.
