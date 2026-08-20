package com.roompick.testsupport;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트 전체가 공유하는 단일 MySQL Testcontainers 인스턴스입니다.
 *
 * 테스트 클래스마다 컨테이너를 새로 기동하던 기존 방식 대신 JVM에 하나만
 * 기동하고, 클래스별로 독립된 데이터베이스를 생성해 사용합니다. 각 클래스는
 * 기존과 동일하게 자신만의 DATABASE_NAME으로 스키마를 격리하므로 데이터
 * 오염 없이 컨테이너 기동 오버헤드만 줄어듭니다.
 */
public final class SharedMySqlTestContainer {

    public static final String USERNAME = "roompick";

    public static final String PASSWORD = "roompick-password";

    /*
     * Testcontainers의 MySQLContainer#configure()는 지정한 사용자 비밀번호를
     * MYSQL_ROOT_PASSWORD에도 그대로 적용해 root 비밀번호를 덮어씁니다.
     * 별도의 root 비밀번호를 지정해도 무시되므로 같은 값을 그대로 사용합니다.
     */
    private static final String ROOT_PASSWORD = PASSWORD;

    private static final int MYSQL_PORT = 3306;

    public static final MySQLContainer<?> INSTANCE;

    static {
        INSTANCE = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .withDatabaseName("roompick_shared_test")
            /*
             * 기본 root 계정은 'root'@'localhost'에만 생성되어
             * 컨테이너 밖에서 접속하는 Testcontainers 연결은 거부됩니다.
             * 임의의 호스트에서도 root로 접속할 수 있도록 허용합니다.
             */
            .withEnv("MYSQL_ROOT_HOST", "%")
            .withStartupTimeout(Duration.ofMinutes(2));

        INSTANCE.start();
        grantGlobalPrivilegesToTestUser();
    }

    private SharedMySqlTestContainer() {
    }

    /**
     * 요청한 이름의 데이터베이스가 없으면 생성합니다.
     *
     * 각 통합 테스트 클래스가 컨텍스트를 초기화하기 전에 호출해
     * 자신만의 스키마를 준비합니다.
     */
    public static void createDatabaseIfAbsent(String databaseName) {
        try (
            Connection connection = DriverManager.getConnection(
                INSTANCE.getJdbcUrl(), USERNAME, PASSWORD
            );
            Statement statement = connection.createStatement()
        ) {
            statement.execute(
                "CREATE DATABASE IF NOT EXISTS `" + databaseName + "`"
            );
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "공유 MySQL 테스트 컨테이너에 데이터베이스를 생성하지 못했습니다: "
                    + databaseName,
                exception
            );
        }
    }

    public static String jdbcUrl(String databaseName) {
        return "jdbc:mysql://"
            + INSTANCE.getHost()
            + ":"
            + INSTANCE.getMappedPort(MYSQL_PORT)
            + "/"
            + databaseName
            + "?useSSL=false&allowPublicKeyRetrieval=true"
            + "&characterEncoding=UTF-8&serverTimezone=Asia/Seoul";
    }

    /**
     * roompick 계정은 기본적으로 컨테이너 기동 시 생성된 단일 데이터베이스에만
     * 권한이 있으므로, 테스트 클래스별로 새 데이터베이스를 만들고 접근할 수
     * 있도록 root 계정으로 전역 권한을 부여합니다.
     */
    private static void grantGlobalPrivilegesToTestUser() {
        try (
            Connection connection = DriverManager.getConnection(
                INSTANCE.getJdbcUrl(), "root", ROOT_PASSWORD
            );
            Statement statement = connection.createStatement()
        ) {
            statement.execute(
                "GRANT ALL PRIVILEGES ON *.* TO '" + USERNAME + "'@'%' WITH GRANT OPTION"
            );
            statement.execute("FLUSH PRIVILEGES");
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "공유 MySQL 테스트 컨테이너에 전역 권한을 부여하지 못했습니다.",
                exception
            );
        }
    }
}
