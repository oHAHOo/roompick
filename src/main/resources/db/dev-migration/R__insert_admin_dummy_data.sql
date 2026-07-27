-- 로컬 개발 전용 관리자 더미 계정입니다. local 프로필의 spring.flyway.locations에서만 로드되며 prod에는 포함되지 않습니다.
-- 로그인: admin@roompick.com / Admin1234!
-- Repeatable Migration이므로 재실행 시 중복 삽입되지 않도록 존재 여부를 확인합니다.
INSERT INTO members (email, password, name, role, created_at, updated_at)
SELECT 'admin@roompick.com',
       '$2a$10$.hlnMB4QrdIRISROmkl7m.oOzSvlSy77FsMz4DqKcukKUBoV4yqrW',
       '관리자',
       'ADMIN',
       CURRENT_TIMESTAMP(6),
       CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (
    SELECT 1 FROM members WHERE email = 'admin@roompick.com'
);
