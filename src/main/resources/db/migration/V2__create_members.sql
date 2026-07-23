-- 회원 테이블
CREATE TABLE members
(
    member_id  BIGINT       NOT NULL AUTO_INCREMENT,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    role       VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,

    CONSTRAINT pk_members
        PRIMARY KEY (member_id),

    -- 이메일은 로그인 식별자이므로 중복될 수 없습니다.
    CONSTRAINT uk_members_email
        UNIQUE (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
