package top.ilovemyhome.zora.muserver.security.filter;

import io.muserver.Mutils;
import io.muserver.rest.Authorizer;
import io.muserver.rest.MuRuntimeDelegate;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import top.ilovemyhome.zora.muserver.security.authenticator.TokenAuthenticator;

import java.security.Principal;
import java.util.Map;

public class CookieAuthSecurityFilter implements ContainerRequestFilter {
    static {
        MuRuntimeDelegate.ensureSet();
    }

    public CookieAuthSecurityFilter(String cookieName, TokenAuthenticator authenticator, Authorizer authorizer) {
        Mutils.notNull("cookieName", cookieName);
        Mutils.notNull("authenticator", authenticator);
        Mutils.notNull("authorizer", authorizer);
        this.cookieName = cookieName;
        this.authenticator = authenticator;
        this.authorizer = authorizer;
        authResponse = Response
            .status(401)
            .entity("401 Unauthorized")
            .type(MediaType.TEXT_PLAIN_TYPE)
//            .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"" + + "\"")
            ;
    }

    @Override
    public void filter(ContainerRequestContext filterContext) {
        String token = getFromCookie(filterContext);
        if (token == null) {
            filterContext.abortWith(authResponse.build());
            return;
        }
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

    public TokenAuthenticator getAuthenticator() {
        return authenticator;
    }

    private String getFromCookie(ContainerRequestContext containerRequestContext){
        String result = null;
        Map<String, Cookie> cookieMap = containerRequestContext.getCookies();
        for (Map.Entry<String, Cookie> entry : cookieMap.entrySet()) {
            if (entry.getKey().startsWith(cookieName)) {
                Cookie cookie = entry.getValue();
                if (cookie != null) {
                    result = cookie.getValue();
                    break;
                }
            }
        }
        return result;
    }
    private Response.ResponseBuilder authResponse;
    private final String cookieName;
    private final TokenAuthenticator authenticator;
    private final Authorizer authorizer;

}
