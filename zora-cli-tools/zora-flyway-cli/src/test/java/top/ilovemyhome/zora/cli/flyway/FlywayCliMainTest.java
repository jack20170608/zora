package top.ilovemyhome.zora.cli.flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.cli.flyway.FlywayCliMain.AbstractFlywayCommand;
import top.ilovemyhome.zora.cli.flyway.FlywayCliMain.MigrateCommand;
import top.ilovemyhome.zora.cli.flyway.FlywayCliMain.InfoCommand;

import javax.sql.DataSource;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for FlywayCliMain using embedded PostgreSQL.
 */
class FlywayCliMainTest {

    private static String jdbcUrl;
    private static final String username = "postgres";
    private static final String password = "postgres";
    private static final String driver = "org.postgresql.Driver";

    private static final Logger logger = LoggerFactory.getLogger(FlywayCliMainTest.class);

    @BeforeAll
    static void setup() throws Exception {
        DataSource dataSource = SharedResources.getDataSource(username, "postgres");
        jdbcUrl = ((org.postgresql.ds.PGSimpleDataSource) dataSource).getUrl();
        logger.info("Embedded Postgres started at: {}", jdbcUrl);
    }

    @AfterAll
    static void tearDown() throws Exception {
        SharedResources.closePg();
    }

    @Test
    void testBuildFlyway_withAllParameters() throws Exception {
        MigrateCommand command = new MigrateCommand();
        command.url = jdbcUrl;
        command.username = username;
        command.password = password;
        command.driver = driver;
        command.locations = "filesystem:" + getTestMigrationDir();

        Flyway flyway = command.buildFlyway();

        assertThat(flyway.getConfiguration().getUrl()).isEqualTo(jdbcUrl);
        assertThat(flyway.getConfiguration().getDriver()).isEqualTo(driver);
        assertThat(flyway.getConfiguration().getLocations()).hasSize(1);
    }

    @Test
    void testMigrate_withEmbeddedPostgres() throws Exception {
        MigrateCommand command = new MigrateCommand();
        command.url = jdbcUrl;
        command.username = username;
        command.password = password;
        command.driver = driver;
        command.locations = "filesystem:" + getTestMigrationDir();
        command.cleanDisabled = false;

        Flyway flyway = command.buildFlyway();
        flyway.clean();
        flyway.migrate();

        // Verify migration history table exists
        try (Connection conn = flyway.getConfiguration().getDataSource().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM flyway_schema_history")) {
            assertThat(rs.next()).isTrue();
            logger.info("Migration history table verified");
        }
    }

    @Test
    void testInfo_command() throws Exception {
        MigrateCommand migrateCommand = new MigrateCommand();
        migrateCommand.url = jdbcUrl;
        migrateCommand.username = username;
        migrateCommand.password = password;
        migrateCommand.driver = driver;
        migrateCommand.locations = "filesystem:" + getTestMigrationDir();
        migrateCommand.buildFlyway().migrate();

        InfoCommand infoCommand = new InfoCommand();
        infoCommand.url = jdbcUrl;
        infoCommand.username = username;
        infoCommand.password = password;
        infoCommand.driver = driver;
        infoCommand.locations = "filesystem:" + getTestMigrationDir();

        Flyway flyway = infoCommand.buildFlyway();
        MigrationInfo[] infos = flyway.info().all();

        assertThat(infos).hasSize(1);
        assertThat(infos[0].getVersion().getVersion()).isEqualTo("1");
        assertThat(infos[0].getDescription()).isEqualTo("create users table");
        assertThat(infos[0].getState().isApplied()).isTrue();

        logger.info("Found {} migrations", infos.length);
    }

    @Test
    void testMigrate_withPlaceholders() throws Exception {
        MigrateCommand command = new MigrateCommand();
        command.url = jdbcUrl;
        command.username = username;
        command.password = password;
        command.driver = driver;
        command.locations = "filesystem:" + getTestMigrationDirWithPlaceholders();
        command.placeholders.put("tableName", "test_users");
        command.cleanDisabled = false;

        Flyway flyway = command.buildFlyway();
        flyway.clean();
        flyway.repair();
        flyway.migrate();

        // Verify table created with placeholder replacement by checking schema
        try (Connection conn = flyway.getConfiguration().getDataSource().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT table_name FROM information_schema.tables " +
                 "WHERE table_schema = 'public' AND table_name = 'test_users'")) {
            assertThat(rs.next()).isTrue();
            logger.info("Table test_users created with placeholder replacement");
        }
    }

    @Test
    void testMigrate_disablePlaceholderReplacement() throws Exception {
        MigrateCommand command = new MigrateCommand();
        command.url = jdbcUrl;
        command.username = username;
        command.password = password;
        command.driver = driver;
        command.locations = "filesystem:" + getTestMigrationDir();
        command.placeholderReplacement = false;

        Flyway flyway = command.buildFlyway();

        assertThat(flyway.getConfiguration().isPlaceholderReplacement()).isFalse();
    }

    private String getTestMigrationDir() throws URISyntaxException {
        URL resource = getClass().getClassLoader().getResource("migrations");
        assertThat(resource).isNotNull();
        return new File(resource.toURI()).getAbsolutePath();
    }

    private String getTestMigrationDirWithPlaceholders() throws URISyntaxException {
        URL resource = getClass().getClassLoader().getResource("migrations-placeholders");
        assertThat(resource).isNotNull();
        return new File(resource.toURI()).getAbsolutePath();
    }
}
