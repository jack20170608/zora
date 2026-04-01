package top.ilovemyhome.zora.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigBeanFactory;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ConfigLoader {

    private ConfigLoader() {
    }

    public static final String DEFAULT_CONFIG_PATH = "config/application.conf";
    public static final String DEFAULT_CONFIG_ENV = "config/application-%s.conf";

    public static Config loadConfigByEnv(String env){
        return loadConfig(DEFAULT_CONFIG_PATH, String.format(DEFAULT_CONFIG_ENV, env));
    }

    public static Config loadConfig(String conf){
        return ConfigFactory.parseResources(conf).resolve();
    }

    public static Config loadConfig(String fallbackConf, String conf){
        Config fallbackConfig = ConfigFactory.parseResources(fallbackConf);
        Config specConfig = ConfigFactory.parseResources(conf);
        return specConfig.withFallback(fallbackConfig).resolve();
    }

    /**
     * Load config using Typesafe Config's default loading order:
     * <ol>
     *     <li>application.conf from classpath</li>
     *     <li>system properties</li>
     *     <li>environment variables</li>
     * </ol>
     *
     * @return resolved config
     */
    public static Config loadDefault() {
        return ConfigFactory.load();
    }

    /**
     * Load config from resource and allow system properties/environment variables to override.
     *
     * @param conf config resource path
     * @return resolved config with overrides
     */
    public static Config loadConfigWithOverrides(String conf) {
        return ConfigFactory.load(ConfigFactory.parseResources(conf));
    }

    /**
     * Load config from resource using the specified class loader.
     *
     * @param conf        config resource path
     * @param classLoader class loader to use for loading resources
     * @return resolved config
     */
    public static Config loadConfig(String conf, ClassLoader classLoader) {
        return ConfigFactory.parseResources(classLoader, conf).resolve();
    }

    /**
     * Load config with fallback using the specified class loader.
     *
     * @param fallbackConf default fallback config resource
     * @param conf        specific config resource that overrides fallback
     * @param classLoader  class loader to use for loading resources
     * @return resolved config with fallback
     */
    public static Config loadConfig(String fallbackConf, String conf, ClassLoader classLoader) {
        Config fallbackConfig = ConfigFactory.parseResources(classLoader, fallbackConf);
        Config specConfig = ConfigFactory.parseResources(classLoader, conf);
        return specConfig.withFallback(fallbackConfig).resolve();
    }

    public static <T> T loadConfigAsBean(String conf, String path, Class<T> clazz){
        Config config = loadConfig(conf);
        return loadConfigAsBean(config, path, clazz);
    }

    public static <T> T loadConfigAsBean(String conf, Class<T> clazz){
        Config config = loadConfig(conf);
        return loadConfigAsBean(config, clazz);
    }

    public static <T> T loadConfigAsBean(Config config , String path,  Class<T> clazz){
        if (!config.hasPath(path)){
            throw new IllegalArgumentException("Config path " + path + " not found");
        }
        Config pathConfig = config.getConfig(path);
        Config resolvedConfig = pathConfig.resolve();
        return ConfigBeanFactory.create(resolvedConfig, clazz);
    }

    public static <T> T loadConfigAsBean(Config config, Class<T> clazz){
        return ConfigBeanFactory.create(config, clazz);
    }

    public static <T> List<T> loadConfigAsBeanList(Config config, String path, Class<T> clazz, List<T> defaultValue){
        try {
            List<T> result = loadConfigAsBeanList(config, path, clazz);
            return result.isEmpty() ? defaultValue : result;
        } catch (Exception e) {
            logger.warn("Failed to load config list, using default value", e);
            return defaultValue;
        }
    }

    public static <T> List<T> loadConfigAsBeanList(Config config, String path, Class<T> clazz){
        if (Objects.isNull(config)) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        if (Objects.isNull(clazz)) {
            throw new IllegalArgumentException("Class cannot be null");
        }
        if (Objects.isNull(path) || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        try {
            // 检查路径是否存在
            if (!config.hasPath(path)) {
                logger.warn("Config path {} not found, returning empty list", path);
                return Collections.emptyList();
            }

            // 获取配置列表
            List<? extends Config> configList = config.getConfigList(path);
            if (configList == null || configList.isEmpty()) {
                logger.debug("Config list at path {} is empty", path);
                return Collections.emptyList();
            }

            // 转换为Java对象列表
            List<T> resultList = configList.stream()
                .map(subConfig -> ConfigBeanFactory.create(subConfig, clazz))
                .collect(Collectors.toList());

            logger.debug("Successfully loaded {} beans from path {}", resultList.size(), path);
            return resultList;

        } catch (ConfigException.WrongType e) {
            logger.error("Config at path {} is not a list type", path, e);
            throw new IllegalArgumentException("Config at path " + path + " is not a list type", e);
        } catch (ConfigException e) {
            logger.error("Failed to load config list from path {}", path, e);
            throw new RuntimeException("Failed to load config list from path " + path, e);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
}
