package top.ilovemyhome.zora.rdb.config;

import com.typesafe.config.Config;
import top.ilovemyhome.zora.config.ConfigLoader;

/**
 * Loader for RdbConfig from external configuration.
 */
public class RdbConfigLoader {

    /**
     * Load RdbConfig from Config with prefix.
     *
     * @param config the Config instance
     * @param prefix configuration prefix (e.g. "db.main")
     * @return loaded RdbConfig
     */
    public static RdbConfig load(Config config, String prefix) {
        RdbConfig rdbConfig = new RdbConfig();

        rdbConfig.setJdbcUrl(getString(config, prefix + ".jdbcUrl", null));
        rdbConfig.setUsername(getString(config, prefix + ".username", null));
        rdbConfig.setPassword(getString(config, prefix + ".password", ""));
        rdbConfig.setDriverClassName(getString(config, prefix + ".driverClassName", null));
        rdbConfig.setMaximumPoolSize(getInt(config, prefix + ".maximumPoolSize", 10));
        rdbConfig.setMinimumIdle(getInt(config, prefix + ".minimumIdle", 2));
        rdbConfig.setIdleTimeout(getLong(config, prefix + ".idleTimeout", 600000L));
        rdbConfig.setConnectionTimeout(getLong(config, prefix + ".connectionTimeout", 30000L));
        rdbConfig.setMaxLifetime(getLong(config, prefix + ".maxLifetime", 1800000L));
        rdbConfig.setPoolName(getString(config, prefix + ".poolName", "zora-rdb-pool"));
        rdbConfig.setAutoCommit(getBoolean(config, prefix + ".autoCommit", true));
        rdbConfig.setReadOnly(getBoolean(config, prefix + ".readOnly", false));

        return rdbConfig;
    }

    /**
     * Load RdbConfig from the default ConfigLoader.
     *
     * @param prefix configuration prefix
     * @return loaded RdbConfig
     */
    public static RdbConfig load(String prefix) {
        return load(ConfigLoader.loadDefault(), prefix);
    }

    private static String getString(Config config, String path, String defaultValue) {
        if (config.hasPath(path)) {
            return config.getString(path);
        }
        return defaultValue;
    }

    private static int getInt(Config config, String path, int defaultValue) {
        if (config.hasPath(path)) {
            return config.getInt(path);
        }
        return defaultValue;
    }

    private static long getLong(Config config, String path, long defaultValue) {
        if (config.hasPath(path)) {
            return config.getLong(path);
        }
        return defaultValue;
    }

    private static boolean getBoolean(Config config, String path, boolean defaultValue) {
        if (config.hasPath(path)) {
            return config.getBoolean(path);
        }
        return defaultValue;
    }
}
