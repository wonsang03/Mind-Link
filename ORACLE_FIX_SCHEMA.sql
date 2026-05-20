-- Oracle 스키마 보정 (문자 길이 여유 + CLOB 보정)
-- 실행 계정: APP_USER

ALTER SESSION SET NLS_LENGTH_SEMANTICS=CHAR;

-- posts: 길이 확장
ALTER TABLE posts MODIFY (author VARCHAR2(200 CHAR));
ALTER TABLE posts MODIFY (title VARCHAR2(1000 CHAR));
ALTER TABLE posts MODIFY (category VARCHAR2(200 CHAR));

-- post_comments: 길이 확장
ALTER TABLE post_comments MODIFY (author VARCHAR2(200 CHAR));

-- recommendation_books: 길이 확장
ALTER TABLE recommendation_books MODIFY (title VARCHAR2(1000 CHAR));
ALTER TABLE recommendation_books MODIFY (author VARCHAR2(500 CHAR));
ALTER TABLE recommendation_books MODIFY (publisher VARCHAR2(500 CHAR));
ALTER TABLE recommendation_books MODIFY (link VARCHAR2(2000 CHAR));
ALTER TABLE recommendation_books MODIFY (image VARCHAR2(2000 CHAR));

-- CONTENT 컬럼을 CLOB으로 강제 보정 (필요 시에만 변환)
DECLARE
    v_type VARCHAR2(30);
BEGIN
    SELECT data_type INTO v_type
      FROM user_tab_columns
     WHERE table_name = 'POSTS' AND column_name = 'CONTENT';

    IF v_type <> 'CLOB' THEN
        EXECUTE IMMEDIATE 'ALTER TABLE posts ADD content_new CLOB';
        EXECUTE IMMEDIATE 'UPDATE posts SET content_new = TO_CLOB(content)';
        EXECUTE IMMEDIATE 'ALTER TABLE posts DROP COLUMN content';
        EXECUTE IMMEDIATE 'ALTER TABLE posts RENAME COLUMN content_new TO content';
    END IF;
END;
/

DECLARE
    v_type VARCHAR2(30);
BEGIN
    SELECT data_type INTO v_type
      FROM user_tab_columns
     WHERE table_name = 'POST_COMMENTS' AND column_name = 'CONTENT';

    IF v_type <> 'CLOB' THEN
        EXECUTE IMMEDIATE 'ALTER TABLE post_comments ADD content_new CLOB';
        EXECUTE IMMEDIATE 'UPDATE post_comments SET content_new = TO_CLOB(content)';
        EXECUTE IMMEDIATE 'ALTER TABLE post_comments DROP COLUMN content';
        EXECUTE IMMEDIATE 'ALTER TABLE post_comments RENAME COLUMN content_new TO content';
    END IF;
END;
/

DECLARE
    v_type VARCHAR2(30);
BEGIN
    SELECT data_type INTO v_type
      FROM user_tab_columns
     WHERE table_name = 'RECOMMENDATION_BOOKS' AND column_name = 'DESCRIPTION';

    IF v_type <> 'CLOB' THEN
        EXECUTE IMMEDIATE 'ALTER TABLE recommendation_books ADD description_new CLOB';
        EXECUTE IMMEDIATE 'UPDATE recommendation_books SET description_new = TO_CLOB(description)';
        EXECUTE IMMEDIATE 'ALTER TABLE recommendation_books DROP COLUMN description';
        EXECUTE IMMEDIATE 'ALTER TABLE recommendation_books RENAME COLUMN description_new TO description';
    END IF;
END;
/

COMMIT;
