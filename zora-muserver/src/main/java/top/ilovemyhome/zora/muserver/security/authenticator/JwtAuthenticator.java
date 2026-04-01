package top.ilovemyhome.zora.muserver.security.authenticator;

import top.ilovemyhome.zora.muserver.security.core.User;

public interface JwtAuthenticator extends TokenAuthenticator {

    String generateJwt(User user);

}
