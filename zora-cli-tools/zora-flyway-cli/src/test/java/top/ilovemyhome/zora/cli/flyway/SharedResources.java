package top.ilovemyhome.zora.cli.flyway;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.apache.commons.lang3.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Objects;

public class SharedResources {

    public static DataSource getDataSource(String user, String  database) throws Exception {
        try {
            pg = EmbeddedPostgres.builder()
                .setLocaleConfig("locale", "en_US")
                .setLocaleConfig("encoding", "UTF-8")
                .start();
            return pg.getDatabase(user, database);
        }catch (Exception e) {
            logger.error("Error {}.", e.getMessage());
            throw e;
        }
    }

    public static void closePg() throws Exception {
        if (Objects.nonNull(pg)) {
            ThreadUtils.sleepQuietly(Duration.ofSeconds(1));
            pg.close();
        }
    }

    public static EmbeddedPostgres pg;

    private static final Logger logger = LoggerFactory.getLogger(SharedResources.class);
}
