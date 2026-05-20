-- =====================================================
-- Oracle 수동 반영용 (추천도서 전용)
-- 실행 계정: APP_USER
-- 목적: recommendation_books 전체 삭제 후 data.sql 도서만 재삽입
-- =====================================================

-- 0) emotion 체크 제약 교체
--    (EMOTION을 참조하는 CHECK 제약을 전부 제거 후 재생성)
DECLARE
    v_sql VARCHAR2(4000);
BEGIN
    FOR c IN (
        SELECT constraint_name
          FROM user_constraints
         WHERE table_name = 'RECOMMENDATION_BOOKS'
           AND constraint_type = 'C'
           AND UPPER(search_condition_vc) LIKE '%EMOTION%'
    ) LOOP
        v_sql := 'ALTER TABLE recommendation_books DROP CONSTRAINT ' || c.constraint_name;
        EXECUTE IMMEDIATE v_sql;
    END LOOP;
END;
/

ALTER TABLE recommendation_books ADD CONSTRAINT chk_recommendation_books_emotion
CHECK (emotion IN ('DEPRESSION', 'STRESS', 'ANXIETY', 'LETHARGY', 'RELATIONSHIP', 'NORMAL'));

-- 1) 기존 도서 전체 삭제 (네이버 캐시 포함 전부 초기화)
DELETE FROM recommendation_books;

-- 2) data.sql 기준 도서 데이터 삽입
INSERT INTO recommendation_books (emotion, title, author, publisher, link, image, description)
SELECT 'STRESS', '스트레스(STRESS)', '로버트 새폴스키', '사이언스북스', 'https://product.kyobobook.co.kr/detail/S000001290666', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9788983712325.jpg', '과학적 시각으로 스트레스의 본질을 파고드는 심층 분석서' FROM dual
UNION ALL SELECT 'STRESS', '스트레스는 어떻게 삶을 이롭게 하는가', '우르스 빌만', '심심', 'https://product.kyobobook.co.kr/detail/S000001744823', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9791156757085.jpg', '인간의 진화와 발전에 커다란 영향을 미친 스트레스 이야기' FROM dual
UNION ALL SELECT 'STRESS', '스트레스와 트라우마, 치유할 수 있다', '데이빗 버셀리', '오랜 기억', 'https://product.kyobobook.co.kr/detail/S000001971552', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9791196076931.jpg', NULL FROM dual
UNION ALL SELECT 'ANXIETY', '불안 끄기 연습', '오언 오케인', '웅진지식하우스', 'https://product.kyobobook.co.kr/detail/S000219997725', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9788901299860.jpg', '불안을 없애려 할수록 더 불안해지는 사람들을 위한 심리 처방전' FROM dual
UNION ALL SELECT 'DEPRESSION', '우울할 땐 뇌 과학', '앨릭스 코브', '심심', 'https://product.kyobobook.co.kr/detail/S000001744837', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9791156757344.jpg', '우울감을 뇌 과학으로 이해하고 삶의 변화를 이끄는 실천 안내서' FROM dual
UNION ALL SELECT 'DEPRESSION', '자존감 수업', '윤홍균', '심플라이프', 'https://product.kyobobook.co.kr/detail/S000001891815', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9791186757093.jpg', '마음의 길을 밝혀주는 현실적인 자존감 이정표' FROM dual
UNION ALL SELECT 'DEPRESSION', '홀로서기 심리학', '라라 E. 필딩', '메이븐', 'https://product.kyobobook.co.kr/detail/S000001941412', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9791190538206.jpg', '흔들리는 마음을 다독이며 스스로 단단하게 설 수 있도록 이끄는 심리 안내서' FROM dual
UNION ALL SELECT 'DEPRESSION', '아무것도 하지 않으면 아무 일도 일어나지 않는다', '기시미 이치로', '살림', 'https://product.kyobobook.co.kr/detail/S000000722112', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9788952235107.jpg', '생각만 하던 나를 행동으로 이끄는 실천의 심리학' FROM dual
UNION ALL SELECT 'DEPRESSION', '당신이 옳다', '정혜신', '해냄출판사', 'https://product.kyobobook.co.kr/detail/S000001026048', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9788965746669.jpg', '상대와 나를 이해하는 진정한 공감의 지혜를 전하는 책' FROM dual
UNION ALL SELECT 'ANXIETY', '불안', '알랭 드 보통', '은행나무', 'https://product.kyobobook.co.kr/detail/S000000828225', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9788956605593.jpg', '불안의 근원을 찾아 객관적 통찰과 깊은 위안을 주는 책' FROM dual
UNION ALL SELECT 'ANXIETY', '과거가 남긴 우울 미래가 보낸 불안', '김아라', '유노북스', 'https://product.kyobobook.co.kr/detail/S000061451536', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9791192300214.jpg', '불안과 우울을 넘어 현재에 집중하도록 돕는 다정한 마음 지침서' FROM dual
UNION ALL SELECT 'ANXIETY', '불안을 알면 흔들리지 않는다', '키렌 슈나크', '오픈도어북스', 'https://product.kyobobook.co.kr/detail/S000218204279', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9791173741975.jpg', '불안의 본질을 이해하고 흔들림 속에서도 단단함을 찾는 안내서' FROM dual
UNION ALL SELECT 'ANXIETY', '당신의 불안은 죄가 없다', '웬디 스즈키', '21세기북스', 'https://product.kyobobook.co.kr/detail/S000213834268', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9791171176670.jpg', NULL FROM dual
UNION ALL SELECT 'RELATIONSHIP', '나는 왜 네 말이 힘들까', '박재연', '한빛라이프', 'https://product.kyobobook.co.kr/detail/S000001944783', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9791190846011.jpg', '관계 속 상처를 보듬고 건강한 대화를 이끄는 통찰의 안내서' FROM dual
UNION ALL SELECT 'RELATIONSHIP', '말센스', '셀레스트 헤들리', '스몰빅라이프', 'https://product.kyobobook.co.kr/detail/S000001898255', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9791187165460.jpg', '대화의 본질을 깨닫고 관계를 변화시키는 안내서' FROM dual
UNION ALL SELECT 'RELATIONSHIP', '나는 왜 남들보다 쉽게 지칠까', '최재훈', '서스테인', 'https://product.kyobobook.co.kr/detail/S000213675097', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9791193388075.jpg', '쉽게 지치는 나를 이해하고 보듬어주는 다정한 안내서' FROM dual
UNION ALL SELECT 'RELATIONSHIP', '데일 카네기 인간관계론', '데일 카네기', '현대지성', 'https://product.kyobobook.co.kr/detail/S000001897788', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9791187142560.jpg', NULL FROM dual
UNION ALL SELECT 'RELATIONSHIP', '미움받을 용기', '기시미 이치로, 고가 후미타케', '인플루엔셜', 'https://product.kyobobook.co.kr/detail/S000200555616', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9791168340770.jpg', '관계 속에서 나를 찾고 행복할 용기를 주는 아들러 심리학 안내서' FROM dual
UNION ALL SELECT 'LETHARGY', '나는 왜 무기력을 되풀이하는가', '에리히 프롬', '나무생각', 'https://product.kyobobook.co.kr/detail/S000001890530', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9791186688519.jpg', NULL FROM dual
UNION ALL SELECT 'LETHARGY', '어느 날 갑자기 무기력이 찾아왔다', '클라우스 베른하르트', '동녘라이프', 'https://product.kyobobook.co.kr/detail/S000001449403', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9788990514776.jpg', NULL FROM dual
UNION ALL SELECT 'LETHARGY', '무기력이 무기력해지도록', '한창수', '알에이치코리아', 'https://product.kyobobook.co.kr/detail/S000000479280', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9788925579863.jpg', '무기력의 실체를 이해하고 극복의 길을 제시하는 전문적인 안내서' FROM dual
UNION ALL SELECT 'LETHARGY', '지쳤거나 좋아하는 게 없거나', '글배우', '강한별', 'https://product.kyobobook.co.kr/detail/S000001986809', 'https://contents.kyobobook.co.kr/sih/fit-in/458x0/pdt/9791197472558.jpg', '지친 마음을 따뜻하게 위로하고 다시 일어설 용기를 주는 에세이' FROM dual;

COMMIT;
