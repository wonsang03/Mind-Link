-- =============================================================================
-- ACTIVITY_LOG.sql — 추천 활동 수행 기록 (멱등)
-- 맞춤 추천의 "추천 활동"(호흡·감사 일기·감정 체크인 등) 완료를 저장한다.
-- 활동 일지(기록), 연속일·주간 통계(습관), 여정(프로그램)의 공통 기반 테이블.
-- 전제: users 테이블이 먼저 존재해야 함 (01_schema/ORACLE_SETUP.sql).
-- =============================================================================

DECLARE
  v_cnt NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_cnt FROM user_tables WHERE table_name = 'ACTIVITY_LOG';
  IF v_cnt = 0 THEN
    EXECUTE IMMEDIATE q'[
      CREATE TABLE activity_log (
        id           NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
        user_id      NUMBER         NOT NULL,
        activity_key VARCHAR2(40)   NOT NULL,   -- gratitude / breathing / checkin ...
        payload      CLOB,                      -- 활동별 입력/결과(JSON), 없으면 NULL
        mood_score   NUMBER(2),                 -- 감정 체크인 정서가(1~5), 그 외 NULL
        duration_sec NUMBER,                    -- 소요 시간(선택)
        program_key  VARCHAR2(60),              -- 여정 진행 중일 때(선택, Phase 3)
        created_at   TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
        CONSTRAINT fk_actlog_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
      )
    ]';
    EXECUTE IMMEDIATE 'CREATE INDEX idx_actlog_user_created ON activity_log(user_id, created_at)';
  END IF;
END;
/
