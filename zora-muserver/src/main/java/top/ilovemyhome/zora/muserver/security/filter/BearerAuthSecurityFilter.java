package top.ilovemyhome.zora.muserver.security.filter;

import io.muserver.Mutils;
import io.muserver.rest.Authorizer;
import io.muserver.rest.MuRuntimeDelegate;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.muserver.security.authenticator.JwtAuthenticator;

import java.security.Principal;


public class BearerAuthSecurityFilter implements ContainerRequestFilter {

    static {
        MuRuntimeDelegate.ensureSet();
    }

    public BearerAuthSecurityFilter(JwtAuthenticator authenticator, Authorizer authorizer) {
        Mutils.notNull("authenticator", authenticator);
        Mutils.notNull("authorizer", authorizer);
//        Mutils.notNull("authRealm", authRealm);
//        if (authRealm.contains("\"")) {
//            throw new IllegalArgumentException("authRealm cannot contain a double quote");
//        }
        this.authenticator = authenticator;
        this.authorizer = authorizer;
        this.authResponse = Response
            .status(401)
            .entity("401 Unauthorized")
            .type(MediaType.TEXT_PLAIN_TYPE)
//            .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"" + authRealm + "\"")
        ;

    }

    @Override
    public void filter(ContainerRequestContext filterContext) {
        String authorization = filterContext.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterContext.abortWith(authResponse.build());
            return;
        }
        String token = authorization.substring("Bearer ".length());
        Principal principal = authenticator.authenticate(token);
        boolean isHttps = "https".equalsIgnoreCase(filterContext.getUriInfo().getRequestUri().getScheme());
        MuServerSecurityContext securityContext;
        if (principal == null) {
            securityContext = isHttps ? MuServerSecurityContext.notLoggedInHttpsContext :  MuServerSecurityContext.notLoggedInHttpContext;
        } else {
            securityContext = new MuServerSecurityContext(principal, authorizer, isHttps, SecurityContext.BASIC_AUTH);
        }
        filterContext.setSecurityContext(securityContext);
    }


    private Response.ResponseBuilder authResponse;
    private final JwtAuthenticator authenticator;
    private final Authorizer authorizer;

    private static final Logger logger = LoggerFactory.getLogger(BearerAuthSecurityFilter.class);
}
