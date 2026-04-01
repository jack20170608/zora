package top.ilovemyhome.zora.rdb.flyway;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.ilovemyhome.zora.rdb.config.RdbConfig;
import top.ilovemyhome.zora.rdb.pool.DataSourcePoolBuilder;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationRunnerTest {

    private HikariDataSource dataSource;
    private FlywayMigrationRunner runner;

    @BeforeEach
    void setUp() {
        RdbConfig config = RdbConfig.builder()
                .jdbcUrl("jdbc:h2:mem:flywaytest")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();

        dataSource = DataSourcePoolBuilder.create(config).build();
        runner = FlywayMigrationRunner.builder(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        DataSourcePoolBuilder.closeDataSource(dataSource);
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
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(null, null, "TEST_TABLE", null);

            assertThat(tables.next()).isTrue();
            assertThat(tables.getString("TABLE_NAME")).isEqualTo("TEST_TABLE");
        }
    }

    @Test
    void testValidateAfterMigration() {
        runner.migrate();
        runner.validate(); // Should not throw exception
    }

    @Test
    void testCustomLocations() {
        // Close the existing one
        DataSourcePoolBuilder.closeDataSource(dataSource);

        RdbConfig config = RdbConfig.builder()
                .jdbcUrl("jdbc:h2:mem:flywaytest2")
                .username("sa")
                .build();
        dataSource = DataSourcePoolBuilder.create(config).build();

        runner = FlywayMigrationRunner.builder(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .build();

        assertThat(runner.hasPendingMigrations()).isTrue();
    }

    @Test
    void testGetFlyway() {
        assertThat(runner.getFlyway()).isNotNull();
    }

    @Test
    void testWithPlaceholders() {
        // Close the existing one
        DataSourcePoolBuilder.closeDataSource(dataSource);

        RdbConfig config = RdbConfig.builder()
                .jdbcUrl("jdbc:h2:mem:flywaytestPlaceholders")
                .username("sa")
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
        // Migration should still work with placeholders configured
        var result = runner.migrate();
        assertThat(result).isNotNull();
        assertThat(runner.hasPendingMigrations()).isFalse();
    }

    @Test
    void testPlaceholderReplacementInMigration() throws SQLException {
        // Close the existing one
        DataSourcePoolBuilder.closeDataSource(dataSource);

        RdbConfig config = RdbConfig.builder()
                .jdbcUrl("jdbc:h2:mem:flywaytestPlaceholderReplacement")
                .username("sa")
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
            ResultSet tables = metaData.getTables(null, null, "DYNAMIC_TABLE", null);
            assertThat(tables.next()).isTrue();
            assertThat(tables.getString("TABLE_NAME")).isEqualTo("DYNAMIC_TABLE");
        }
    }
}
