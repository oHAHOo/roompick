-- 관리자 더미 계정
-- 로그인: admin@roompick.com / Admin1234!
-- 운영 환경 배포 전 반드시 비밀번호를 변경한다.
INSERT INTO members (email, password, name, role, created_at, updated_at)
VALUES (
    'admin@roompick.com',
    '$2a$10$.hlnMB4QrdIRISROmkl7m.oOzSvlSy77FsMz4DqKcukKUBoV4yqrW',
    '관리자',
    'ADMIN',
    CURRENT_TIMESTAMP(6),
    CURRENT_TIMESTAMP(6)
);
