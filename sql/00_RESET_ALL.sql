-- =============================================================================
-- Mind-Link · Oracle 스키마 초기화 (개발 DB 전용)
--
--   ▶ 실행 계정: APP_USER (= .env 의 DB_USERNAME)
--   ▶ 주의: 아래 테이블의 **모든 데이터가 삭제**됩니다.
--   ▶ 이후: @00_INSTALL_ALL.sql 또는 @00_FRESH_INSTALL.sql 실행
-- =============================================================================

SET DEFINE OFF
SET SERVEROUTPUT ON
WHENEVER SQLERROR CONTINUE
ALTER SESSION SET NLS_LENGTH_SEMANTICS=CHAR;

PROMPT
PROMPT ===========================================================================
PROMPT  Mind-Link — DROP ALL APPLICATION TABLES (APP_USER schema)
PROMPT ===========================================================================

DECLARE
  PROCEDURE drop_if_exists(p_table VARCHAR2) IS
  BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE ' || p_table || ' CASCADE CONSTRAINTS PURGE';
    DBMS_OUTPUT.PUT_LINE('OK: dropped ' || p_table);
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLCODE = -942 THEN
        DBMS_OUTPUT.PUT_LINE('SKIP: ' || p_table || ' (not found)');
      ELSE
        DBMS_OUTPUT.PUT_LINE('WARN: ' || p_table || ' — ' || SQLERRM);
      END IF;
  END;
BEGIN
  /* 자식 → 부모 순 */
  drop_if_exists('activity_log');
  drop_if_exists('care_daily_inputs');
  drop_if_exists('care_reports');
  drop_if_exists('user_assessment_profiles');
  drop_if_exists('user_alerts');
  drop_if_exists('book_reviews');
  drop_if_exists('reports');
  drop_if_exists('attachments');
  drop_if_exists('post_comments');
  drop_if_exists('assessment_results');
  drop_if_exists('score_ranges');
  drop_if_exists('assessment_choices');
  drop_if_exists('assessment_questions');
  drop_if_exists('assessment_types');
  drop_if_exists('bookings');
  drop_if_exists('posts');
  drop_if_exists('notices');
  drop_if_exists('recommendation_books');
  drop_if_exists('proverbs');
  drop_if_exists('users');
END;
/

COMMIT;

PROMPT
PROMPT ===========================================================================
PROMPT  초기화 완료. 다음: @00_INSTALL_ALL.sql
PROMPT ===========================================================================
