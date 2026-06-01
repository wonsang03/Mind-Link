-- 개인정보 보호법 제23조: 민감정보(자가진단 결과) 별도 동의 컬럼 추가
-- APP_USER 로 실행할 것

DECLARE
  c INTEGER;
BEGIN
  SELECT COUNT(*) INTO c FROM user_tab_columns
  WHERE table_name = 'USERS' AND column_name = 'SENSITIVE_DATA_CONSENT';
  IF c = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE users ADD (sensitive_data_consent NUMBER(1) DEFAULT 0 NOT NULL)';
  END IF;
END;
/
COMMENT ON COLUMN users.sensitive_data_consent IS '민감정보(자가진단 결과) 처리 동의 여부 (0=미동의, 1=동의) — 개인정보보호법 제23조';
