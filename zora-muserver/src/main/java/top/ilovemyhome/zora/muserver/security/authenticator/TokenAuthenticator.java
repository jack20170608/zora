package top.ilovemyhome.zora.muserver.security.authenticator;

import java.security.Principal;

public interface TokenAuthenticator {

    Principal authenticate(String token) ;
}
