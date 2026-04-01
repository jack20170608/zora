package top.ilovemyhome.zora.rdb.config;

import java.util.StringJoiner;

/**
 * Configuration for relational database connection.
 */
public class RdbConfig {

    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName;
    private int maximumPoolSize = 10;
    private int minimumIdle = 2;
    private long idleTimeout = 600000;
    private long connectionTimeout = 30000;
    private long maxLifetime = 1800000;
    private String poolName = "zora-rdb-pool";
    private boolean autoCommit = true;
    private boolean readOnly = false;

    public RdbConfig() {
    }

    private RdbConfig(Builder builder) {
        this.jdbcUrl = builder.jdbcUrl;
        this.username = builder.username;
        this.password = builder.password;
        this.driverClassName = builder.driverClassName;
        this.maximumPoolSize = builder.maximumPoolSize;
        this.minimumIdle = builder.minimumIdle;
        this.idleTimeout = builder.idleTimeout;
        this.connectionTimeout = builder.connectionTimeout;
        this.maxLifetime = builder.maxLifetime;
        this.poolName = builder.poolName;
        this.autoCommit = builder.autoCommit;
        this.readOnly = builder.readOnly;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    public int getMinimumIdle() {
        return minimumIdle;
    }

    public void setMinimumIdle(int minimumIdle) {
        this.minimumIdle = minimumIdle;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(long connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public long getMaxLifetime() {
        return maxLifetime;
    }

    public void setMaxLifetime(long maxLifetime) {
        this.maxLifetime = maxLifetime;
    }

    public String getPoolName() {
        return poolName;
    }

    public void setPoolName(String poolName) {
        this.poolName = poolName;
    }

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public void setAutoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", RdbConfig.class.getSimpleName() + "[", "]")
                .add("jdbcUrl='" + jdbcUrl + "'")
                .add("username='" + username + "'")
                .add("driverClassName='" + driverClassName + "'")
                .add("maximumPoolSize=" + maximumPoolSize)
                .add("minimumIdle=" + minimumIdle)
                .add("poolName='" + poolName + "'")
                .add("autoCommit=" + autoCommit)
                .add("readOnly=" + readOnly)
                .toString();
    }

    public static class Builder {
        private String jdbcUrl;
        private String username;
        private String password;
        private String driverClassName;
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        private long idleTimeout = 600000;
        private long connectionTimeout = 30000;
        private long maxLifetime = 1800000;
        private String poolName = "zora-rdb-pool";
        private boolean autoCommit = true;
        private boolean readOnly = false;

        public Builder jdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder driverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
            return this;
        }

        public Builder maximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
            return this;
        }

        public Builder minimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
            return this;
        }

        public Builder idleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        public Builder connectionTimeout(long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        public Builder maxLifetime(long maxLifetime) {
            this.maxLifetime = maxLifetime;
            return this;
        }

        public Builder poolName(String poolName) {
            this.poolName = poolName;
            return this;
        }

        public Builder autoCommit(boolean autoCommit) {
            this.autoCommit = autoCommit;
            return this;
        }

        public Builder readOnly(boolean readOnly) {
            this.readOnly = readOnly;
            return this;
        }

        public RdbConfig build() {
            return new RdbConfig(this);
        }
    }
}
