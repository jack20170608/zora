package top.ilovemyhome.zora.httpclient.websocket;

import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Thread.sleep;
import static org.slf4j.LoggerFactory.getLogger;

public class WebSocketApp {

    private static final Logger logger = getLogger(WebSocketApp.class.getName());

    private static final ExecutorService executor = Executors.newFixedThreadPool(3);


    static void main(String... args) throws InterruptedException {

        HttpClient httpClient = HttpClient.newBuilder().executor(executor).build();

        WebSocket webSocket = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:10086"),
                        new EchoListener(executor))
                .join();

        logger.info("WebSocket created");
        webSocket.sendText("hello", false);
        webSocket.sendText("world", false);


        sleep(800);

        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok")
                .thenRun(() -> logger.info("Sent close"))
                .join();

    }
}
