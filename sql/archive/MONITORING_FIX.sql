-- =============================================================================
-- Mind-Link · 모니터링 스키마 긴급 보정 (테이블 DROP 없이)
--
-- ORA-00904 created_at / ORA-01400 TYPE_NAME·RESULT_LEVEL 등
-- → Hibernate 가 만든 assessment_results 와 JPA 컬럼 불일치
--
-- ★ 권장: 반복되면 archive/MONITORING_REBUILD.sql + 02_features/MONITORING.sql (데이터 초기화)
-- 계정: APP_USER
-- =============================================================================

SET SERVEROUTPUT ON;

-- ── A. 컬럼 추가 (없을 때만) ─────────────────────────────────────────────
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE assessment_results ADD (created_at TIMESTAMP DEFAULT SYSTIMESTAMP)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430,-942) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE assessment_results ADD (type_key VARCHAR2(50 CHAR))'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430,-942) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE assessment_results ADD (type_name VARCHAR2(100 CHAR))'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430,-942) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE assessment_results ADD (result_level VARCHAR2(30 CHAR))'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430,-942) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE assessment_results ADD (score_level VARCHAR2(30 CHAR))'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430,-942) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE assessment_results ADD (score NUMBER)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430,-942) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE assessment_results ADD (is_high_risk NUMBER(1) DEFAULT 0)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430,-942) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE assessment_results ADD (personal_score NUMBER)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430,-942) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE assessment_results ADD (personal_level VARCHAR2(30 CHAR))'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430,-942) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE assessment_results ADD (work_score NUMBER)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430,-942) THEN RAISE; END IF; END;
/
BEGIN EXECUTE IMMEDIATE 'ALTER TABLE assessment_results ADD (work_level VARCHAR2(30 CHAR))'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-1430,-942) THEN RAISE; END IF; END;
/

-- completed_at → created_at 복사
DECLARE
  c_created NUMBER; c_completed NUMBER;
BEGIN
  SELECT COUNT(*) INTO c_created FROM user_tab_columns WHERE table_name = 'ASSESSMENT_RESULTS' AND column_name = 'CREATED_AT';
  SELECT COUNT(*) INTO c_completed FROM user_tab_columns WHERE table_name = 'ASSESSMENT_RESULTS' AND column_name = 'COMPLETED_AT';
  IF c_created = 0 AND c_completed > 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE assessment_results ADD (created_at TIMESTAMP)';
    EXECUTE IMMEDIATE 'UPDATE assessment_results SET created_at = completed_at WHERE created_at IS NULL';
  END IF;
END;
/

-- ── B. 레거시 컬럼 백필 (JPA 가 INSERT 시 채우는 값과 동기) ─────────────────
UPDATE assessment_results SET type_name = CASE type_key
  WHEN 'depression' THEN '우울증 (PHQ-9)'
  WHEN 'anxiety'    THEN '불안장애 (GAD-7)'
  WHEN 'stress'     THEN '스트레스 (PSS-10)'
  WHEN 'burnout'    THEN '번아웃 (CBI)'
  ELSE NVL(type_key, '자가진단')
END WHERE type_name IS NULL;

UPDATE assessment_results SET result_level = NVL(score_level, NVL(personal_level, NVL(work_level, '미분류')))
 WHERE result_level IS NULL;

UPDATE assessment_results SET score_level = result_level
 WHERE score_level IS NULL AND result_level IS NOT NULL;

UPDATE assessment_results SET created_at = SYSTIMESTAMP WHERE created_at IS NULL;

COMMIT;

-- ── C. 확인 (앱이 INSERT 하는 컬럼) ───────────────────────────────────────
PROMPT === ASSESSMENT_RESULTS columns ===
SELECT column_name, nullable, data_type
  FROM user_tab_columns
 WHERE table_name = 'ASSESSMENT_RESULTS'
 ORDER BY column_id;

PROMPT === Canonical schema (02_features/MONITORING.sql 신규 테이블) ===
PROMPT id, user_id, type_key, type_name, score, score_level, result_level(legacy), is_high_risk,
PROMPT personal_*, work_*, created_at
