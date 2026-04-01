package top.ilovemyhome.zora.cli.flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

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
        protected Integer executeFlywayOperation(Flyway flyway) {
            flyway.migrate();
            LOGGER.info("Database migration completed successfully");
            return 0;
        }
    }

    @Command(name = "clean", description = "Drops all database objects owned by the user")
    static class CleanCommand extends AbstractFlywayCommand {

        @Override
        protected Integer executeFlywayOperation(Flyway flyway) {
            flyway.clean();
            LOGGER.info("Database cleaned successfully");
            return 0;
        }
    }

    @Command(name = "info", description = "Prints the details and status information about all migrations")
    static class InfoCommand extends AbstractFlywayCommand {

        @Override
        protected Integer executeFlywayOperation(Flyway flyway) {
            for (MigrationInfo info : flyway.info().all()) {
                LOGGER.info("{} - {} [{}]", info.getVersion(), info.getDescription(), info.getState());
            }
            return 0;
        }
    }

    @Command(name = "repair", description = "Repairs the migration history table")
    static class RepairCommand extends AbstractFlywayCommand {

        @Override
        protected Integer executeFlywayOperation(Flyway flyway) {
            flyway.repair();
            LOGGER.info("Migration history table repaired successfully");
            return 0;
        }
    }

    @Command(name = "validate", description = "Validates applied migrations against local migrations")
    static class ValidateCommand extends AbstractFlywayCommand {

        @Override
        protected Integer executeFlywayOperation(Flyway flyway) {
            flyway.validate();
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
                Flyway flyway = buildFlyway();
                Integer result = executeFlywayOperation(flyway);
                System.exit(result);
            } catch (FlywayException e) {
                LOGGER.error("Flyway operation failed: {}", e.getMessage(), e);
                System.exit(1);
            }
        }

        protected Flyway buildFlyway() {
            FluentConfiguration builder = Flyway.configure()
                    .dataSource(url, username, password)
                    .locations(locations.split(","));

            if (driver != null) {
                builder.driver(driver);
            }

            if (placeholders != null && !placeholders.isEmpty()) {
                builder.placeholders(placeholders);
            }

            builder.placeholderReplacement(placeholderReplacement);
            builder.cleanDisabled(cleanDisabled);

            if (baselineVersion != null) {
                builder.baselineVersion(MigrationVersion.fromVersion(baselineVersion));
                builder.baselineOnMigrate(true);
            }

            if (configFile != null && configFile.exists()) {
                Properties properties = new Properties();
                try {
                    properties.load(new java.io.FileReader(configFile));
                    builder.configuration(properties);
                } catch (Exception e) {
                    LOGGER.warn("Failed to load config file: {}", e.getMessage());
                }
            }

            return builder.load();
        }

        protected abstract Integer executeFlywayOperation(Flyway flyway);
    }
}
