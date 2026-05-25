-- 실행 계정이 앱과 같은지 확인 (.env DB_USERNAME = APP_USER)
SELECT SYS_CONTEXT('USERENV', 'SESSION_USER') AS session_user,
       SYS_CONTEXT('USERENV', 'CON_NAME')     AS pdb
  FROM dual;

-- user_alerts 컬럼 목록 (RELATED_POST_ID 등 4개 있어야 함)
SELECT column_id, column_name, data_type
  FROM user_tab_columns
 WHERE table_name = 'USER_ALERTS'
 ORDER BY column_id;

-- 필수 컬럼 확인 (5행: LINK_URL, TITLE, RELATED_POST_ID, RELATED_COMMENT_ID, NOTICE_ID)
SELECT column_name
  FROM user_tab_columns
 WHERE table_name = 'USER_ALERTS'
   AND column_name IN ('LINK_URL', 'TITLE', 'RELATED_POST_ID', 'RELATED_COMMENT_ID', 'NOTICE_ID')
 ORDER BY column_name;
