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

-- =============================================
-- 자가진단 타입
-- =============================================
INSERT INTO assessment_types (name, description, type_key, duration) VALUES ('우울증 자가진단', 'PHQ-9 기반 우울 증상 선별 검사', 'depression', '약 2분');
INSERT INTO assessment_types (name, description, type_key, duration) VALUES ('불안장애 자가진단', 'GAD-7 기반 불안 수준 평가', 'anxiety', '약 2분');
INSERT INTO assessment_types (name, description, type_key, duration) VALUES ('스트레스 자가진단', 'PSS 기반 스트레스 측정', 'stress', '약 2분');
INSERT INTO assessment_types (name, description, type_key, duration) VALUES ('번아웃 자가진단', 'CBI 척도 기반 개인·업무 번아웃 측정', 'burnout', '약 3분');

-- =============================================
-- 문항 (depression: 9문항 / anxiety: 7문항 / stress: 10문항 / burnout: 13문항)
-- =============================================
-- 우울증 (PHQ-9: 9문항)
INSERT INTO assessment_questions (assessment_type_id, order_num, content) SELECT id, 1, '일상적인 일에 대한 흥미나 즐거움이 거의 없었다' FROM assessment_types WHERE type_key = 'depression';
INSERT INTO assessment_questions (assessment_type_id, order_num, content) SELECT id, 2, '우울하거나, 기분이 가라앉거나, 희망이 없다고 느꼈다' FROM assessment_types WHERE type_key = 'depression';
INSERT INTO assessment_questions (assessment_type_id, order_num, content) SELECT id, 3, '잠들기 어렵거나 자주 깼거나, 반대로 너무 많이 잤다' FROM assessment_types WHERE type_key = 'depression';
INSERT INTO assessment_questions (assessment_type_id, order_num, content) SELECT id, 4, '피곤하거나 기운이 거의 없다고 느꼈다' FROM assessment_types WHERE type_key = 'depression';
INSERT INTO assessment_questions (assessment_type_id, order_num, content) SELECT id, 5, '식욕이 없거나 반대로 과식을 했다' FROM assessment_types WHERE type_key = 'depression';
INSERT INTO assessment_questions (assessment_type_id, order_num, content) SELECT id, 6, '자신이 실패자라고 느끼거나, 자신 또는 가족을 실망시켰다고 느꼈다' FROM assessment_types WHERE type_key = 'depression';
INSERT INTO assessment_questions (assessment_type_id, order_num, content) SELECT id, 7, '신문을 읽거나 TV를 보는 것 같은 일에 집중하기 어려웠다' FROM assessment_types WHERE type_key = 'depression';
INSERT INTO assessment_questions (assessment_type_id, order_num, content) SELECT id, 8, '다른 사람이 눈치챌 정도로 말이나 행동이 느려졌거나, 반대로 너무 안절부절못하고 평소보다 많이 움직였다' FROM assessment_types WHERE type_key = 'depression';
INSERT INTO assessment_questions (assessment_type_id, order_num, content) SELECT id, 9, '차라리 죽는 것이 낫겠다고 생각했거나, 자신을 해칠 생각을 했다' FROM assessment_types WHERE type_key = 'depression';

-- 불안장애 (GAD-7: 7문항)
INSERT INTO assessment_questions (assessment_type_id, order_num, content) SELECT id, 1, '불안하거나 초조하거나 조마조마한 느낌이 들었다' FROM assessment_types WHERE type_key = 'anxiety';
INSERT INTO assessment_questions (assessment_type_id, order_num, content) SELECT id, 2, '걱정을 멈추거나 조절할 수 없었다' FROM assessment_types WHERE type_key = 'anxiety';
INSERT INTO assessment_questions (assessment_type_id, order_num, content) SELECT id, 3, '여러 가지 일에 대해 지나치게 걱정하였다' FROM assessment_types WHERE type_key = 'anxiety';
INSERT INTO assessment_questions (assessment_type_id, order_num, content) SELECT id, 4, '긴장을 풀기가 어려웠다' FROM assessment_types WHERE type_key = 'anxiety';
INSERT INTO assessment_questions (assessment_type_id, order_num, content) SELECT id, 5, '너무 안절부절못해서 가만히 앉아 있기가 힘들었다' FROM assessment_types WHERE type_key = 'anxiety';
INSERT INTO assessment_questions (assessment_type_id, order_num, content) SELECT id, 6, '쉽게 짜증이 나거나 화가 났다' FROM assessment_types WHERE type_key = 'anxiety';
INSERT INTO assessment_questions (assessment_type_id, order_num, content) SELECT id, 7, '끔찍한 일이 일어날 것 같은 두려움을 느꼈다' FROM assessment_types WHERE type_key = 'anxiety';

-- 스트레스 (PSS-10: 10문항, 역채점 4·5·7·8번)
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed) SELECT id, 1,  '예상치 못한 일이 생겨서 속상했다', false FROM assessment_types WHERE type_key = 'stress';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed) SELECT id, 2,  '삶에서 중요한 일들을 통제할 수 없다고 느꼈다', false FROM assessment_types WHERE type_key = 'stress';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed) SELECT id, 3,  '불안하고 스트레스를 받는다고 느꼈다', false FROM assessment_types WHERE type_key = 'stress';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed) SELECT id, 4,  '개인적인 문제를 처리하는 능력에 자신감을 느꼈다', true FROM assessment_types WHERE type_key = 'stress';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed) SELECT id, 5,  '일이 내 뜻대로 잘 풀린다고 느꼈다', true FROM assessment_types WHERE type_key = 'stress';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed) SELECT id, 6,  '해야 할 모든 일들을 감당할 수 없다고 느꼈다', false FROM assessment_types WHERE type_key = 'stress';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed) SELECT id, 7,  '삶의 짜증스러운 일들을 잘 다스릴 수 있었다', true FROM assessment_types WHERE type_key = 'stress';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed) SELECT id, 8,  '상황을 잘 장악하고 있다고 느꼈다', true FROM assessment_types WHERE type_key = 'stress';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed) SELECT id, 9,  '내가 통제할 수 없는 일들 때문에 화가 났다', false FROM assessment_types WHERE type_key = 'stress';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed) SELECT id, 10, '어려움이 너무 쌓여서 극복할 수 없다고 느꼈다', false FROM assessment_types WHERE type_key = 'stress';

-- 번아웃 (CBI: Part1 개인 6문항 + Part2 업무 7문항, 역채점 10번)
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed, part) SELECT id, 1,  '얼마나 자주 피로감을 느끼십니까?', false, 1 FROM assessment_types WHERE type_key = 'burnout';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed, part) SELECT id, 2,  '얼마나 자주 신체적으로 탈진된다고 느끼십니까?', false, 1 FROM assessment_types WHERE type_key = 'burnout';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed, part) SELECT id, 3,  '얼마나 자주 감정적으로 탈진된다고 느끼십니까?', false, 1 FROM assessment_types WHERE type_key = 'burnout';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed, part) SELECT id, 4,  '얼마나 자주 "더 이상 못 하겠다"는 생각이 드십니까?', false, 1 FROM assessment_types WHERE type_key = 'burnout';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed, part) SELECT id, 5,  '얼마나 자주 기진맥진하다고 느끼십니까?', false, 1 FROM assessment_types WHERE type_key = 'burnout';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed, part) SELECT id, 6,  '얼마나 자주 몸이 약해지고 쉽게 아플 것 같다고 느끼십니까?', false, 1 FROM assessment_types WHERE type_key = 'burnout';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed, part) SELECT id, 7,  '하루 일과가 끝날 때 기진맥진하다고 느끼십니까?', false, 2 FROM assessment_types WHERE type_key = 'burnout';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed, part) SELECT id, 8,  '아침에 또 하루를 출근해야 한다는 생각에 탈진된다고 느끼십니까?', false, 2 FROM assessment_types WHERE type_key = 'burnout';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed, part) SELECT id, 9,  '업무 시간 매 순간이 힘들다고 느끼십니까?', false, 2 FROM assessment_types WHERE type_key = 'burnout';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed, part) SELECT id, 10, '퇴근 후 가족이나 친구들과 함께할 충분한 에너지가 있습니까?', true, 2 FROM assessment_types WHERE type_key = 'burnout';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed, part) SELECT id, 11, '업무가 감정적으로 소진되게 만듭니까?', false, 2 FROM assessment_types WHERE type_key = 'burnout';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed, part) SELECT id, 12, '업무로 인해 좌절감을 느끼십니까?', false, 2 FROM assessment_types WHERE type_key = 'burnout';
INSERT INTO assessment_questions (assessment_type_id, order_num, content, reversed, part) SELECT id, 13, '업무 때문에 번아웃된다고 느끼십니까?', false, 2 FROM assessment_types WHERE type_key = 'burnout';

-- =============================================
-- 선택지 (depression/anxiety: 4선택지 0~3점 / stress: 5선택지 0~4점 / burnout: 5선택지 0/25/50/75/100점)
-- =============================================
INSERT INTO assessment_choices (assessment_type_id, order_num, label, score) SELECT id, 1, '전혀없음', 0 FROM assessment_types WHERE type_key = 'depression';
INSERT INTO assessment_choices (assessment_type_id, order_num, label, score) SELECT id, 2, '며칠 동안', 1 FROM assessment_types WHERE type_key = 'depression';
INSERT INTO assessment_choices (assessment_type_id, order_num, label, score) SELECT id, 3, '7일 이상', 2 FROM assessment_types WHERE type_key = 'depression';
INSERT INTO assessment_choices (assessment_type_id, order_num, label, score) SELECT id, 4, '거의 매일', 3 FROM assessment_types WHERE type_key = 'depression';

INSERT INTO assessment_choices (assessment_type_id, order_num, label, score) SELECT id, 1, '전혀없음', 0 FROM assessment_types WHERE type_key = 'anxiety';
INSERT INTO assessment_choices (assessment_type_id, order_num, label, score) SELECT id, 2, '며칠 동안', 1 FROM assessment_types WHERE type_key = 'anxiety';
INSERT INTO assessment_choices (assessment_type_id, order_num, label, score) SELECT id, 3, '7일 이상', 2 FROM assessment_types WHERE type_key = 'anxiety';
INSERT INTO assessment_choices (assessment_type_id, order_num, label, score) SELECT id, 4, '거의 매일', 3 FROM assessment_types WHERE type_key = 'anxiety';

INSERT INTO assessment_choices (assessment_type_id, order_num, label, score) SELECT id, 1, '전혀없음', 0 FROM assessment_types WHERE type_key = 'stress';
INSERT INTO assessment_choices (assessment_type_id, order_num, label, score) SELECT id, 2, '거의 없음', 1 FROM assessment_types WHERE type_key = 'stress';
INSERT INTO assessment_choices (assessment_type_id, order_num, label, score) SELECT id, 3, '때때로',   2 FROM assessment_types WHERE type_key = 'stress';
INSERT INTO assessment_choices (assessment_type_id, order_num, label, score) SELECT id, 4, '자주',     3 FROM assessment_types WHERE type_key = 'stress';
INSERT INTO assessment_choices (assessment_type_id, order_num, label, score) SELECT id, 5, '매우 자주', 4 FROM assessment_types WHERE type_key = 'stress';

INSERT INTO assessment_choices (assessment_type_id, order_num, label, score) SELECT id, 1, '전혀없음',  0   FROM assessment_types WHERE type_key = 'burnout';
INSERT INTO assessment_choices (assessment_type_id, order_num, label, score) SELECT id, 2, '거의 없음', 25  FROM assessment_types WHERE type_key = 'burnout';
INSERT INTO assessment_choices (assessment_type_id, order_num, label, score) SELECT id, 3, '때때로',    50  FROM assessment_types WHERE type_key = 'burnout';
INSERT INTO assessment_choices (assessment_type_id, order_num, label, score) SELECT id, 4, '자주',      75  FROM assessment_types WHERE type_key = 'burnout';
INSERT INTO assessment_choices (assessment_type_id, order_num, label, score) SELECT id, 5, '매우 자주', 100 FROM assessment_types WHERE type_key = 'burnout';

-- =============================================
-- 점수 범위
--   depression(PHQ-9):  0~4 최소 / 5~9 경증 / 10~14 중등도 / 15~19 중등도-중증 / 20~27 중증
--   anxiety(GAD-7):     0~4 최소 / 5~9 경증 / 10~14 중등도 / 15~21 중증
--   stress(PSS-10):     0~13 낮음 / 14~26 보통 / 27~40 높음
--   burnout(CBI):       파트별 평균 — 0~49 낮음 / 50~74 보통 / 75~100 높음
-- =============================================
INSERT INTO score_ranges (assessment_type_id, min_score, max_score, level, message) SELECT id, 0, 4, '최소', '최소한의 우울 증상이 있습니다. 일상적인 자기 관리를 유지해주세요.' FROM assessment_types WHERE type_key = 'depression';
INSERT INTO score_ranges (assessment_type_id, min_score, max_score, level, message) SELECT id, 5, 9, '경증', '가벼운 우울 증상이 있습니다. 규칙적인 생활과 충분한 휴식에 신경 써주세요.' FROM assessment_types WHERE type_key = 'depression';
INSERT INTO score_ranges (assessment_type_id, min_score, max_score, level, message) SELECT id, 10, 14, '중등도', '중간 정도의 우울 증상이 있습니다. 전문가 상담을 고려해보세요.' FROM assessment_types WHERE type_key = 'depression';
INSERT INTO score_ranges (assessment_type_id, min_score, max_score, level, message) SELECT id, 15, 19, '중등도-중증', '상당한 우울 증상이 있습니다. 전문가의 도움을 받으시기를 권장합니다.' FROM assessment_types WHERE type_key = 'depression';
INSERT INTO score_ranges (assessment_type_id, min_score, max_score, level, message) SELECT id, 20, 27, '중증', '심각한 우울 증상이 있습니다. 즉각적인 전문가의 도움이 필요합니다.' FROM assessment_types WHERE type_key = 'depression';

INSERT INTO score_ranges (assessment_type_id, min_score, max_score, level, message) SELECT id, 0, 4, '최소', '최소한의 불안 증상이 있습니다. 일상적인 자기 관리를 유지해주세요.' FROM assessment_types WHERE type_key = 'anxiety';
INSERT INTO score_ranges (assessment_type_id, min_score, max_score, level, message) SELECT id, 5, 9, '경증', '가벼운 불안 증상이 있습니다. 이완 기법과 충분한 휴식을 취해보세요.' FROM assessment_types WHERE type_key = 'anxiety';
INSERT INTO score_ranges (assessment_type_id, min_score, max_score, level, message) SELECT id, 10, 14, '중등도', '중간 정도의 불안 증상이 있습니다. 전문가 상담을 고려해보세요.' FROM assessment_types WHERE type_key = 'anxiety';
INSERT INTO score_ranges (assessment_type_id, min_score, max_score, level, message) SELECT id, 15, 21, '중증', '심각한 불안 증상이 있습니다. 즉각적인 전문가의 도움이 필요합니다.' FROM assessment_types WHERE type_key = 'anxiety';

INSERT INTO score_ranges (assessment_type_id, min_score, max_score, level, message) SELECT id, 0,  13, '낮음', '스트레스 수준이 낮은 안정적인 상태입니다. 현재의 생활 습관을 유지해주세요.' FROM assessment_types WHERE type_key = 'stress';
INSERT INTO score_ranges (assessment_type_id, min_score, max_score, level, message) SELECT id, 14, 26, '보통', '보통 수준의 스트레스가 있습니다. 스트레스 원인을 파악하고 꾸준한 관리가 필요합니다.' FROM assessment_types WHERE type_key = 'stress';
INSERT INTO score_ranges (assessment_type_id, min_score, max_score, level, message) SELECT id, 27, 40, '높음', '심각한 스트레스 상태입니다. 즉각적인 휴식과 전문가의 도움이 필요합니다.' FROM assessment_types WHERE type_key = 'stress';

INSERT INTO score_ranges (assessment_type_id, min_score, max_score, level, message) SELECT id, 0,  49,  '낮음', '현재 번아웃 위험이 낮은 상태입니다. 균형 잡힌 생활을 유지하고 계십니다.' FROM assessment_types WHERE type_key = 'burnout';
INSERT INTO score_ranges (assessment_type_id, min_score, max_score, level, message) SELECT id, 50, 74,  '보통', '중간 수준의 소진이 나타나고 있습니다. 충분한 휴식과 꾸준한 관리가 필요합니다.' FROM assessment_types WHERE type_key = 'burnout';
INSERT INTO score_ranges (assessment_type_id, min_score, max_score, level, message) SELECT id, 75, 100, '높음', '번아웃 위험이 높은 상태입니다. 전문가 상담을 고려해 보시길 권장합니다.' FROM assessment_types WHERE type_key = 'burnout';
