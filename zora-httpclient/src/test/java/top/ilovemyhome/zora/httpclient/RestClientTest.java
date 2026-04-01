package top.ilovemyhome.zora.httpclient;

import io.muserver.MuServer;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.httpclient.exception.ThrowingRunnable;

import java.io.File;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class RestClientTest {


    @BeforeAll
    public static void startHttpServer() {
        muServer = StartTestServer.start();
        restClient = RestClient.restClient(false, muServer.uri().toString());
        shortRetryRestClient = RestClient.restClient(false, muServer.uri().toString()
            , null, code -> code < 500, 3, Duration.ofSeconds(1));
    }

    @AfterAll
    public static void stopHttpServer() {
        if (muServer != null) {
            muServer.stop();
        }
    }

    private void reset() {
        Runnable invoke = ThrowingRunnable.unchecked(() -> {
            HttpResponse<String> response = restClient.get("api/v1/order/reset", null, Map.of())
                .get(5, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("success");
            LOGGER.info(response.body());
        });
        invoke.run();
    }

    @Test
    public void testHello() {
        Runnable invoke = ThrowingRunnable.unchecked(() -> {
            HttpResponse<String> response = restClient.get("hello", null, Map.of())
                .get(5, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("Hello World");
            LOGGER.info(response.body());
        });
        invoke.run();
    }

    @Test
    public void testHiPost() {
        Runnable invoke = ThrowingRunnable.unchecked(() -> {
            HttpResponse<String> response = restClient.post("hi", null, Map.of("foo", "bar"), "")
                .get(5, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("Hi!");
            assertThat(response.headers().firstValue("foo")).get().isEqualTo("bar");
            LOGGER.info(response.body());
        });
        invoke.run();
    }

    @Test
    public void testGetAll() {
        reset();
        Runnable invoke = ThrowingRunnable.unchecked(() -> {
            HttpResponse<String> response = restClient.get("api/v1/order/getall", null
                    , Map.of())
                .get(5, TimeUnit.SECONDS);
            assertThat(response.body().trim()).isEqualTo(getAllJsonResponse.trim());
            assertThat(response.statusCode()).isEqualTo(200);
            LOGGER.info(response.body());
        });
        invoke.run();
    }

    @Test
    public void testPut() {
        Runnable invoke = ThrowingRunnable.unchecked(() -> {
            HttpResponse<String> response = restClient.put("api/v1/order/put", Map.of("id", "1")
                    , Map.of("Content-Type", MediaType.APPLICATION_JSON), "{\"counterparty\":\"CCB\",\"amount\":111.00}")
                .get(5, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("{\"id\":1,\"counterparty\":\"CCB\",\"amount\":111.00}");
            LOGGER.info(response.body());
        });
        invoke.run();
    }


    @Test
    public void testGetById() {
        reset();
        Runnable invoke = ThrowingRunnable.unchecked(() -> {
            HttpResponse<String> response = restClient.get("api/v1/order/1", null
                    , Map.of())
                .get(5, TimeUnit.SECONDS);
            assertThat(response.body().trim()).isEqualTo("{\"id\":1,\"counterparty\":\"CCB\",\"amount\":100.00}");
            assertThat(response.statusCode()).isEqualTo(200);
            LOGGER.info(response.body());
        });
        invoke.run();
    }


    @Test
    public void testGetByIdNotFound() {
        reset();
        Runnable invoke = ThrowingRunnable.unchecked(() -> {
            HttpResponse<String> response = restClient.get("api/v1/order/999", null
                    , Map.of())
                .get(5, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.body()).isEqualTo("<h1>404 Not Found</h1><p>Order with id 999 not found</p>");
        });
        invoke.run();
    }

    @Test
    public void testPostNewAndDelete() {
        reset();
        Runnable invoke = ThrowingRunnable.unchecked(() -> {
            HttpResponse<String> response = restClient.post("api/v1/order", null
                    , Map.of("Content-Type", MediaType.APPLICATION_JSON), "{\"counterparty\":\"ABC\",\"amount\":200.00}")
                .get(5, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("{\"id\":4,\"counterparty\":\"ABC\",\"amount\":200.00}");
        });
        invoke.run();
    }

    @Test
    public void testFileUpload() {
        Runnable invoke = ThrowingRunnable.unchecked(() -> {
            final File file = new File(getClass().getClassLoader().getResource("dummyFile").toURI());
            HttpResponse<String> response = restClient.postFilesInForm("api/v1/order/fileUpload", null
                , Map.of("Content-Type", MediaType.MULTIPART_FORM_DATA)
                , "files", List.of(file.toPath())
                )
                .get(5, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(Files.isRegularFile(Paths.get(System.getProperty("java.io.tmpdir"), "dummyFile")))
                .isTrue();
        });
        invoke.run();
    }

    private static MuServer muServer;
    private static RestClient restClient;
    private static RestClient shortRetryRestClient;
    private static RestClient badHostRestClient = RestClient.restClient(false, "https://localhost:22");
    private static final Logger LOGGER = LoggerFactory.getLogger(RestClientTest.class);


    private final String getAllJsonResponse = """
        [{"id":1,"counterparty":"CCB","amount":100.00},{"id":2,"counterparty":"CB","amount":-100.00},{"id":3,"counterparty":"ICBC","amount":-100.00}]
        """;
}
