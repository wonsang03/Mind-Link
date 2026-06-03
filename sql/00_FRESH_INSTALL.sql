-- =============================================================================
-- Mind-Link · 초기화 + 전체 설치 (한 번에)
--
--   ▶ 실행 계정: APP_USER
--   ▶ 1) 00_RESET_ALL.sql  — 모든 앱 테이블 DROP
--   ▶ 2) 00_INSTALL_ALL.sql — 스키마·시드 재생성
--
--   SQL Developer / SQL*Plus (sql/ 폴더 기준):
--     SQL> @00_FRESH_INSTALL.sql
-- =============================================================================

SET DEFINE OFF
WHENEVER SQLERROR CONTINUE
ALTER SESSION SET NLS_LENGTH_SEMANTICS=CHAR;

PROMPT
PROMPT ===== [1/2] RESET =====
@@00_RESET_ALL.sql

PROMPT
PROMPT ===== [2/2] INSTALL =====
@@00_INSTALL_ALL.sql

PROMPT
PROMPT ===========================================================================
PROMPT  FRESH INSTALL 완료. Spring Boot 앱을 재시작하세요.
PROMPT ===========================================================================
