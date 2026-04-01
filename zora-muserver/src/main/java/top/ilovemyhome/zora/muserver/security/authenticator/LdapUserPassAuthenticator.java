package top.ilovemyhome.zora.muserver.security.authenticator;

import io.muserver.rest.UserPassAuthenticator;

import java.security.Principal;

public class LdapUserPassAuthenticator implements UserPassAuthenticator {

    private final LdapClient ldapClient;

    public LdapUserPassAuthenticator(LdapClient ldapClient) {
        this.ldapClient = ldapClient;
    }

    @Override
    public Principal authenticate(String username, String password) {
        return ldapClient.authenticate(username, password);
    }
}
