package top.ilovemyhome.zora.httpclient;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class URLBuilderTest {

    @Test
    public void testLocalhost() {
        URLBuilder urlBuilder = URLBuilder.of("localhost")
            .addPath("api/v1/query")
            .addParam("id", "100")
            .addParam("page", "1");

        assertThat(urlBuilder.getURL()).isEqualTo("https://localhost:443/api/v1/query?id=100&page=1");
        assertThat(urlBuilder.getRelativeURL()).isEqualTo("/api/v1/query?id=100&page=1");
    }

    @Test
    public void testAllBlank() {
        URLBuilder urlBuilder = URLBuilder.of("localhost");
        assertThat(urlBuilder.getURL()).isEqualTo("https://localhost:443");
        assertThat(urlBuilder.getRelativeURL()).isEqualTo("");
    }

    @Test
    public void testBlankPath() {
        URLBuilder urlBuilder = URLBuilder.of("localhost")
            .addParam("id", "100")
            .addParam("page", "1");
        assertThat(urlBuilder.getURL()).isEqualTo("https://localhost:443?id=100&page=1");
        assertThat(urlBuilder.getRelativeURL()).isEqualTo("?id=100&page=1");
    }


    @Test
    public void testHttpNonParam() {
        URLBuilder urlBuilder = URLBuilder.of("localhost")
            .addPath("api/v1/query");
        assertThat(urlBuilder.getURL()).isEqualTo("https://localhost:443/api/v1/query");
        assertThat(urlBuilder.getRelativeURL()).isEqualTo("/api/v1/query");
    }

    @Test
    public void testHttpWithPort() {
        URLBuilder urlBuilder = URLBuilder.of("http", "localhost", 10086)
            .addPath("api/v1/query")
            .addParam("id", "100")
            .addParam("page", "1");

        assertThat(urlBuilder.getURL()).isEqualTo("http://localhost:10086/api/v1/query?id=100&page=1");
        assertThat(urlBuilder.getRelativeURL()).isEqualTo("/api/v1/query?id=100&page=1");
    }
}
