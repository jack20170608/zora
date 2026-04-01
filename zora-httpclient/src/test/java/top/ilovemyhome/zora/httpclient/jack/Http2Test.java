package top.ilovemyhome.zora.httpclient.jack;

import io.muserver.Http2ConfigBuilder;
import io.muserver.HttpsConfigBuilder;
import io.muserver.Method;
import io.muserver.MuServer;
import org.junit.jupiter.api.Test;

import static io.muserver.MuServerBuilder.httpsServer;

public class Http2Test {

    @Test
    public void testHttp2(){
        System.out.println("hi");
    }

    public static void main(String[] args) {
//        System.setProperty("java.net.preferIPv4Stack","true");
        HttpsConfigBuilder httpsConfigBuilder = HttpsConfigBuilder.unsignedLocalhost();

        MuServer server = httpsServer()
            .withHttpsConfig(httpsConfigBuilder)
            .withHttpsPort(10086)
            .withHttp2Config(Http2ConfigBuilder.http2EnabledIfAvailable())
            .addHandler(Method.GET, "/", (req, resp, pp) -> {
                resp.write("The HTTP protocol is " + req.protocol());
            })
            .start();
        System.out.println("Server started at " + server.uri());
    }
}
