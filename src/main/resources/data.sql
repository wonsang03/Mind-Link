-- 샘플 게시글 (커뮤니티)
INSERT INTO posts (author, title, content, category, likes, created_at)
VALUES
('익명123', '불안할 때 도움이 되는 호흡법 공유합니다',
 '최근 불안감이 심할 때 4-7-8 호흡법을 알게 됐어요. 4초 들이마시고, 7초 참고, 8초 내뱉기. 정말 도움이 되더라고요. 특히 잠들기 전이나 급하게 불안해질 때 효과적이었어요. 여러분도 한번 시도해보세요!',
 '스트레스 관리', 24, CURRENT_TIMESTAMP);

INSERT INTO posts (author, title, content, category, likes, created_at)
VALUES
('힐링중', '오늘 처음으로 상담을 받았어요',
 '용기내서 전문 상담을 받았는데, 생각보다 훨씬 편안했습니다. 혼자 고민하지 말고 도움을 받는 게 중요한 것 같아요. 처음엔 두려웠지만 이제는 정말 잘한 선택이라고 생각합니다.',
 '경험 공유', 45, CURRENT_TIMESTAMP);

INSERT INTO posts (author, title, content, category, likes, created_at)
VALUES
('새출발', '감사 일기 30일 챌린지 같이 하실 분!',
 '매일 감사한 일 3가지씩 기록하는 챌린지 시작합니다. 함께 하실 분 있으면 댓글 남겨주세요 :) 작은 것에도 감사하는 마음을 가지면 삶이 달라진다고 하더라고요.',
 '함께 해요', 38, CURRENT_TIMESTAMP);

-- 샘플 댓글
INSERT INTO post_comments (post_id, author, content, created_at)
VALUES (1, '응원해요', '좋은 정보 감사합니다! 저도 한번 시도해볼게요.', CURRENT_TIMESTAMP);

INSERT INTO post_comments (post_id, author, content, created_at)
VALUES (1, '함께해요', '정말 도움이 되는 글이네요. 저도 비슷한 경험이 있어서 공감됩니다.', CURRENT_TIMESTAMP);

INSERT INTO post_comments (post_id, author, content, created_at)
VALUES (2, '용기있어요', '대단하세요! 저도 용기 내볼게요.', CURRENT_TIMESTAMP);

INSERT INTO post_comments (post_id, author, content, created_at)
VALUES (3, '참여할게요', '저도 같이 해요! 화이팅!', CURRENT_TIMESTAMP);
