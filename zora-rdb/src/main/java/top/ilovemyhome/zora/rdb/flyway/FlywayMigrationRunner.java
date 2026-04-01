package top.ilovemyhome.zora.rdb.flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Flyway database migration runner with simplified configuration.
 */
public class FlywayMigrationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlywayMigrationRunner.class);

    private final Flyway flyway;

    private FlywayMigrationRunner(Builder builder) {
        Objects.requireNonNull(builder.dataSource, "DataSource cannot be null");

        FluentConfiguration flywayBuilder = Flyway.configure()
                .dataSource(builder.dataSource);

        // Set locations
        if (builder.locations != null && builder.locations.length > 0) {
            flywayBuilder.locations(builder.locations);
        }

        // Set baseline configuration
        flywayBuilder.baselineOnMigrate(builder.baselineOnMigrate);
        if (builder.baselineVersion != null) {
            flywayBuilder.baselineVersion(builder.baselineVersion);
        }
        if (builder.baselineDescription != null) {
            flywayBuilder.baselineDescription(builder.baselineDescription);
        }

        // Set other configurations
        flywayBuilder.cleanOnValidationError(builder.cleanOnValidationError);
        flywayBuilder.validateOnMigrate(builder.validateOnMigrate);
        if (builder.targetVersion != null) {
            flywayBuilder.target(builder.targetVersion);
        }
        if (builder.table != null) {
            flywayBuilder.table(builder.table);
        }

        if (builder.defaultSchema != null) {
            flywayBuilder.defaultSchema(builder.defaultSchema);
        }
        if (builder.schemas != null && builder.schemas.length > 0) {
            flywayBuilder.schemas(builder.schemas);
        }

        // Set placeholders
        if (builder.placeholders != null && !builder.placeholders.isEmpty()) {
            flywayBuilder.placeholders(builder.placeholders);
        }

        this.flyway = flywayBuilder.load();

        LOGGER.info("Flyway configured with locations: {}", Arrays.toString(builder.locations));
    }

    /**
     * Create a new builder for FlywayMigrationRunner.
     *
     * @param dataSource the DataSource to use for migration
     * @return new builder instance
     */
    public static Builder builder(DataSource dataSource) {
        return new Builder(dataSource);
    }

    /**
     * Get the configured Flyway instance.
     *
     * @return Flyway instance
     */
    public Flyway getFlyway() {
        return flyway;
    }

    /**
     * Execute the migration.
     *
     * @return migration result
     */
    public MigrateResult migrate() {
        LOGGER.info("Starting Flyway database migration...");
        MigrateResult result = flyway.migrate();
        LOGGER.info("Flyway migration completed successfully");
        return result;
    }

    /**
     * Check if pending migrations exist.
     *
     * @return true if there are pending migrations
     */
    public boolean hasPendingMigrations() {
        MigrationInfo[] pendingMigrations = flyway.info().pending();
        return pendingMigrations != null && pendingMigrations.length > 0;
    }

    /**
     * Get the number of pending migrations.
     *
     * @return number of pending migrations
     */
    public int getPendingMigrationCount() {
        MigrationInfo[] pendingMigrations = flyway.info().pending();
        return pendingMigrations != null ? pendingMigrations.length : 0;
    }

    /**
     * Validate applied migrations.
     */
    public void validate() {
        LOGGER.info("Validating Flyway migrations...");
        flyway.validate();
        LOGGER.info("Flyway validation completed successfully");
    }

    /**
     * Clean the database (drops all objects).
     * WARNING: This is destructive and should only be used in development/testing.
     */
    public void clean() {
        LOGGER.warn("Cleaning database with Flyway - all objects will be dropped!");
        flyway.clean();
        LOGGER.info("Database clean completed");
    }

    /**
     * Baseline existing database.
     */
    public void baseline() {
        LOGGER.info("Creating Flyway baseline...");
        flyway.baseline();
        LOGGER.info("Flyway baseline created");
    }

    /**
     * Builder for FlywayMigrationRunner.
     */
    public static class Builder {
        private final DataSource dataSource;
        private String[] locations = new String[]{"classpath:db/migration"};
        private boolean baselineOnMigrate = true;
        private MigrationVersion baselineVersion = MigrationVersion.fromVersion("1");
        private String baselineDescription = "<< Flyway Baseline >>";
        private boolean cleanOnValidationError = false;
        private boolean validateOnMigrate = true;
        private MigrationVersion targetVersion;
        private String table;
        private String defaultSchema;
        private String[] schemas;
        private Map<String, String> placeholders = new HashMap<>();

        public Builder(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        public Builder locations(String... locations) {
            this.locations = locations;
            return this;
        }

        public Builder baselineOnMigrate(boolean baselineOnMigrate) {
            this.baselineOnMigrate = baselineOnMigrate;
            return this;
        }

        public Builder baselineVersion(String baselineVersion) {
            this.baselineVersion = MigrationVersion.fromVersion(baselineVersion);
            return this;
        }

        public Builder baselineDescription(String baselineDescription) {
            this.baselineDescription = baselineDescription;
            return this;
        }

        public Builder cleanOnValidationError(boolean cleanOnValidationError) {
            this.cleanOnValidationError = cleanOnValidationError;
            return this;
        }

        public Builder validateOnMigrate(boolean validateOnMigrate) {
            this.validateOnMigrate = validateOnMigrate;
            return this;
        }

        public Builder targetVersion(String targetVersion) {
            this.targetVersion = MigrationVersion.fromVersion(targetVersion);
            return this;
        }

        public Builder table(String table) {
            this.table = table;
            return this;
        }

        public Builder defaultSchema(String defaultSchema) {
            this.defaultSchema = defaultSchema;
            return this;
        }

        public Builder schemas(String... schemas) {
            this.schemas = schemas;
            return this;
        }

        /**
         * Add a single placeholder.
         *
         * @param key placeholder key
         * @param value placeholder value
         * @return builder instance
         */
        public Builder placeholder(String key, String value) {
            this.placeholders.put(key, value);
            return this;
        }

        /**
         * Set all placeholders, replacing any existing ones.
         *
         * @param placeholders map of placeholders
         * @return builder instance
         */
        public Builder placeholders(Map<String, String> placeholders) {
            this.placeholders = new HashMap<>(placeholders);
            return this;
        }

        public FlywayMigrationRunner build() {
            return new FlywayMigrationRunner(this);
        }
    }
}
