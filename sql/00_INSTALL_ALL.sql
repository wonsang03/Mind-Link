-- =============================================================================
-- Mind-Link · Oracle 원샷 설치 스크립트 (한 번 실행으로 전체 스키마 구축)
--
--   ▶ 실행 계정: 반드시 APP_USER (= .env 의 DB_USERNAME) 로 접속해 실행하세요.
--     SYSTEM 등 다른 계정으로 돌리면 앱이 보는 스키마에 반영되지 않아
--     ORA-00904 같은 오류가 납니다.
--
--   ▶ 실행 방법
--       SQL*Plus / SQL Developer 에서 sql/ 폴더 기준:
--         SQL> @00_INSTALL_ALL.sql
--       초기화 후 재설치:
--         SQL> @00_FRESH_INSTALL.sql
--
--   ▶ 포함 순서
--       1) 01_schema/ORACLE_SETUP.sql
--       2) 02_features/USERS_PROFILE.sql
--       3) 02_features/PRIVACY_CONSENT.sql
--       4) 02_features/ASSESSMENT_SEED.sql
--       5) 02_features/MONITORING.sql
--       6) 02_features/CARE_REPORT.sql
--       7) 02_features/ACTIVITY_LOG.sql
--       8) 02_features/CHAT_CLUSTERING.sql
--       (선택) 03_optional/PROVERBS_SEED.sql
--
--   ▶ 시드: 추천도서·자가진단 마스터·210 페르소나·(선택) 명언
--   ▶ [9] CLEAR_RUNTIME_DATA: 공지·커뮤니티·회원·이력 전부 삭제 (빈 DB로 서버 오픈)
-- =============================================================================

SET DEFINE OFF
WHENEVER SQLERROR CONTINUE
ALTER SESSION SET NLS_LENGTH_SEMANTICS=CHAR;

PROMPT
PROMPT ===========================================================================
PROMPT  [1/8] 01_schema/ORACLE_SETUP.sql
PROMPT ===========================================================================
@@01_schema/ORACLE_SETUP.sql

PROMPT
PROMPT ===========================================================================
PROMPT  [2/8] 02_features/USERS_PROFILE.sql
PROMPT ===========================================================================
@@02_features/USERS_PROFILE.sql

PROMPT
PROMPT ===========================================================================
PROMPT  [3/8] 02_features/PRIVACY_CONSENT.sql
PROMPT ===========================================================================
@@02_features/PRIVACY_CONSENT.sql

PROMPT
PROMPT ===========================================================================
PROMPT  [4/8] 02_features/ASSESSMENT_SEED.sql
PROMPT ===========================================================================
@@02_features/ASSESSMENT_SEED.sql

PROMPT
PROMPT ===========================================================================
PROMPT  [5/8] 02_features/MONITORING.sql
PROMPT ===========================================================================
@@02_features/MONITORING.sql

PROMPT
PROMPT ===========================================================================
PROMPT  [6/8] 02_features/CARE_REPORT.sql
PROMPT ===========================================================================
@@02_features/CARE_REPORT.sql

PROMPT
PROMPT ===========================================================================
PROMPT  [7/8] 02_features/ACTIVITY_LOG.sql
PROMPT ===========================================================================
@@02_features/ACTIVITY_LOG.sql

PROMPT
PROMPT ===========================================================================
PROMPT  [8/8] 02_features/CHAT_CLUSTERING.sql
PROMPT ===========================================================================
@@02_features/CHAT_CLUSTERING.sql

-- (선택) 명언·속담 — 홈/커뮤니티/추천 랜덤 문구
@@03_optional/PROVERBS_SEED.sql

PROMPT
PROMPT ===========================================================================
PROMPT  [9/9] 02_features/CLEAR_RUNTIME_DATA.sql  — 공지·커뮤니티·회원 데이터 삭제
PROMPT ===========================================================================
@@02_features/CLEAR_RUNTIME_DATA.sql

PROMPT
PROMPT ===========================================================================
PROMPT  설치 완료. ORA- 오류 없는지 확인 후 Spring Boot 재시작.
PROMPT  회원·공지·커뮤니티는 비어 있음 → 회원가입 후 사용. ADMIN은 SQL로 role 부여.
PROMPT ===========================================================================
