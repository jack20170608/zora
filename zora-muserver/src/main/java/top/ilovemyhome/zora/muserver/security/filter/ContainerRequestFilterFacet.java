package top.ilovemyhome.zora.muserver.security.filter;

import io.muserver.rest.MuRuntimeDelegate;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.text.AntPathMatcher;

import java.util.List;
import java.util.Objects;


public class ContainerRequestFilterFacet implements ContainerRequestFilter {
    static {
        MuRuntimeDelegate.ensureSet();
    }

    public ContainerRequestFilterFacet(List<String> whiteList, List<ContainerRequestFilter> authFilters) {
        this.whiteList = whiteList;
        this.authFilters = authFilters;
        this.authResponse = Response
            .status(401)
            .entity("401 Unauthorized")
            .type(MediaType.TEXT_PLAIN_TYPE);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (Objects.isNull(authFilters) || authFilters.isEmpty()) {
            return;
        }
        //Check if in the white list
        String path = requestContext.getUriInfo().getRequestUri().getPath();
        if (checkWhiteList(path)) {
            return;
        }
        //filter in the chain, if success any one success, then return
        boolean success = false;
        for (ContainerRequestFilter filter : authFilters) {
            try {
                filter.filter(requestContext);
                success = true;
                break;
            } catch (Throwable e) {
                logger.warn("Error in the filter chain, authType: {}, errorMsg: {}", filter.getClass().getSimpleName(), e.getMessage());
            }
        }
        if (!success) {
            requestContext.abortWith(authResponse.build());
        }
    }


    private boolean checkWhiteList(String pathUri) {
        boolean result = false;
        if (Objects.isNull(whiteList) || whiteList.isEmpty()) {
            return false;
        }
        for (String whitePath : whiteList) {
            if (antPathMatcher.match(whitePath, pathUri)) {
                result = true;
                break;
            }
        }
        return result;
    }

    private Response.ResponseBuilder authResponse;
    private List<ContainerRequestFilter> authFilters;
    private List<String> whiteList;

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();


    private static final Logger logger = LoggerFactory.getLogger(ContainerRequestFilterFacet.class);
}
