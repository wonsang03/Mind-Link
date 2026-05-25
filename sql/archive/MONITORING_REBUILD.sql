-- =============================================================================
-- Mind-Link · 모니터링 테이블 재생성 (Hibernate 구스키마 ORA-01400/00904 해결)
--
-- ★ 기존 assessment_results 가 Hibernate 로 만들어져
--   TYPE_NAME, RESULT_LEVEL, COMPLETED_AT 등 JPA 와 다른 컬럼이 섞여 있을 때 사용
--
-- 주의: assessment_results · user_alerts 데이터 전부 삭제됩니다.
-- 실행 계정: APP_USER
-- 실행 후: sql/02_features/MONITORING.sql 전체 실행 (테이블 없으면 생성 + user_alerts)
-- =============================================================================

SET SERVEROUTPUT ON;

-- 1) 알림·결과 데이터 삭제
BEGIN
  EXECUTE IMMEDIATE 'DELETE FROM user_alerts';
  DBMS_OUTPUT.PUT_LINE('OK: user_alerts cleared');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -942 THEN DBMS_OUTPUT.PUT_LINE('SKIP: user_alerts (no table)');
    ELSE RAISE;
    END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'DELETE FROM assessment_results';
  DBMS_OUTPUT.PUT_LINE('OK: assessment_results cleared');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -942 THEN DBMS_OUTPUT.PUT_LINE('SKIP: assessment_results (no table)');
    ELSE RAISE;
    END IF;
END;
/

-- 2) user_alerts FK 제거 후 assessment_results DROP
DECLARE
  PROCEDURE drop_fk_to_ar IS
  BEGIN
    FOR r IN (
      SELECT uc.constraint_name, uc.table_name
        FROM user_constraints uc
        JOIN user_cons_columns ucc ON ucc.constraint_name = uc.constraint_name
        JOIN user_constraints pk ON pk.constraint_name = uc.r_constraint_name
       WHERE uc.constraint_type = 'R'
         AND pk.table_name = 'ASSESSMENT_RESULTS'
    ) LOOP
      BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE ' || r.table_name || ' DROP CONSTRAINT ' || r.constraint_name;
        DBMS_OUTPUT.PUT_LINE('OK: dropped FK ' || r.constraint_name);
      EXCEPTION
        WHEN OTHERS THEN NULL;
      END;
    END LOOP;
  END;
BEGIN
  drop_fk_to_ar;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'DROP TABLE assessment_results CASCADE CONSTRAINTS PURGE';
  DBMS_OUTPUT.PUT_LINE('OK: assessment_results dropped');
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -942 THEN DBMS_OUTPUT.PUT_LINE('SKIP: assessment_results already absent');
    ELSE RAISE;
    END IF;
END;
/

COMMIT;

PROMPT === 다음: sql/02_features/MONITORING.sql 을 이어서 실행하세요 ===
