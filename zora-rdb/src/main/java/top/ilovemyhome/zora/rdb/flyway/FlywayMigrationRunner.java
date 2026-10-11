package top.ilovemyhome.zora.rdb.flyway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Flyway database migration runner with simplified configuration.
 */
public class FlywayMigrationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlywayMigrationRunner.class);

    private final SqlMigrationEngine engine;

    private FlywayMigrationRunner(Builder builder) {
        Objects.requireNonNull(builder.dataSource, "DataSource cannot be null");

        this.engine = new SqlMigrationEngine(builder.dataSource, builder.locations,
            builder.targetVersion, builder.table, builder.defaultSchema,
            builder.placeholders, true, false);
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
    public SqlMigrationEngine getFlyway() {
        return engine;
    }

    /**
     * Execute the migration.
     *
     * @return migration result
     */
    public int migrate() {
        LOGGER.info("Starting Flyway database migration...");
        int result = engine.migrate();
        LOGGER.info("Flyway migration completed successfully");
        return result;
    }

    /**
     * Check if pending migrations exist.
     *
     * @return true if there are pending migrations
     */
    public boolean hasPendingMigrations() {
        return engine.pendingCount() > 0;
    }

    /**
     * Get the number of pending migrations.
     *
     * @return number of pending migrations
     */
    public int getPendingMigrationCount() {
        return engine.pendingCount();
    }

    /**
     * Validate applied migrations.
     */
    public void validate() {
        LOGGER.info("Validating Flyway migrations...");
        engine.validate();
        LOGGER.info("Flyway validation completed successfully");
    }

    /**
     * Clean the database (drops all objects).
     * WARNING: This is destructive and should only be used in development/testing.
     */
    public void clean() {
        LOGGER.warn("Cleaning database with Flyway - all objects will be dropped!");
        engine.clean();
        LOGGER.info("Database clean completed");
    }

    /**
     * Baseline existing database.
     */
    public void baseline() {
        LOGGER.info("Creating Flyway baseline...");
        engine.baseline();
        LOGGER.info("Flyway baseline created");
    }

    /**
     * Builder for FlywayMigrationRunner.
     */
    public static class Builder {
        private final DataSource dataSource;
        private String[] locations = new String[]{"classpath:db/migration"};
        private boolean baselineOnMigrate = true;
        private String baselineVersion = "1";
        private String baselineDescription = "<< Flyway Baseline >>";
        private boolean cleanOnValidationError = false;
        private boolean validateOnMigrate = true;
        private String targetVersion;
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
            this.baselineVersion = baselineVersion;
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
            this.targetVersion = targetVersion;
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
