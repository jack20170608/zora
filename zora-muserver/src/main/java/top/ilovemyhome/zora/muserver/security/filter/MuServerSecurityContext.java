package top.ilovemyhome.zora.muserver.security.filter;

import io.muserver.rest.Authorizer;
import jakarta.ws.rs.core.SecurityContext;

import java.security.Principal;

public class MuServerSecurityContext implements SecurityContext {


    public MuServerSecurityContext(Principal principal, Authorizer authorizer, boolean isHttps, String authenticationScheme) {
        this.principal = principal;
        this.authorizer = authorizer;
        this.isHttps = isHttps;
        this.authenticationScheme = authenticationScheme;
    }

    @Override
    public Principal getUserPrincipal() {
        return principal;
    }

    @Override
    public boolean isUserInRole(String s) {
        return authorizer.isInRole(principal, s);
    }

    @Override
    public boolean isSecure() {
        return isHttps;
    }

    @Override
    public String getAuthenticationScheme() {
        return authenticationScheme;
    }

    public static final MuServerSecurityContext notLoggedInHttpContext = new MuServerSecurityContext(null, (principal1, role) -> false, false, null);
    public static final MuServerSecurityContext notLoggedInHttpsContext = new MuServerSecurityContext(null, (principal1, role) -> false, true, null);


    private final Principal principal;
    private final Authorizer authorizer;
    private final boolean isHttps;
    private final String authenticationScheme;
}
