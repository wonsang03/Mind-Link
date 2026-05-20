-- ============================================================
--  recommendation_books 초기 데이터
--  실행 방법: APP_USER 계정으로 Oracle 접속 후 1회만 실행
--  접속: APP_USER / AppUser1234
--  주의: 테이블은 앱 최초 기동 시 Hibernate가 자동 생성함
--        앱을 먼저 한 번 실행한 뒤 이 스크립트를 실행할 것
-- ============================================================

-- 기존 수동 등록 데이터 초기화 (isbn IS NULL = 수동 등록 도서)
-- 네이버 자동 캐싱 도서(isbn NOT NULL)는 삭제되지 않음
DELETE FROM recommendation_books WHERE isbn IS NULL;

-- 도서 5권씩 일괄 삽입 (Oracle INSERT ALL 문법)
-- 책 정보 변경 시 VALUES 부분만 수정 후 재실행
INSERT ALL
    -- DEPRESSION (우울)
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('DEPRESSION', '죽고 싶지만 떡볶이는 먹고 싶어', '백세희', '흔', NULL, NULL, '우울감을 솔직하게 풀어낸 에세이')
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('DEPRESSION', '나는 나로 살기로 했다', '김수현', '마음의숲', NULL, NULL, '자기 자신을 사랑하는 법')
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('DEPRESSION', '감정의 발견', '마크 브래킷', '북라이프', NULL, NULL, '감정을 이해하고 다루는 방법')
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('DEPRESSION', '우울할 땐 뇌과학', '알렉스 코브', '심심', NULL, NULL, '뇌과학으로 보는 우울증 극복법')
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('DEPRESSION', '괜찮지 않아도 괜찮아', '최명기', '더퀘스트', NULL, NULL, '있는 그대로의 나를 받아들이는 심리 에세이')

    -- STRESS (스트레스)
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('STRESS', '마음챙김', '마크 윌리엄스', '불광출판사', NULL, NULL, '스트레스 해소를 위한 마음챙김')
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('STRESS', '지금 이 순간을 살아라', '에크하르트 톨레', '양문', NULL, NULL, '현재에 집중하는 삶')
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('STRESS', '번아웃 사회', '한병철', '문학과지성사', NULL, NULL, '현대 사회의 피로감을 분석')
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('STRESS', '쉬어도 피곤한 사람들', '박용철', '21세기북스', NULL, NULL, '번아웃에서 벗어나는 회복 전략')
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('STRESS', '번아웃', '에밀리 나고스키', '해나무', NULL, NULL, '완벽주의에서 벗어나 진짜 쉬는 법')

    -- ANXIETY (불안)
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('ANXIETY', '불안', '알랭 드 보통', '은행나무', NULL, NULL, '불안의 본질을 탐구')
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('ANXIETY', '오늘도 불안한 너에게', '박진영', '시공사', NULL, NULL, '불안을 극복하는 실천법')
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('ANXIETY', '명상록', '마르쿠스 아우렐리우스', '현대지성', NULL, NULL, '내면의 평화를 위한 철학적 성찰')
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('ANXIETY', '걱정 끄기 연습', '쑤언 수치', '유노북스', NULL, NULL, '걱정을 줄이는 심리 기법')
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('ANXIETY', '불안해도 괜찮아', '클라우스 베른하르트', '갈매나무', NULL, NULL, '불안 장애를 스스로 극복하는 인지 훈련법')

    -- LETHARGY (무기력)
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('LETHARGY', '아주 작은 습관의 힘', '제임스 클리어', '비즈니스북스', NULL, NULL, '작은 습관으로 큰 변화를')
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('LETHARGY', '미라클 모닝', '할 엘로드', '한빛비즈', NULL, NULL, '아침을 바꾸면 인생이 바뀐다')
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('LETHARGY', '그릿', '앤절라 더크워스', '비즈니스북스', NULL, NULL, '끝까지 해내는 힘의 비밀')
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('LETHARGY', '내가 확실히 알고 있는 것들', '오프라 윈프리', '북하우스', NULL, NULL, '삶을 다시 시작하게 해주는 이야기')
    INTO recommendation_books (emotion, title, author, publisher, link, image, description)
        VALUES ('LETHARGY', '게으름에 대한 찬양', '버트런드 러셀', '사회평론', NULL, NULL, '진정한 휴식과 활력을 되찾는 철학적 에세이')
SELECT 1 FROM DUAL;

COMMIT;
