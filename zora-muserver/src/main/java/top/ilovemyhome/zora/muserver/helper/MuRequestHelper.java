package top.ilovemyhome.zora.muserver.helper;

import io.muserver.Cookie;
import io.muserver.MuRequest;
import top.ilovemyhome.zora.text.SharedString;

import java.net.InetAddress;
import java.util.Map;

import static top.ilovemyhome.zora.text.SharedString.BR;

public class MuRequestHelper {

    private MuRequestHelper(){}

    public static String dumpHostInfo(){
        var builder = new StringBuilder();
        try {
            InetAddress addr = InetAddress.getLocalHost();
            String hostname = addr.getHostName();
            String ip = addr.getHostAddress();
            builder.append("Hostname: ").append(hostname).append(BR);
            builder.append("IP: ").append(ip).append(BR);
        }catch (Throwable t){
            builder.append("Hostname: ").append(SharedString.EMPTY).append(BR);
            builder.append("IP: ").append(SharedString.EMPTY).append(BR);
        }
        return builder.toString();
    }

    public static String dumpRequestInfo(MuRequest request){
        var builder = new StringBuilder();
        builder.append("Protocol: ").append(request.protocol()).append(BR);
        builder.append("Method: ").append(request.method()).append(BR);
        builder.append("URI: ").append(request.uri()).append(BR);
        builder.append("QueryString: ").append(request.query().toString()).append(BR);
        builder.append("RemoteAddress: ").append(request.remoteAddress()).append(BR);
        builder.append("ClientIp: ").append(request.clientIP()).append(BR);
        builder.append("ContextPath: ").append(request.contextPath()).append(BR);
        builder.append("RelativePath: ").append(request.relativePath()).append(BR);
        return builder.toString();
    }

    public static String dumpHeaderInfo(MuRequest request){
        var builder = new StringBuilder();
        for (Map.Entry<String, String> header : request.headers()) {
            builder.append(header.getKey()).append(": ").append(header.getValue()).append(BR);
        }
        return builder.toString();
    }

    public static String dumpCookies(MuRequest request){
        var builder = new StringBuilder();
        for (Cookie cookie : request.cookies()){
            builder.append(cookie.name()).append(": ").append(cookie.value()).append(BR);
        }
        return builder.toString();
    }
}
