package top.ilovemyhome.zora.rdb.pool;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import top.ilovemyhome.zora.rdb.config.RdbConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataSourcePoolBuilderTest {

    private HikariDataSource dataSource;

    @AfterEach
    void tearDown() {
        DataSourcePoolBuilder.closeDataSource(dataSource);
    }

    @Test
    void testBuildWithBasicConfig() {
        RdbConfig config = RdbConfig.builder()
                .jdbcUrl("jdbc:h2:mem:test1")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();

        dataSource = DataSourcePoolBuilder.create(config).build();

        assertThat(dataSource).isNotNull();
        assertThat(dataSource.isClosed()).isFalse();
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(10);
    }

    @Test
    void testBuildWithCustomPoolSettings() {
        RdbConfig config = RdbConfig.builder()
                .jdbcUrl("jdbc:h2:mem:test2")
                .username("sa")
                .maximumPoolSize(20)
                .minimumIdle(5)
                .poolName("custom-test-pool")
                .autoCommit(false)
                .readOnly(true)
                .build();

        dataSource = DataSourcePoolBuilder.create(config).build();

        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(20);
        assertThat(dataSource.getMinimumIdle()).isEqualTo(5);
        assertThat(dataSource.getPoolName()).isEqualTo("custom-test-pool");
        assertThat(dataSource.isAutoCommit()).isFalse();
        assertThat(dataSource.isReadOnly()).isTrue();
    }

    @Test
    void testGetConnection() throws SQLException {
        RdbConfig config = RdbConfig.builder()
                .jdbcUrl("jdbc:h2:mem:test3")
                .username("sa")
                .build();

        dataSource = DataSourcePoolBuilder.create(config).build();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            assertThat(conn).isNotNull();
            assertThat(conn.isValid(5)).isTrue();

            // Create a table and test it works
            stmt.execute("CREATE TABLE test (id INT, name VARCHAR(100))");
            stmt.execute("INSERT INTO test VALUES (1, 'test')");

            assertThat(stmt.getUpdateCount()).isEqualTo(1);
        }
    }

    @Test
    void testCloseDataSource() {
        RdbConfig config = RdbConfig.builder()
                .jdbcUrl("jdbc:h2:mem:test4")
                .username("sa")
                .build();

        dataSource = DataSourcePoolBuilder.create(config).build();
        assertThat(dataSource.isClosed()).isFalse();

        DataSourcePoolBuilder.closeDataSource(dataSource);
        assertThat(dataSource.isClosed()).isTrue();
        dataSource = null;
    }

    @Test
    void testNullConfigShouldThrow() {
        assertThrows(NullPointerException.class, () -> DataSourcePoolBuilder.create(null));
    }

    @Test
    void testNullJdbcUrlShouldThrow() {
        RdbConfig config = RdbConfig.builder()
                .username("sa")
                .build();

        assertThrows(NullPointerException.class, () -> DataSourcePoolBuilder.create(config));
    }
}
