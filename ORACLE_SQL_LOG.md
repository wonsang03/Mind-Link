# Oracle SQL 실행 이력 — recommendation 기능

접속 정보: `APP_USER / AppUser1234 @ localhost:1521/FREEPDB1`

---

## DDL — Hibernate 자동 생성 (`ddl-auto=update`)

앱 최초 기동 시 Hibernate가 아래 테이블을 Oracle에 자동 생성한다.
수동 실행 불필요 (참고용).

```sql
CREATE TABLE recommendation_books (
    id          NUMBER(19,0) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    emotion     VARCHAR2(50)   NOT NULL,
    title       VARCHAR2(255)  NOT NULL,
    author      VARCHAR2(255),
    publisher   VARCHAR2(255),
    link        VARCHAR2(255),
    image       VARCHAR2(500),
    description VARCHAR2(1000),
    isbn        VARCHAR2(50)   UNIQUE
);
```

---

## 초기 데이터 — `ORACLE_SEED.sql` (최초 1회 수동 실행)

```
실행 순서:
1. 앱을 한 번 기동하여 Hibernate가 recommendation_books 테이블 생성 확인
2. Oracle SQL Developer (WEB 접속) 에서 ORACLE_SEED.sql 실행
3. COMMIT 확인
```

실행 내용 요약:
- `DELETE FROM recommendation_books WHERE isbn IS NULL` — 기존 수동 데이터 초기화
- `INSERT ALL ... SELECT 1 FROM DUAL` — 감정별 5권 × 4감정 = 20건 삽입
- `COMMIT`

---

## isbn 컬럼 추가 (기존 테이블 운영 중 추가 시)

Hibernate `ddl-auto=update`가 자동 처리하나, 수동 실행이 필요한 경우:

```sql
ALTER TABLE recommendation_books ADD isbn VARCHAR2(50);
ALTER TABLE recommendation_books ADD CONSTRAINT uk_recbook_isbn UNIQUE (isbn);
COMMIT;
```

---

## 도서 수동 추가 (운영 중 신규 도서 등록)

```sql
INSERT INTO recommendation_books (emotion, title, author, publisher, link, image, description)
VALUES ('DEPRESSION', '책 제목', '저자명', '출판사', NULL, NULL, '한줄 설명');
COMMIT;
```

- `emotion`: `DEPRESSION` | `STRESS` | `ANXIETY` | `LETHARGY` | `NORMAL`
- `link`, `image`: 네이버 도서 URL 있으면 입력, 없으면 `NULL`
- `isbn`: 수동 등록 도서는 `NULL` 권장 (자동 캐싱 도서와 구분)

---

## 자동 캐싱 확인 (네이버 신규 도서 캐싱 여부)

```sql
SELECT id, emotion, title, isbn, description
FROM   recommendation_books
WHERE  isbn IS NOT NULL
ORDER  BY id DESC;
```

---

## 전체 데이터 조회

```sql
SELECT emotion, COUNT(*) AS cnt
FROM   recommendation_books
GROUP  BY emotion
ORDER  BY emotion;
```
