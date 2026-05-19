-- 테스트용 관리자 계정 (이메일: admin@mindlink.com / 비밀번호: admin1234)
INSERT INTO users (name, email, password, role, created_at)
VALUES ('관리자', 'admin@mindlink.com',
        '$2a$10$cjwUlGVCVIxzYLVt7eP3XegA4dxbZtULyD5aPCUrnS.jfKkU3U23G',
        'ADMIN', CURRENT_TIMESTAMP);

-- 샘플 공지사항
INSERT INTO notices (category, title, summary, content, created_at)
VALUES ('중요', 'AI 기반 정서 케어 베타 오픈 안내',
        'AI가 감정을 분석하고 단계별 케어를 제공하는 새로운 서비스가 베타 오픈되었습니다.',
        '안녕하세요, 마음이음입니다.

많은 분들이 기다려주신 ''AI 기반 정서 케어'' 서비스가 베타 오픈되었습니다.
AI가 사용자의 메시지를 분석해 감정 상태와 위험도를 판단하고, 단계별로 맞춤 케어를 제공합니다.

■ 주요 기능
  - 실시간 감정 분석 및 위험도 단계 제시
  - 위험도에 따른 맞춤형 추천 (호흡법, 활동, 전문 상담 연계)
  - 고위험 상황에서 긴급 연락처 자동 안내

■ 이용 방법
  상단의 ''AI 정서 케어'' 메뉴에서 바로 이용하실 수 있습니다.

베타 기간 동안 발견되는 개선점은 지속적으로 반영하겠습니다. 많은 관심과 의견 부탁드립니다.',
        DATEADD('DAY', -9, CURRENT_TIMESTAMP));

INSERT INTO notices (category, title, summary, content, created_at)
VALUES ('서비스', '상담소 찾기 기능이 새롭게 개편되었습니다',
        '지도 기반의 보기 좋은 카드 형태로 상담 센터를 더 빠르게 찾아볼 수 있습니다.',
        '안녕하세요, 마음이음입니다.

상담소 찾기 페이지가 새롭게 단장했습니다.

■ 개편 내용
  - 가로형 카드 디자인 (썸네일 + 평점/거리/영업상태 + 위치/시간/전화)
  - 센터 유형별 필터링 (심리상담센터, 정신건강복지센터 등)
  - 키워드 검색 (센터명, 지역, 전문 분야)

앞으로도 더 편리한 사용 경험을 제공하기 위해 계속해서 개선해 나가겠습니다.',
        DATEADD('DAY', -17, CURRENT_TIMESTAMP));

INSERT INTO notices (category, title, summary, content, created_at)
VALUES ('점검', '정기 시스템 점검 안내',
        '매월 둘째 주 화요일 오전 2시~4시 정기 점검이 진행됩니다.',
        '안녕하세요, 마음이음입니다.

서비스 안정성을 높이기 위해 정기 점검을 진행합니다.

■ 점검 일정
  매월 둘째 주 화요일 02:00 ~ 04:00 (KST)

■ 점검 내용
  - 인프라 보안 패치
  - 데이터베이스 백업 및 무결성 점검
  - 신규 기능 배포

점검 시간 동안에는 서비스 이용이 일시적으로 제한될 수 있습니다.',
        DATEADD('DAY', -31, CURRENT_TIMESTAMP));

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
