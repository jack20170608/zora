package top.ilovemyhome.zora.httpclient;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.httpclient.domain.HTTPRequestMultipartBody;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;

import static java.util.Objects.*;

public final class RestClient {

    public static RestClient restClient(String baseUrl) {
        return restClient(true, baseUrl, null, DEFAULT_SUCCESS_PREDICATE, 20, Duration.ofSeconds(5));
    }

    public static RestClient restClient(boolean safeClient, String baseUrl) {
        return restClient(safeClient, baseUrl, null, DEFAULT_SUCCESS_PREDICATE, 20, Duration.ofSeconds(5));
    }

    public static RestClient restClient(boolean safeClient, String baseUrl, String authorization
        , IntPredicate successPredicate, int retryAttempts, Duration retryDuration) {
        HttpClient httpClient = null;
        if (safeClient) {
            httpClient = HttpClients.safeClient();
        } else {
            httpClient = HttpClients.unsafeClient();
        }
        HttpRetryClient retryablerHttpClient = HttpRetryClient.builder(httpClient)
            .withMaxAttempts(retryAttempts)
            .withSuccessPredicate(successPredicate)
            .withDelay(retryDuration)
            .withLogConsumer(LOGGER::info)
            .build();
        return new RestClient(baseUrl, retryablerHttpClient);
    }

    public RestClient(String baseUrl, HttpRetryClient httpRetryClient) {
        this.baseUrl = baseUrl;
        this.httpRetryClient = httpRetryClient;
    }


    public File getFile(String url, Map<String, String> queryParams, Map<String, String> headers, String destinationFile) throws Exception {
        File resultFile = new File(destinationFile);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(toUrl(url, queryParams)));
        headers.forEach(requestBuilder::header);
        CompletableFuture<HttpResponse<InputStream>> responseCompletableFuture =
            httpRetryClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
        HttpResponse<InputStream> httpResponse = responseCompletableFuture.get(3600, TimeUnit.SECONDS);
        if (httpResponse.statusCode() == 200) {
            try (FileOutputStream outputStream = new FileOutputStream(resultFile)) {
                int read;
                byte[] bytes = new byte[1024];
                while ((read = httpResponse.body().read(bytes)) != -1) {
                    outputStream.write(bytes, 0, read);
                }
            }
        } else {
            throw new RuntimeException("Unexpected response code: " + httpResponse.statusCode());
        }
        return resultFile;
    }

    public CompletableFuture<HttpResponse<String>> get(String uri, Map<String, String> queryParams, Map<String, String> headers) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(toUrl(uri, queryParams)));
        headers.forEach(requestBuilder::header);
        return httpRetryClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }


    public CompletableFuture<HttpResponse<String>> postFilesInForm(String uri, Map<String, String> queryParams
        , Map<String, String> headers, String filesPartName, List<Path> files) throws Exception {
        HTTPRequestMultipartBody.Builder builder = HTTPRequestMultipartBody.builder();
        if (isNull(files) || files.isEmpty() || files.size() > 10) {
            throw new IllegalArgumentException("Files must contain less than 10 files");
        }
        files.forEach(f -> {
            if (isNull(f) || !Files.isRegularFile(f) || !Files.isReadable(f)) {
                throw new IllegalArgumentException("File is not readable, file name is " + f.getFileName());
            }
            try {
                if (Files.size(f) > MAX_FILE_SIZE_GB) {
                    throw new IllegalArgumentException("File size is greater than max file size " + MAX_FILE_SIZE_GB);
                }
                builder.addPart(filesPartName, f, null, f.toFile().getName());
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse file " + f.getFileName(), e);
            }
        });
        return postInForm(uri, queryParams, headers, builder.build());

    }

    public CompletableFuture<HttpResponse<String>> postInForm(String uri, Map<String, String> queryParams
        , Map<String, String> headers, HTTPRequestMultipartBody multipartBody) throws Exception {
        requireNonNull(headers);
        requireNonNull(multipartBody);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .header("Content-Type", multipartBody.getContentType())
            .uri(URI.create(toUrl(uri, queryParams)))
            .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody.getBody()));
        headers.forEach(requestBuilder::header);
        return httpRetryClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public CompletableFuture<HttpResponse<String>> postInForm(String uri, Map<String, String> queryParams
        , Map<String, String> headers) {
        requireNonNull(headers);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .GET()
            .uri(URI.create(toUrl(uri, queryParams)));
        headers.forEach(requestBuilder::header);
        return httpRetryClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public CompletableFuture<HttpResponse<String>> post(String uri, String body) {
        return post(uri, null, Map.of(), body);
    }

    public CompletableFuture<HttpResponse<String>> post(String uri, Map<String, String> queryParams, Map<String, String> headers, String body) {
        requireNonNull(headers);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .uri(URI.create(toUrl(uri, queryParams)));
        headers.forEach(requestBuilder::header);
        return httpRetryClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public CompletableFuture<HttpResponse<String>> put(String uri, String body) {
        return put(uri, null, Map.of(), body);
    }

    public CompletableFuture<HttpResponse<String>> put(String uri, Map<String, String> queryParams, Map<String, String> headers, String body) {
        requireNonNull(headers);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .uri(URI.create(toUrl(uri, queryParams)));
        headers.forEach(requestBuilder::header);
        return httpRetryClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public CompletableFuture<HttpResponse<String>> delete(String uri, Map<String, String> queryParams, Map<String, String> headers) {
        requireNonNull(headers);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .DELETE()
            .uri(URI.create(toUrl(uri, queryParams)));
        headers.forEach(requestBuilder::header);
        return httpRetryClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String toUrl(String uri, Map<String, String> queryParams) {
        StringBuilder stringBuilder = new StringBuilder();
        if (nonNull(baseUrl)) {
            if (baseUrl.endsWith("/")) {
                stringBuilder.append(baseUrl);
            } else {
                stringBuilder.append(baseUrl).append("/");
            }
        }
        URLBuilder urlBuilder = URLBuilder.of()
            .addPath(uri);
        if (nonNull(queryParams)) {
            queryParams.keySet().forEach(k -> urlBuilder.addParam(k, queryParams.get(k)));
        }
        stringBuilder.append(StringUtils.substringAfter(urlBuilder.getRelativeURL(), "/"));
        return stringBuilder.toString();
    }

    private static final long MAX_FILE_SIZE_GB = 1024 * 1024 * 1024;
    private static final long MAX_FILE_SIZE_MB = 1024 * 1024;
    private static final long MAX_FILE_SIZE_KB = 1024;
    private static final long MAX_FILE_SIZE = MAX_FILE_SIZE_GB;

    private final String baseUrl;
    private final HttpRetryClient httpRetryClient;
    private static final IntPredicate DEFAULT_SUCCESS_PREDICATE = status -> status < 500;

    private static final Logger LOGGER = LoggerFactory.getLogger(RestClient.class);
}
