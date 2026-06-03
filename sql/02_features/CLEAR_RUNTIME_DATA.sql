-- =============================================================================
-- Mind-Link · 런타임 데이터 초기화 (공지·커뮤니티·회원·이력)
--
--   ▶ 실행 계정: APP_USER
--   ▶ 유지: 자가진단 마스터, 추천도서, 합성 페르소나(210), proverbs
--   ▶ 삭제: users, posts, notices, 검사이력, 알림, 예약, 실사용자 프로필 등
--   ▶ 00_INSTALL_ALL.sql 맨 마지막에 자동 호출됨
-- =============================================================================

SET SERVEROUTPUT ON;

DECLARE
  PROCEDURE del(p_sql VARCHAR2, p_label VARCHAR2) IS
  BEGIN
    EXECUTE IMMEDIATE p_sql;
    DBMS_OUTPUT.PUT_LINE('OK: ' || p_label);
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLCODE = -942 THEN
        DBMS_OUTPUT.PUT_LINE('SKIP: ' || p_label || ' (no table)');
      ELSE
        DBMS_OUTPUT.PUT_LINE('WARN: ' || p_label || ' — ' || SQLERRM);
      END IF;
  END;
BEGIN
  del('DELETE FROM attachments', 'attachments');
  del('DELETE FROM reports', 'reports');
  del('DELETE FROM post_comments', 'post_comments');
  del('DELETE FROM posts', 'posts');
  del('DELETE FROM notices', 'notices');
  del('DELETE FROM user_alerts', 'user_alerts');
  del('DELETE FROM care_reports', 'care_reports');
  del('DELETE FROM activity_log', 'activity_log');
  del('DELETE FROM assessment_results', 'assessment_results');
  del('DELETE FROM book_reviews', 'book_reviews');
  del('DELETE FROM bookings', 'bookings');
  del('DELETE FROM user_assessment_profiles WHERE NVL(is_synthetic, 0) = 0', 'user_assessment_profiles (real users)');
  del('DELETE FROM users', 'users');
END;
/

COMMIT;

PROMPT === 런타임 데이터 삭제 완료 (공지·커뮤니티·회원 비움). 관리자는 회원가입 후 role 부여. ===
