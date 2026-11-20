package com.marketingagent.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.regex.Pattern;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * MySQL 검증용 테스트 컨테이너. 한 번 띄운 컨테이너를 모든 MySQL 테스트가 같이 쓰고, 테스트 클래스마다 빈 DB 를 따로 만든다.
 *
 * <p>이미지는 시스템 속성 {@code mysql.image} 로 정한다(기본 mysql:8.0.46). 운영 버전과 같다는 뜻은 아니다.
 * 컨테이너는 테스트 JVM 이 끝나면 Testcontainers 가 지운다. 로컬 MySQL 서비스와 H2 파일 DB 는 건드리지 않는다.
 */
public final class MySqlTestDatabases {

    private static final Pattern SAFE_NAME = Pattern.compile("[a-z][a-z0-9_]{0,40}");

    private static final MySQLContainer<?> CONTAINER = new MySQLContainer<>(
            DockerImageName.parse(System.getProperty("mysql.image", "mysql:8.0.46")).asCompatibleSubstituteFor("mysql"))
            .withUsername("root")
            .withPassword("test-only-root");

    private MySqlTestDatabases() {
    }

    public static synchronized MySQLContainer<?> container() {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        return CONTAINER;
    }

    /** 운영 안내(scripts/setup-mysql.sql)와 같은 문자셋으로 빈 DB 를 만들고 JDBC URL 을 돌려준다. */
    public static String newDatabase(String name) {
        if (!SAFE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("DB 이름은 소문자·숫자·밑줄만: " + name);
        }
        MySQLContainer<?> c = container();
        try (Connection connection = DriverManager.getConnection(serverUrl(), c.getUsername(), c.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + name + "` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci");
        } catch (Exception e) {
            throw new IllegalStateException("테스트 DB 생성 실패: " + name, e);
        }
        return url(name);
    }

    public static String url(String name) {
        MySQLContainer<?> c = container();
        return "jdbc:mysql://" + c.getHost() + ":" + c.getMappedPort(MySQLContainer.MYSQL_PORT) + "/" + name
                + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8";
    }

    public static String username() {
        return container().getUsername();
    }

    public static String password() {
        return container().getPassword();
    }

    /** 운영 MySQL 설정(.env.example)과 같게: Flyway 로 만들고 Hibernate 는 검증만 한다. */
    public static void register(DynamicPropertyRegistry registry, String database) {
        registerUrl(registry, newDatabase(database));
    }

    /** 미리 준비해 둔 DB(예: V5 까지 올리고 데이터를 넣은 DB)로 애플리케이션을 띄울 때. */
    public static void registerUrl(DynamicPropertyRegistry registry, String url) {
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", MySqlTestDatabases::username);
        registry.add("spring.datasource.password", MySqlTestDatabases::password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        // 진단용으로만 -PmysqlDdlAuto=none 처럼 바꿀 수 있다. 기본은 운영과 같은 validate.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> System.getProperty("mysql.ddl-auto", "validate"));
    }

    private static String serverUrl() {
        MySQLContainer<?> c = CONTAINER;
        return "jdbc:mysql://" + c.getHost() + ":" + c.getMappedPort(MySQLContainer.MYSQL_PORT)
                + "/?serverTimezone=Asia/Seoul&characterEncoding=UTF-8";
    }
}
