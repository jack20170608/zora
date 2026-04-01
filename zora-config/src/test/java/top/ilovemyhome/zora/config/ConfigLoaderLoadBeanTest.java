package top.ilovemyhome.zora.config;

import com.typesafe.config.Config;
import org.junit.jupiter.api.Test;
import top.ilovemyhome.zora.config.bean.FlywayConfig;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class ConfigLoaderLoadBeanTest {

    @Test
    public void testLoadConfigAsBeanWithPath() {
        FlywayConfig flywayConfig
            = ConfigLoader.loadConfigAsBean("config/flyway-bean.conf", "flyway", FlywayConfig.class);
        assertThat(flywayConfig.isEnabled()).isTrue();
        assertThat(flywayConfig.getDriver()).isEqualTo("org.postgresql.Driver");
        assertThat(flywayConfig.getUrl()).isEqualTo("jdbc:postgresql://localhost:5432/peanotes");
        assertThat(flywayConfig.getUser()).isEqualTo("postgres");
        assertThat(flywayConfig.getPassword()).isEmpty();
        assertThat(flywayConfig.getMetaTable()).isEqualTo("flyway_schema_history");
        assertThat(flywayConfig.getLocations()).isEqualTo(
            List.of(
                "classpath:db/migration",
                "classpath:db/migration-postgresql"
            )
        );
        assertThat(flywayConfig.getPlaceHolders()).isEqualTo(
            Map.of("p1", "value1",
                "p2", "value2")
        );
        assertThat(flywayConfig.getCallbacks()).isEqualTo(
            List.of("top.ilovemyhome.commons.common.config.bean.FlywayCallback")
        );

    }

    @Test
    public void testLoadConfigSubstitutions() {
        Config config = ConfigLoader.loadConfig("config/flyway-bean.conf");
        assertThat(config.getString("foo.timeout")).isEqualTo("10ms");
        assertThat(config.getString("bar.timeout")).isEqualTo("10ms");
        assertThat(config.getIsNull("foo.p1")).isTrue();
    }
}
