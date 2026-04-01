package top.ilovemyhome.zora.muserver.handler;

import io.muserver.MuHandler;
import io.muserver.MuRequest;
import io.muserver.MuResponse;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.muserver.security.core.CIDRValidator;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public class ClientNetAddressHandler implements MuHandler {

    public ClientNetAddressHandler(List<CIDRValidator> cidrValidators
        , Function<MuRequest, String> clientNetAddressGetter
        , Predicate<MuRequest> requestPredicate) {
        this.cidrValidators = List.copyOf(cidrValidators);
        this.clientNetAddressGetter = clientNetAddressGetter;
        this.requestPredicate = requestPredicate;
    }

    @Override
    public boolean handle(MuRequest muRequest, MuResponse muResponse) {
        boolean doTest = this.requestPredicate.test(muRequest);
        if (!doTest) {
            return false;
        }
        String clientNetAddress = clientNetAddressGetter.apply(muRequest);
        boolean isAllowed = cidrValidators.stream()
            .anyMatch(validator -> validator.isValid(clientNetAddressGetter.apply(muRequest)));
        if (!isAllowed) {
            logger.warn("Client net address {} is not allowed", clientNetAddress);
            promptForIpValidation(muResponse);
        }
        return !isAllowed;
    }

    private void promptForIpValidation(MuResponse muResponse){
        muResponse.status(Response.Status.FORBIDDEN.getStatusCode());
        muResponse.write("Not allowed.");
    }

    private final Predicate<MuRequest> requestPredicate;
    private final List<CIDRValidator> cidrValidators;
    private final Function<MuRequest, String> clientNetAddressGetter;


    public static final Function<MuRequest, String> BY_REMOATE_ADDRESS
        = (muRequest) -> muRequest.remoteAddress();

    public static final Function<MuRequest, String> BY_CLIENT_IP
        = (muRequest) -> muRequest.clientIP();

    private static final Logger logger = LoggerFactory.getLogger(ClientNetAddressHandler.class);

}
