package top.ilovemyhome.zora.rdb.flyway;

import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.rdb.config.RdbConfig;
import top.ilovemyhome.zora.rdb.pool.DataSourcePoolBuilder;

import java.io.IOException;
import java.sql.*;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationRunnerPostgresTest {

    private EmbeddedPostgres embeddedPostgres;
    private HikariDataSource dataSource;
    private FlywayMigrationRunner runner;

    @BeforeEach
    void setUp() throws IOException {
        // Start embedded PostgreSQL
        embeddedPostgres = EmbeddedPostgres.builder()
            .setPort(0) // Random port
            .setServerConfig("log_connections", "on")
            .setServerConfig("log_disconnections", "on")
            .setServerConfig("log_min_messages", "debug1")
            .start();

        String jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres");

        RdbConfig config = RdbConfig.builder()
            .jdbcUrl(jdbcUrl)
            .username("postgres")
            .password("postgres")
            .driverClassName("org.postgresql.Driver")
            .build();

        dataSource = DataSourcePoolBuilder.create(config).build();
        runner = FlywayMigrationRunner.builder(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        DataSourcePoolBuilder.closeDataSource(dataSource);
        if (embeddedPostgres != null) {
            embeddedPostgres.close();
        }
    }

    @Test
    void testMigrate() {
        var result = runner.migrate();
        assertThat(result).isNotNull();
    }

    @Test
    void testHasPendingMigrationsBeforeMigration() {
        assertThat(runner.hasPendingMigrations()).isTrue();
        assertThat(runner.getPendingMigrationCount()).isEqualTo(2);
    }

    @Test
    void testHasNoPendingMigrationsAfterMigration() {
        runner.migrate();
        assertThat(runner.hasPendingMigrations()).isFalse();
        assertThat(runner.getPendingMigrationCount()).isEqualTo(0);
    }

    @Test
    void testTablesCreatedAfterMigration() throws SQLException {
        runner.migrate();

        try (Connection conn = dataSource.getConnection()) {

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET client_encoding = 'UTF8'");
                ResultSet rs = stmt.executeQuery("select version() as version");
                while (rs.next()) {
                    logger.info("Version={}.", rs.getString("version"));
                }
            }

            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(null, "public", "test_table", null);

            assertThat(tables.next()).isTrue();
            assertThat(tables.getString("TABLE_NAME")).isEqualTo("test_table");
        }
    }

    @Test
    void testValidateAfterMigration() {
        runner.migrate();
        runner.validate(); // Should not throw exception
    }

    @Test
    void testGetFlyway() {
        assertThat(runner.getFlyway()).isNotNull();
    }

    @Test
    void testWithPlaceholders() throws IOException {
        // Close the existing one
        DataSourcePoolBuilder.closeDataSource(dataSource);
        embeddedPostgres.close();

        // Start new embedded instance for this test
        embeddedPostgres = EmbeddedPostgres.builder()
            .setPort(0)
            .start();

        String jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres");

        RdbConfig config = RdbConfig.builder()
            .jdbcUrl(jdbcUrl)
            .username("postgres")
            .password("postgres")
            .driverClassName("org.postgresql.Driver")
            .build();
        dataSource = DataSourcePoolBuilder.create(config).build();

        runner = FlywayMigrationRunner.builder(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .placeholder("key1", "value1")
            .placeholder("key2", "value2")
            .build();

        assertThat(runner.getFlyway()).isNotNull();
        assertThat(runner.hasPendingMigrations()).isTrue();
        var result = runner.migrate();
        assertThat(result).isNotNull();
        assertThat(runner.hasPendingMigrations()).isFalse();
    }

    @Test
    void testPlaceholderReplacementInMigration() throws IOException, SQLException {
        // Close the existing one
        DataSourcePoolBuilder.closeDataSource(dataSource);
        embeddedPostgres.close();

        // Start new embedded instance for this test
        embeddedPostgres = EmbeddedPostgres.builder()
            .setPort(0)
            .start();

        String jdbcUrl = embeddedPostgres.getJdbcUrl("postgres", "postgres");

        RdbConfig config = RdbConfig.builder()
            .jdbcUrl(jdbcUrl)
            .username("postgres")
            .password("postgres")
            .driverClassName("org.postgresql.Driver")
            .build();
        dataSource = DataSourcePoolBuilder.create(config).build();

        // The migration script contains ${table_name} which will be replaced
        runner = FlywayMigrationRunner.builder(dataSource)
            .locations("classpath:db/migration_with_placeholder")
            .baselineOnMigrate(true)
            .placeholder("table_name", "dynamic_table")
            .build();

        assertThat(runner.hasPendingMigrations()).isTrue();
        var result = runner.migrate();
        assertThat(result).isNotNull();
        assertThat(runner.hasPendingMigrations()).isFalse();

        // Verify the table was actually created with the replaced name
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(null, "public", "dynamic_table", null);
            assertThat(tables.next()).isTrue();
            assertThat(tables.getString("TABLE_NAME")).isEqualTo("dynamic_table");
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(FlywayMigrationRunnerPostgresTest.class);
}
