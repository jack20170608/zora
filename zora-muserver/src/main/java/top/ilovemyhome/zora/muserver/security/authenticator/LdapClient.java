package top.ilovemyhome.zora.muserver.security.authenticator;

import java.security.Principal;

public interface LdapClient {
    Principal authenticate(String username, String password);
}
