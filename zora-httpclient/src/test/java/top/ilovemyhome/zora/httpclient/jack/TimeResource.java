package top.ilovemyhome.zora.httpclient.jack;

import io.muserver.rest.MuRuntimeDelegate;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseBroadcaster;
import jakarta.ws.rs.sse.SseEventSink;


import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Path("/sse/counter")
public class TimeResource {

    long count = 0;
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    Sse sse = MuRuntimeDelegate.createSseFactory();
    SseBroadcaster broadcaster = sse.newBroadcaster();

    public TimeResource() {
        this.start();
    }

    @GET
    @Produces("text/event-stream")
    public void registerListener(@Context SseEventSink eventSink) {
        broadcaster.register(eventSink);
    }

    public void start() {
        executor.scheduleAtFixedRate(() -> {
            count++;
            String data = String.format(jsonData, count, count %3 == 0 ? "bill" : "lei");
            broadcaster.broadcast(sse.newEvent(data));
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    private String jsonData = """
        {
            "id": "%s",
            "name": "%s",
            "status" : "active"
        }
        """;
}
