-- =============================================================================
-- Mind-Link · users 프로필 컬럼 (내 정보 / 프로필 이미지)
-- 실행: ORACLE_SETUP.sql 직후, ASSESSMENT_SEED.sql 이전 (APP_USER)
-- 멱등: 이미 있으면 건너뜀
-- =============================================================================

DECLARE
  v_cnt NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_cnt FROM user_tab_columns
   WHERE table_name = 'USERS' AND column_name = 'NICKNAME';
  IF v_cnt = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE users ADD (nickname VARCHAR2(100))';
  END IF;

  SELECT COUNT(*) INTO v_cnt FROM user_tab_columns
   WHERE table_name = 'USERS' AND column_name = 'REGION';
  IF v_cnt = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE users ADD (region VARCHAR2(100))';
  END IF;

  SELECT COUNT(*) INTO v_cnt FROM user_tab_columns
   WHERE table_name = 'USERS' AND column_name = 'NOTIFICATION_ENABLED';
  IF v_cnt = 0 THEN
    EXECUTE IMMEDIATE q'[
      ALTER TABLE users ADD (
        notification_enabled NUMBER(1) DEFAULT 0 NOT NULL
          CONSTRAINT chk_users_notification CHECK (notification_enabled IN (0, 1))
      )
    ]';
  END IF;

  SELECT COUNT(*) INTO v_cnt FROM user_tab_columns
   WHERE table_name = 'USERS' AND column_name = 'PHONE';
  IF v_cnt = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE users ADD (phone VARCHAR2(20))';
  END IF;

  SELECT COUNT(*) INTO v_cnt FROM user_tab_columns
   WHERE table_name = 'USERS' AND column_name = 'PROFILE_IMAGE_URL';
  IF v_cnt = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE users ADD (profile_image_url VARCHAR2(500))';
  END IF;

  DBMS_OUTPUT.PUT_LINE('users 프로필 컬럼 확인 완료');
END;
/

COMMIT;
