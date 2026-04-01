package top.ilovemyhome.zora.rdb.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RdbConfigLoaderTest {

    @Test
    void testLoadFullConfig() {
        String configStr = """
                db.test {
                    jdbcUrl = "jdbc:h2:mem:testdb"
                    username = "testuser"
                    password = "testpass"
                    driverClassName = "org.h2.Driver"
                    maximumPoolSize = 15
                    minimumIdle = 3
                    idleTimeout = 300000
                    connectionTimeout = 10000
                    maxLifetime = 1200000
                    poolName = "test-pool"
                    autoCommit = false
                    readOnly = true
                }
                """;

        Config config = ConfigFactory.parseString(configStr);
        RdbConfig rdbConfig = RdbConfigLoader.load(config, "db.test");

        assertThat(rdbConfig.getJdbcUrl()).isEqualTo("jdbc:h2:mem:testdb");
        assertThat(rdbConfig.getUsername()).isEqualTo("testuser");
        assertThat(rdbConfig.getPassword()).isEqualTo("testpass");
        assertThat(rdbConfig.getDriverClassName()).isEqualTo("org.h2.Driver");
        assertThat(rdbConfig.getMaximumPoolSize()).isEqualTo(15);
        assertThat(rdbConfig.getMinimumIdle()).isEqualTo(3);
        assertThat(rdbConfig.getIdleTimeout()).isEqualTo(300000);
        assertThat(rdbConfig.getConnectionTimeout()).isEqualTo(10000);
        assertThat(rdbConfig.getMaxLifetime()).isEqualTo(1200000);
        assertThat(rdbConfig.getPoolName()).isEqualTo("test-pool");
        assertThat(rdbConfig.isAutoCommit()).isFalse();
        assertThat(rdbConfig.isReadOnly()).isTrue();
    }

    @Test
    void testLoadPartialConfig() {
        String configStr = """
                db.test {
                    jdbcUrl = "jdbc:h2:mem:testdb"
                    username = "sa"
                }
                """;

        Config config = ConfigFactory.parseString(configStr);
        RdbConfig rdbConfig = RdbConfigLoader.load(config, "db.test");

        assertThat(rdbConfig.getJdbcUrl()).isEqualTo("jdbc:h2:mem:testdb");
        assertThat(rdbConfig.getUsername()).isEqualTo("sa");
        assertThat(rdbConfig.getPassword()).isEmpty();
        assertThat(rdbConfig.getMaximumPoolSize()).isEqualTo(10);
        assertThat(rdbConfig.getMinimumIdle()).isEqualTo(2);
        assertThat(rdbConfig.getPoolName()).isEqualTo("zora-rdb-pool");
        assertThat(rdbConfig.isAutoCommit()).isTrue();
        assertThat(rdbConfig.isReadOnly()).isFalse();
    }

    @Test
    void testLoadWithDefaultValues() {
        String configStr = "{}";
        Config config = ConfigFactory.parseString(configStr);
        RdbConfig rdbConfig = RdbConfigLoader.load(config, "db.test");

        // jdbcUrl and username can be null, others have defaults
        assertThat(rdbConfig.getJdbcUrl()).isNull();
        assertThat(rdbConfig.getUsername()).isNull();
        assertThat(rdbConfig.getPassword()).isEmpty();
        assertThat(rdbConfig.getMaximumPoolSize()).isEqualTo(10);
        assertThat(rdbConfig.isAutoCommit()).isTrue();
    }
}
