package top.ilovemyhome.zora.rdb.flyway;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.ilovemyhome.zora.rdb.config.RdbConfig;
import top.ilovemyhome.zora.rdb.pool.DataSourcePoolBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                .build();
    }

    @AfterEach
    void tearDown() {
        DataSourcePoolBuilder.closeDataSource(dataSource);
    }

    @Test
    void testMigrate() {
        var result = runner.migrate();
        assertThat(result).isEqualTo(2);
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
    void testBaselineMarksExistingScriptsAsApplied() {
        runner.baseline();
        assertThat(runner.hasPendingMigrations()).isFalse();
        assertThat(runner.getPendingMigrationCount()).isZero();
    }

    @Test
    void testRepairPreservesAppliedScripts() {
        runner.migrate();
        runner.getFlyway().repair();
        runner.validate();
        assertThat(runner.hasPendingMigrations()).isFalse();
    }

    @Test
    void testValidateDetectsChangedScript(@TempDir Path scripts) throws Exception {
        Path script = scripts.resolve("V1__create_validation_table.sql");
        Files.writeString(script, "CREATE TABLE validation_table (id INT PRIMARY KEY);");
        runner = FlywayMigrationRunner.builder(dataSource)
            .locations("filesystem:" + scripts)
            .build();
        runner.migrate();

        Files.writeString(script, "CREATE TABLE validation_table (id INT PRIMARY KEY, name VARCHAR(10));");

        assertThatThrownBy(runner::validate).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Database migration operation failed");
    }

    @Test
    void testMultipleLocations(@TempDir Path scripts) throws Exception {
        Path first = Files.createDirectory(scripts.resolve("first"));
        Path second = Files.createDirectory(scripts.resolve("second"));
        Files.writeString(first.resolve("V1__create_one.sql"), "CREATE TABLE one (id INT);");
        Files.writeString(second.resolve("V2__create_two.sql"), "CREATE TABLE two (id INT);");

        runner = FlywayMigrationRunner.builder(dataSource)
            .locations("filesystem:" + first, "filesystem:" + second).build();

        assertThat(runner.migrate()).isEqualTo(2);
        assertThat(runner.getPendingMigrationCount()).isZero();
    }

    @Test
    void testDuplicateVersionFailsBeforeMigration(@TempDir Path scripts) throws Exception {
        Path first = Files.createDirectory(scripts.resolve("first"));
        Path second = Files.createDirectory(scripts.resolve("second"));
        Files.writeString(first.resolve("V1__one.sql"), "CREATE TABLE one (id INT);");
        Files.writeString(second.resolve("V1__two.sql"), "CREATE TABLE two (id INT);");

        runner = FlywayMigrationRunner.builder(dataSource)
            .locations("filesystem:" + first, "filesystem:" + second).build();

        assertThatThrownBy(runner::migrate).isInstanceOf(IllegalStateException.class)
            .hasRootCauseInstanceOf(IllegalArgumentException.class);
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
                .placeholder("key1", "value1")
                .placeholder("key2", "value2")
                .build();

        assertThat(runner.getFlyway()).isNotNull();
        assertThat(runner.hasPendingMigrations()).isTrue();
        // Migration should still work with placeholders configured
        var result = runner.migrate();
        assertThat(result).isEqualTo(2);
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
                .placeholder("table_name", "dynamic_table")
                .build();

        assertThat(runner.hasPendingMigrations()).isTrue();
        var result = runner.migrate();
        assertThat(result).isEqualTo(1);
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
