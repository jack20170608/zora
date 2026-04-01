package top.ilovemyhome.zora.muserver.security;

import io.muserver.rest.Authorizer;
import io.muserver.rest.BasicAuthSecurityFilter;
import io.muserver.rest.MuRuntimeDelegate;
import io.muserver.rest.UserPassAuthenticator;
import jakarta.ws.rs.container.ContainerRequestFilter;
import org.apache.commons.lang3.StringUtils;
import top.ilovemyhome.zora.muserver.security.authenticator.*;
import top.ilovemyhome.zora.muserver.security.authorizer.SimpleRoleAuthorizer;
import top.ilovemyhome.zora.muserver.security.core.CookieValueType;
import top.ilovemyhome.zora.muserver.security.core.User;
import top.ilovemyhome.zora.muserver.security.filter.BearerAuthSecurityFilter;
import top.ilovemyhome.zora.muserver.security.filter.ContainerRequestFilterFacet;
import top.ilovemyhome.zora.muserver.security.filter.CookieAuthSecurityFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AppSecurityContext {

    static {
        MuRuntimeDelegate.ensureSet();
    }

    private final List<ContainerRequestFilter> authFilters;
    private final ContainerRequestFilterFacet facetFilter;

    private CookieAuthSecurityFilter cookieAuthSecurityFilter;
    private JwtAuthenticator jwtAuthenticator;
    private final List<UserPassAuthenticator> userPassAuthenticators;

    protected AppSecurityContext(
        List<User> inMemoryUser
        , LdapClient ldapClient
        , String jwtIssuer
        , String jwtSubject
        , long jwtTtl
        , String jwtPublicKeyPath
        , String jwtPrivateKeyPath
        , String cookieName
        , CookieValueType cookieValueType
        , List<String> whiteList) {

        Authorizer authorizer = new SimpleRoleAuthorizer();
        List<ContainerRequestFilter> tempList = new ArrayList<>();
        List<UserPassAuthenticator> upList = new ArrayList<>();
        //1. The in-memory authentication filter
        if (Objects.nonNull(inMemoryUser) && !inMemoryUser.isEmpty()) {
            InMemoryUserPassAuthenticator inMemoryUserPassAuthenticator = new InMemoryUserPassAuthenticator(inMemoryUser);
            BasicAuthSecurityFilter inMemBasicAuthSecurityFilter = new BasicAuthSecurityFilter(
                "/"
                , inMemoryUserPassAuthenticator
                , authorizer
            );
            tempList.add(inMemBasicAuthSecurityFilter);
            upList.add(inMemoryUserPassAuthenticator);
        }
        //2. The LDAP authentication filter
        if (Objects.nonNull(ldapClient)) {
            LdapUserPassAuthenticator ldapUserPassAuthenticator = new LdapUserPassAuthenticator(ldapClient);
            BasicAuthSecurityFilter ldapBasicAuthSecurityFilter = new BasicAuthSecurityFilter(
                "/"
                , ldapUserPassAuthenticator
                , authorizer
            );
            tempList.add(ldapBasicAuthSecurityFilter);
            upList.add(ldapUserPassAuthenticator);
        }
        //3. The JWT authentication filter
        if (Objects.nonNull(jwtIssuer) && Objects.nonNull(jwtPublicKeyPath) && Objects.nonNull(jwtPrivateKeyPath)) {
            this.jwtAuthenticator = new JwtAuthenticatorImpl(jwtIssuer, jwtSubject, jwtTtl, jwtPublicKeyPath, jwtPrivateKeyPath);
            BearerAuthSecurityFilter jwtAuthSecurityFilter = new BearerAuthSecurityFilter(
                this.jwtAuthenticator
                , authorizer
            );
            tempList.add(jwtAuthSecurityFilter);
        }
        //4. The cookie authentication filter
        if (Objects.nonNull(cookieValueType) && StringUtils.isNotBlank(cookieName)) {
            TokenAuthenticator authenticator;
            if (cookieValueType == CookieValueType.JWT) {
                if (Objects.isNull(this.jwtAuthenticator)){
                    throw new IllegalArgumentException("JWT authentication is enabled but no JWT authenticator is provided.");
                }
                authenticator = this.jwtAuthenticator;
            } else {
                authenticator = new SessionAuthenticator();
            }
            this.cookieAuthSecurityFilter = new CookieAuthSecurityFilter(cookieName, authenticator, authorizer);
            tempList.add(this.cookieAuthSecurityFilter);
        }
        this.authFilters = List.copyOf(tempList);
        this.userPassAuthenticators = List.copyOf(upList);

        //5. The container request filter facet
        facetFilter = new ContainerRequestFilterFacet(
            whiteList, authFilters
        );
    }

    public ContainerRequestFilterFacet getFacetFilter() {
        return facetFilter;
    }

    public List<ContainerRequestFilter> getAuthFilters() {
        return authFilters;
    }

    public JwtAuthenticator getJwtAuthenticator() {
        return jwtAuthenticator;
    }

    public CookieAuthSecurityFilter getCookieAuthSecurityFilter() {
        return cookieAuthSecurityFilter;
    }

    public List<UserPassAuthenticator> getUserPassAuthenticators() {
        return userPassAuthenticators;
    }

    public static Builder builder(){
        return new Builder();
    }

    public static class Builder {
        private List<User> inMemoryUser;
        private LdapClient ldapClient;
        private String jwtIssuer;
        private String jwtSubject;
        private long jwtTtl;
        private String jwtPublicKeyPath;
        private String jwtPrivateKeyPath;
        private String cookieName;
        private CookieValueType cookieValueType;
        private List<String> whiteList;

        public Builder inMemoryUser(List<User> inMemoryUser) {
            this.inMemoryUser = inMemoryUser;
            return this;
        }

        public Builder ldapClient(LdapClient ldapClient) {
            this.ldapClient = ldapClient;
            return this;
        }

        public Builder jwtIssuer(String jwtIssuer) {
            this.jwtIssuer = jwtIssuer;
            return this;
        }

        public Builder jwtSubject(String jwtSubject) {
            this.jwtSubject = jwtSubject;
            return this;
        }

        public Builder jwtTtl(long jwtTtl) {
            this.jwtTtl = jwtTtl;
            return this;
        }

        public Builder jwtPublicKeyPath(String jwtPublicKeyPath) {
            this.jwtPublicKeyPath = jwtPublicKeyPath;
            return this;
        }

        public Builder jwtPrivateKeyPath(String jwtPrivateKeyPath) {
            this.jwtPrivateKeyPath = jwtPrivateKeyPath;
            return this;
        }

        public Builder cookieName(String cookieName) {
            this.cookieName = cookieName;
            return this;
        }

        public Builder cookieValueType(CookieValueType cookieValueType) {
            this.cookieValueType = cookieValueType;
            return this;
        }

        public Builder whiteList(List<String> whiteList) {
            this.whiteList = whiteList;
            return this;
        }

        public AppSecurityContext build() {
            return new AppSecurityContext(
                inMemoryUser,
                ldapClient,
                jwtIssuer,
                jwtSubject,
                jwtTtl,
                jwtPublicKeyPath,
                jwtPrivateKeyPath,
                cookieName,
                cookieValueType,
                whiteList
            );
        }
    }
}
