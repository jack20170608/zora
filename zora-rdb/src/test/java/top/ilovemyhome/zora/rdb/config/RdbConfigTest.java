package top.ilovemyhome.zora.rdb.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RdbConfigTest {

    @Test
    void testBuilderAndGetters() {
        RdbConfig config = RdbConfig.builder()
                .jdbcUrl("jdbc:h2:mem:test")
                .username("sa")
                .password("password")
                .driverClassName("org.h2.Driver")
                .maximumPoolSize(20)
                .minimumIdle(5)
                .idleTimeout(300000)
                .connectionTimeout(10000)
                .maxLifetime(1200000)
                .poolName("test-pool")
                .autoCommit(false)
                .readOnly(true)
                .build();

        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:h2:mem:test");
        assertThat(config.getUsername()).isEqualTo("sa");
        assertThat(config.getPassword()).isEqualTo("password");
        assertThat(config.getDriverClassName()).isEqualTo("org.h2.Driver");
        assertThat(config.getMaximumPoolSize()).isEqualTo(20);
        assertThat(config.getMinimumIdle()).isEqualTo(5);
        assertThat(config.getIdleTimeout()).isEqualTo(300000);
        assertThat(config.getConnectionTimeout()).isEqualTo(10000);
        assertThat(config.getMaxLifetime()).isEqualTo(1200000);
        assertThat(config.getPoolName()).isEqualTo("test-pool");
        assertThat(config.isAutoCommit()).isFalse();
        assertThat(config.isReadOnly()).isTrue();
    }

    @Test
    void testDefaultValues() {
        RdbConfig config = RdbConfig.builder()
                .jdbcUrl("jdbc:h2:mem:test")
                .username("sa")
                .build();

        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:h2:mem:test");
        assertThat(config.getUsername()).isEqualTo("sa");
        assertThat(config.getMaximumPoolSize()).isEqualTo(10);
        assertThat(config.getMinimumIdle()).isEqualTo(2);
        assertThat(config.getIdleTimeout()).isEqualTo(600000);
        assertThat(config.getConnectionTimeout()).isEqualTo(30000);
        assertThat(config.getMaxLifetime()).isEqualTo(1800000);
        assertThat(config.getPoolName()).isEqualTo("zora-rdb-pool");
        assertThat(config.isAutoCommit()).isTrue();
        assertThat(config.isReadOnly()).isFalse();
    }

    @Test
    void testSetters() {
        RdbConfig config = new RdbConfig();
        config.setJdbcUrl("jdbc:test");
        config.setUsername("user");
        config.setPassword("pass");
        config.setDriverClassName("org.test.Driver");
        config.setMaximumPoolSize(15);
        config.setMinimumIdle(3);
        config.setIdleTimeout(100000);
        config.setConnectionTimeout(5000);
        config.setMaxLifetime(900000);
        config.setPoolName("custom-pool");
        config.setAutoCommit(false);
        config.setReadOnly(true);

        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:test");
        assertThat(config.getUsername()).isEqualTo("user");
        assertThat(config.getPassword()).isEqualTo("pass");
        assertThat(config.getDriverClassName()).isEqualTo("org.test.Driver");
        assertThat(config.getMaximumPoolSize()).isEqualTo(15);
        assertThat(config.getMinimumIdle()).isEqualTo(3);
        assertThat(config.getIdleTimeout()).isEqualTo(100000);
        assertThat(config.getConnectionTimeout()).isEqualTo(5000);
        assertThat(config.getMaxLifetime()).isEqualTo(900000);
        assertThat(config.getPoolName()).isEqualTo("custom-pool");
        assertThat(config.isAutoCommit()).isFalse();
        assertThat(config.isReadOnly()).isTrue();
    }

    @Test
    void testToString() {
        RdbConfig config = RdbConfig.builder()
                .jdbcUrl("jdbc:h2:mem:test")
                .username("sa")
                .build();

        String str = config.toString();
        assertThat(str).contains("jdbcUrl", "username", "maximumPoolSize");
        assertThat(str).doesNotContain("password"); // Password not included in toString for security
    }
}
