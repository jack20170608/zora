package top.ilovemyhome.zora.muserver.handler;

import io.muserver.MuHandler;
import io.muserver.MuRequest;
import io.muserver.MuResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.muserver.helper.MuRequestHelper;
import top.ilovemyhome.zora.text.SharedString;

import static top.ilovemyhome.zora.text.SharedString.BR;

public class HttpRequestLoggerHandler implements MuHandler {

    @Override
    public boolean handle(MuRequest muRequest, MuResponse muResponse) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(BR);
        if (logAll) {
            stringBuilder.append(SharedString.LINE_SPLITTER_DASH).append(BR)
                .append(MuRequestHelper.dumpRequestInfo(muRequest));
            stringBuilder.append(SharedString.LINE_SPLITTER_DASH).append(BR)
                .append(MuRequestHelper.dumpHeaderInfo(muRequest));
            stringBuilder.append(SharedString.LINE_SPLITTER_DASH).append(BR)
                .append(MuRequestHelper.dumpCookies(muRequest));
            logger.info(stringBuilder.toString());
        } else {
            logger.info("Request: remoteAddr=[{}], remoteIp=[{}], request=[{}]"
                , muRequest.remoteAddress(), muRequest.clientIP(), muRequest);
        }
        return false;
    }

    public HttpRequestLoggerHandler() {
        this(false);
    }

    public HttpRequestLoggerHandler(boolean logAll) {
        this.logAll = logAll;
    }

    private final boolean logAll;

    private static final Logger logger = LoggerFactory.getLogger(HttpRequestLoggerHandler.class);
}
