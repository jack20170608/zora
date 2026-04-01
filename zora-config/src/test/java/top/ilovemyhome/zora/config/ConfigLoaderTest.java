package top.ilovemyhome.zora.config;

import com.typesafe.config.Config;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConfigLoaderTest {

    // Test bean class for configuration mapping
    public static class DatabaseConfig {
        private String url;
        private String user;
        private String password;

        // Getters and setters for Typesafe Config
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class AppConfig {
        private String contextPath;

        public String getContextPath() { return contextPath; }
        public void setContextPath(String contextPath) { this.contextPath = contextPath; }
    }


    @Test
    public void testLoadConfigByEnv() {
        Config config = ConfigLoader.loadConfigByEnv("dev");
        assertNotNull(config);
        assertThat(config.getString("database.url")).isEqualTo("jdbc:localhost:1234:foo");
        assertThat(config.getString("database.user")).isEqualTo("app_user");
        assertThat(config.getString("database.password")).isEqualTo("1");
        assertThat(config.getStringList("names")).isEqualTo(List.of("jack", "leo", "bill"));
        assertThat(config.getString("app.context-path")).isEqualTo("are you ok");
    }

    @Test
    public void testLoadConfigWithSingleFile() {
        // Arrange & Act
        Config config = ConfigLoader.loadConfig("config/application.conf");
        // Assert
        assertNotNull(config);
        assertThat(config.getString("database.url")).isEqualTo("NOT-SET");
        assertThat(config.getString("database.user")).isEqualTo("DUMMY");
        assertThat(config.getString("database.password")).isEqualTo("foo");
        assertThat(config.getString("app.context-path")).isEqualTo("are you ok");
    }

    @Test
    public void testLoadConfigWithFallback() {
        // Arrange & Act
        Config config = ConfigLoader.loadConfig("config/application.conf", "config/application-dev.conf");

        // Assert
        assertNotNull(config);
        // Values from application-dev.conf should override application.conf
        assertThat(config.getString("database.url")).isEqualTo("jdbc:localhost:1234:foo");
        assertThat(config.getString("database.user")).isEqualTo("app_user");
        assertThat(config.getString("database.password")).isEqualTo("1");
        assertThat(config.getStringList("names")).isEqualTo(List.of("jack", "leo", "bill"));
        // Values only in application.conf should still be present
        assertThat(config.getString("app.context-path")).isEqualTo("are you ok");
        assertThat(config.getString("context_path")).isEqualTo("hahaha");
    }

    @Test
    public void testLoadConfigAsBeanWithPath() {
        DatabaseConfig dbConfig = ConfigLoader.loadConfigAsBean(
                "config/application-dev.conf", "database", DatabaseConfig.class);
        assertNotNull(dbConfig);
        assertThat(dbConfig.getUrl()).isEqualTo("jdbc:localhost:1234:foo");
        assertThat(dbConfig.getUser()).isEqualTo("app_user");
        assertThat(dbConfig.getPassword()).isEqualTo("1");
    }

    @Test
    public void testLoadConfigAsBeanWithoutPath() {
        // Arrange & Act
        AppConfig appConfig = ConfigLoader.loadConfigAsBean("config/application.conf", "app", AppConfig.class);

        // Assert
        assertNotNull(appConfig);
        assertThat(appConfig.getContextPath()).isEqualTo("are you ok");
    }

    @Test
    public void testLoadConfigAsBeanWithConfigObjectAndPath() {
        // Arrange
        Config config = ConfigLoader.loadConfig("config/application-dev.conf");

        // Act
        DatabaseConfig dbConfig = ConfigLoader.loadConfigAsBean(config, "database", DatabaseConfig.class);

        // Assert
        assertNotNull(dbConfig);
        assertThat(dbConfig.getUrl()).isEqualTo("jdbc:localhost:1234:foo");
        assertThat(dbConfig.getUser()).isEqualTo("app_user");
        assertThat(dbConfig.getPassword()).isEqualTo("1");
    }

    @Test
    public void testLoadConfigAsBeanWithConfigObject() {
        // Arrange
        Config config = ConfigLoader.loadConfig("config/application.conf");

        // Act
        AppConfig appConfig = ConfigLoader.loadConfigAsBean(config, "app", AppConfig.class);

        // Assert
        assertNotNull(appConfig);
        assertThat(appConfig.getContextPath()).isEqualTo("are you ok");
    }

    @Test
    public void testLoadConfigAsBeanWithNonExistentPath() {
        // Arrange
        Config config = ConfigLoader.loadConfig("config/application.conf");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                ConfigLoader.loadConfigAsBean(config, "non-existent-path", DatabaseConfig.class));
        assertThat(exception.getMessage()).isEqualTo("Config path non-existent-path not found");
    }

    @Test
    public void testLoadConfigAsBeanWithInvalidBeanClass() {
        Config config = ConfigLoader.loadConfig("config/application.conf");
        assertThat(ConfigLoader.loadConfigAsBean(config, "database", InvalidConfigBean.class).url)
            .isNull();
    }

    public static class InvalidConfigBean {
        private String url;
        // Missing setUrl method
        public String getUrl() { return url; }
    }

    @Test
    public void testLoadDefault() {
        Config config = ConfigLoader.loadDefault();
        assertNotNull(config);
        assertThat(config.getString("test.string-value")).isEqualTo("default-loaded");
        assertThat(config.getInt("test.int-value")).isEqualTo(42);
        assertThat(config.getBoolean("test.boolean-value")).isTrue();
    }

    @Test
    public void testLoadConfigWithOverrides() {
        Config config = ConfigLoader.loadConfigWithOverrides("config/application.conf");
        assertNotNull(config);
        assertThat(config.getString("database.url")).isEqualTo("NOT-SET");
        assertThat(config.getString("database.user")).isEqualTo("DUMMY");
    }

    @Test
    public void testLoadConfigWithClassLoader() {
        ClassLoader classLoader = getClass().getClassLoader();
        Config config = ConfigLoader.loadConfig("config/application.conf", classLoader);
        assertNotNull(config);
        assertThat(config.getString("database.url")).isEqualTo("NOT-SET");
        assertThat(config.getString("database.user")).isEqualTo("DUMMY");
    }

    @Test
    public void testLoadConfigFallbackWithClassLoader() {
        ClassLoader classLoader = getClass().getClassLoader();
        Config config = ConfigLoader.loadConfig("config/application.conf", "config/application-dev.conf", classLoader);
        assertNotNull(config);
        assertThat(config.getString("database.url")).isEqualTo("jdbc:localhost:1234:foo");
        assertThat(config.getString("database.user")).isEqualTo("app_user");
    }

    @Test
    public void testLoadConfigAsBeanList() {
        // Test loading a list of config objects converted to beans
        Config config = ConfigLoader.loadConfig("config/list-of-configs.conf");
        List<DatabaseConfig> databases = ConfigLoader.loadConfigAsBeanList(config, "databases", DatabaseConfig.class);
        assertThat(databases).hasSize(2);
        assertThat(databases.get(0).getUrl()).isEqualTo("jdbc:localhost:1234:foo");
        assertThat(databases.get(0).getUser()).isEqualTo("app_user");
        assertThat(databases.get(1).getUrl()).isEqualTo("jdbc:localhost:5432:bar");
        assertThat(databases.get(1).getUser()).isEqualTo("app_admin");
    }

    @Test
    public void testLoadConfigAsBeanListWithDefault() {
        Config config = ConfigLoader.loadConfig("config/application.conf");
        List<DatabaseConfig> defaultList = List.of();
        List<DatabaseConfig> result = ConfigLoader.loadConfigAsBeanList(config, "non-existent-path", DatabaseConfig.class, defaultList);
        assertThat(result).isEqualTo(defaultList);
    }

    @Test
    public void testLoadConfigAsBeanList_wrongType_throwsIllegalArgument() {
        Config config = ConfigLoader.loadConfig("config/application.conf");
        // database is an object, not a list, so it should throw
        assertThrows(IllegalArgumentException.class, () ->
                ConfigLoader.loadConfigAsBeanList(config, "database", DatabaseConfig.class));
    }
}
