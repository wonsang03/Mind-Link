-- =============================================================================
-- Mind-Link · 클러스터링 실사용자 샘플 3명 (이름 검색·3D 맵 테스트용)
--
-- 전제:
--   1) sql/ORACLE_SETUP.sql 실행됨 (users 테이블)
--   2) sql/CHAT_CLUSTERING.sql 실행됨 (user_assessment_profiles 테이블)
--
-- 로그인 (3명 공통 비밀번호): admin1234  (ORACLE_SETUP 관리자와 동일 bcrypt)
--   · 김민수  cluster.demo1@mindlink.com
--   · 이서연  cluster.demo2@mindlink.com
--   · 박준혁  cluster.demo3@mindlink.com
--
-- 실행 후: 앱에서 ADMIN 으로 POST /api/chat-cluster/recompute 1회 권장
--          (cluster_id 를 K-Means 결과로 맞춤)
-- =============================================================================

ALTER SESSION SET NLS_LENGTH_SEMANTICS=CHAR;

-- 동일 이메일 재실행 시 프로필·계정 갱신
DECLARE
  v_pw VARCHAR2(255) := '$2a$10$cjwUlGVCVIxzYLVt7eP3XegA4dxbZtULyD5aPCUrnS.jfKkU3U23G';
BEGIN
  MERGE INTO users u
  USING (SELECT 'cluster.demo1@mindlink.com' AS email FROM dual) s
  ON (u.email = s.email)
  WHEN MATCHED THEN UPDATE SET u.name = '김민수', u.password = v_pw, u.role = 'USER'
  WHEN NOT MATCHED THEN
    INSERT (name, email, password, role, created_at)
    VALUES ('김민수', 'cluster.demo1@mindlink.com', v_pw, 'USER', SYSTIMESTAMP);

  MERGE INTO users u
  USING (SELECT 'cluster.demo2@mindlink.com' AS email FROM dual) s
  ON (u.email = s.email)
  WHEN MATCHED THEN UPDATE SET u.name = '이서연', u.password = v_pw, u.role = 'USER'
  WHEN NOT MATCHED THEN
    INSERT (name, email, password, role, created_at)
    VALUES ('이서연', 'cluster.demo2@mindlink.com', v_pw, 'USER', SYSTIMESTAMP);

  MERGE INTO users u
  USING (SELECT 'cluster.demo3@mindlink.com' AS email FROM dual) s
  ON (u.email = s.email)
  WHEN MATCHED THEN UPDATE SET u.name = '박준혁', u.password = v_pw, u.role = 'USER'
  WHEN NOT MATCHED THEN
    INSERT (name, email, password, role, created_at)
    VALUES ('박준혁', 'cluster.demo3@mindlink.com', v_pw, 'USER', SYSTIMESTAMP);
END;
/

-- user_assessment_profiles (실사용자 is_synthetic=0)
-- 점수: PSS 0~40, PHQ-9 0~27, GAD-7 0~21 / norm = 점수÷만점
DECLARE
  PROCEDURE upsert_profile(
    p_email       VARCHAR2,
    p_key         VARCHAR2,
    p_label       VARCHAR2,
    p_story       VARCHAR2,
    p_stress      NUMBER,
    p_depression  NUMBER,
    p_anxiety     NUMBER,
    p_cluster     NUMBER
  ) IS
    v_uid NUMBER;
  BEGIN
    SELECT id INTO v_uid FROM users WHERE email = p_email;

    DELETE FROM user_assessment_profiles
     WHERE user_id = v_uid AND is_synthetic = 0;

    INSERT INTO user_assessment_profiles (
      user_id, persona_key, persona_label, persona_story,
      stress_score, depression_score, anxiety_score,
      stress_norm, depression_norm, anxiety_norm,
      cluster_id, is_synthetic, created_at, updated_at
    ) VALUES (
      v_uid, p_key, p_label, p_story,
      p_stress, p_depression, p_anxiety,
      ROUND(p_stress / 40, 4),
      ROUND(p_depression / 27, 4),
      ROUND(p_anxiety / 21, 4),
      p_cluster, 0, SYSTIMESTAMP, SYSTIMESTAMP
    );
  END;
BEGIN
  -- 안정·낮은 3축
  upsert_profile(
    'cluster.demo1@mindlink.com',
    'real_kim_minsu',
    '김민수',
    '시험 기간이지만 수면·운동 루틴을 유지하는 편이에요.',
    11, 5, 4,
    0
  );

  -- 스트레스 우세
  upsert_profile(
    'cluster.demo2@mindlink.com',
    'real_lee_seoyeon',
    '이서연',
    '인턴·학업 병행으로 피로와 긴장이 높은 상태예요.',
    33, 11, 9,
    1
  );

  -- 우울·불안 복합
  upsert_profile(
    'cluster.demo3@mindlink.com',
    'real_park_junhyuk',
    '박준혁',
    '취업 준비가 길어지며 무기력과 걱정이 함께 올라왔어요.',
    19, 21, 16,
    4
  );
END;
/

-- 확인
SELECT u.id, u.name, u.email,
       p.stress_score, p.depression_score, p.anxiety_score,
       p.stress_norm, p.depression_norm, p.anxiety_norm,
       p.cluster_id, p.is_synthetic
  FROM users u
  JOIN user_assessment_profiles p ON p.user_id = u.id AND p.is_synthetic = 0
 WHERE u.email LIKE 'cluster.demo%@mindlink.com'
 ORDER BY u.id;
