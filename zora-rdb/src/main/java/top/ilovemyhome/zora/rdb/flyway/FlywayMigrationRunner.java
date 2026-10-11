package top.ilovemyhome.zora.rdb.flyway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Versioned SQL migration runner backed by Liquibase; the class name is retained for existing callers.
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
     * Create a new builder for the migration runner.
     *
     * @param dataSource the DataSource to use for migration
     * @return new builder instance
     */
    public static Builder builder(DataSource dataSource) {
        return new Builder(dataSource);
    }

    /**
     * Get the configured SQL migration engine.
     *
     * @return migration engine
     */
    public SqlMigrationEngine getFlyway() {
        return engine;
    }

    /**
     * Execute the migration.
     *
     * @return number of migrations applied
     */
    public int migrate() {
        LOGGER.info("Starting SQL database migration...");
        int result = engine.migrate();
        LOGGER.info("SQL migration completed successfully");
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
        LOGGER.info("Validating SQL migrations...");
        engine.validate();
        LOGGER.info("SQL migration validation completed successfully");
    }

    /**
     * Clean the database (drops all objects).
     * WARNING: This is destructive and should only be used in development/testing.
     */
    public void clean() {
        LOGGER.warn("Cleaning database - all objects will be dropped!");
        engine.clean();
        LOGGER.info("Database clean completed");
    }

    /**
     * Baseline existing database.
     */
    public void baseline() {
        LOGGER.info("Creating SQL migration baseline...");
        engine.baseline();
        LOGGER.info("SQL migration baseline created");
    }

    /**
     * Builder for FlywayMigrationRunner.
     */
    public static class Builder {
        private final DataSource dataSource;
        private String[] locations = new String[]{"classpath:db/migration"};
        private String targetVersion;
        private String table;
        private String defaultSchema;
        private Map<String, String> placeholders = new HashMap<>();

        public Builder(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        public Builder locations(String... locations) {
            this.locations = locations;
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
