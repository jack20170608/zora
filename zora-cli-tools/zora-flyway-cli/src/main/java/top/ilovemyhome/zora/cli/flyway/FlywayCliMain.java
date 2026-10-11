package top.ilovemyhome.zora.cli.flyway;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import top.ilovemyhome.zora.rdb.flyway.SqlMigrationEngine;

import java.io.File;
import java.util.Map;
import java.util.Properties;

import static picocli.CommandLine.*;

/**
 * Command-line tool for managing Flyway database migrations.
 * Provides simplified interface for common Flyway operations.
 */
@picocli.CommandLine.Command(
        name = "zora-flyway-cli",
        description = "Flyway database migration command-line tool",
        version = "1.0.0",
        mixinStandardHelpOptions = true,
        subcommands = {
                FlywayCliMain.MigrateCommand.class,
                FlywayCliMain.CleanCommand.class,
                FlywayCliMain.InfoCommand.class,
                FlywayCliMain.RepairCommand.class,
                FlywayCliMain.ValidateCommand.class
        }
)
public class FlywayCliMain {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlywayCliMain.class);

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FlywayCliMain()).execute(args);
        System.exit(exitCode);
    }

    @Command(name = "migrate", description = "Migrates the database")
    static class MigrateCommand extends AbstractFlywayCommand {

        @Override
        protected Integer executeFlywayOperation(SqlMigrationEngine engine) {
            engine.migrate();
            LOGGER.info("Database migration completed successfully");
            return 0;
        }
    }

    @Command(name = "clean", description = "Drops all database objects owned by the user")
    static class CleanCommand extends AbstractFlywayCommand {

        @Override
        protected Integer executeFlywayOperation(SqlMigrationEngine engine) {
            engine.clean();
            LOGGER.info("Database cleaned successfully");
            return 0;
        }
    }

    @Command(name = "info", description = "Prints the details and status information about all migrations")
    static class InfoCommand extends AbstractFlywayCommand {

        @Override
        protected Integer executeFlywayOperation(SqlMigrationEngine engine) {
            for (SqlMigrationEngine.MigrationStatus info : engine.info()) {
                LOGGER.info("{} - {} [{}]", info.version(), info.description(),
                    info.applied() ? "APPLIED" : "PENDING");
            }
            return 0;
        }
    }

    @Command(name = "repair", description = "Repairs the migration history table")
    static class RepairCommand extends AbstractFlywayCommand {

        @Override
        protected Integer executeFlywayOperation(SqlMigrationEngine engine) {
            engine.repair();
            LOGGER.info("Migration history table repaired successfully");
            return 0;
        }
    }

    @Command(name = "validate", description = "Validates applied migrations against local migrations")
    static class ValidateCommand extends AbstractFlywayCommand {

        @Override
        protected Integer executeFlywayOperation(SqlMigrationEngine engine) {
            engine.validate();
            LOGGER.info("All migrations validated successfully");
            return 0;
        }
    }

    abstract static class AbstractFlywayCommand implements Runnable {

        @Option(names = {"-u", "--url"}, description = "JDBC URL", required = true)
        String url;

        @Option(names = {"-user", "--username"}, description = "Database username", required = true)
        String username;

        @Option(names = {"-p", "--password"}, description = "Database password", required = true)
        String password;

        @Option(names = {"-d", "--driver"}, description = "JDBC driver class name")
        String driver;

        @Option(names = {"-l", "--locations"}, description = "Comma-separated list of migration locations")
        String locations = "filesystem:./sql";

        @Option(names = {"-b", "--baseline-version"}, description = "Baseline version for existing database")
        String baselineVersion;

        @Option(names = {"-c", "--config-file"}, description = "Flyway properties configuration file")
        File configFile;

        @Option(names = {"-P", "--placeholder"}, description = "Placeholders in format key=value. Can be specified multiple times.", paramLabel = "key=value")
        Map<String, String> placeholders = new java.util.HashMap<>();

        @Option(names = {"--placeholder-replacement"}, description = "Whether placeholder replacement is enabled", defaultValue = "true")
        boolean placeholderReplacement = true;

        @Option(names = {"--clean-disabled"}, description = "Whether clean operation is disabled", defaultValue = "true")
        boolean cleanDisabled = true;

        @Override
        public void run() {
            try {
                SqlMigrationEngine engine = buildFlyway();
                Integer result = executeFlywayOperation(engine);
                System.exit(result);
            } catch (RuntimeException e) {
                LOGGER.error("Database migration operation failed: {}", e.getMessage(), e);
                System.exit(1);
            }
        }

        protected SqlMigrationEngine buildFlyway() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(username);
            config.setPassword(password);
            if (driver != null) {
                config.setDriverClassName(driver);
            }
            if (configFile != null && configFile.exists()) {
                Properties properties = new Properties();
                try {
                    properties.load(new java.io.FileReader(configFile));
                    locations = properties.getProperty("locations", locations);
                } catch (Exception e) {
                    LOGGER.warn("Failed to load config file: {}", e.getMessage());
                }
            }
            return new SqlMigrationEngine(new HikariDataSource(config), locations.split(","),
                null, null, null, placeholders, placeholderReplacement, cleanDisabled);
        }

        protected abstract Integer executeFlywayOperation(SqlMigrationEngine engine);
    }
}
