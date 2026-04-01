package top.ilovemyhome.zora.rdb.pool;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.rdb.config.RdbConfig;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * Builder for creating HikariCP connection pool based on RdbConfig.
 */
public class DataSourcePoolBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataSourcePoolBuilder.class);

    private final RdbConfig config;

    private DataSourcePoolBuilder(RdbConfig config) {
        Objects.requireNonNull(config, "RdbConfig cannot be null");
        Objects.requireNonNull(config.getJdbcUrl(), "jdbcUrl cannot be null");
        this.config = config;
    }

    /**
     * Create a new DataSourcePoolBuilder with the given configuration.
     *
     * @param config database configuration
     * @return new builder instance
     */
    public static DataSourcePoolBuilder create(RdbConfig config) {
        return new DataSourcePoolBuilder(config);
    }

    /**
     * Build the HikariDataSource based on the configuration.
     *
     * @return configured HikariDataSource
     */
    public HikariDataSource build() {
        HikariConfig hikariConfig = new HikariConfig();

        // Basic connection settings
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());

        // Driver class name if provided
        if (config.getDriverClassName() != null && !config.getDriverClassName().isEmpty()) {
            hikariConfig.setDriverClassName(config.getDriverClassName());
        }

        // Pool settings
        hikariConfig.setMaximumPoolSize(config.getMaximumPoolSize());
        hikariConfig.setMinimumIdle(config.getMinimumIdle());
        hikariConfig.setIdleTimeout(config.getIdleTimeout());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
        hikariConfig.setMaxLifetime(config.getMaxLifetime());
        hikariConfig.setPoolName(config.getPoolName());
        hikariConfig.setAutoCommit(config.isAutoCommit());
        hikariConfig.setReadOnly(config.isReadOnly());

        LOGGER.info("Creating Hikari connection pool with config: {}", config);

        return new HikariDataSource(hikariConfig);
    }

    /**
     * Utility method to close a DataSource that may be a HikariDataSource.
     *
     * @param dataSource the DataSource to close
     */
    public static void closeDataSource(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            if (!hikariDataSource.isClosed()) {
                hikariDataSource.close();
                LOGGER.info("Hikari connection pool closed");
            }
        }
    }
}
