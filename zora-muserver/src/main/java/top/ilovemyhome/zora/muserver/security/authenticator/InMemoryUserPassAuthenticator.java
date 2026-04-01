package top.ilovemyhome.zora.muserver.security.authenticator;

import io.muserver.rest.UserPassAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.common.codec.DigestUtils;
import top.ilovemyhome.zora.muserver.security.core.User;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;


/**
 *
 */
public class InMemoryUserPassAuthenticator implements UserPassAuthenticator {


    public InMemoryUserPassAuthenticator(List<User> users) {
        this.inMemoryUserMap = users.stream()
            .map(user -> new AbstractMap.SimpleEntry<>(user.name(), user))
            .collect(Collectors.collectingAndThen(
                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue),
                Collections::unmodifiableMap
            ));
        this.usersPassHashHexMap = users.stream()
            .map(user -> new AbstractMap.SimpleEntry<>(user.name(), user.passwordHashVal()))
            .collect(Collectors.collectingAndThen(
                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue),
                Collections::unmodifiableMap
            ));
    }


    @Override
    public Principal authenticate(String userName, String password) {
        logger.info("Authenticating user: {}", userName);
        Principal result;
        if (!inMemoryUserMap.containsKey(userName)) {
            logger.warn("User not exists!");
            return null;
        }
        String storedHashPass = usersPassHashHexMap.get(userName);
        String hashedPass = DigestUtils.sha256Hex(password);
        if (!Objects.equals(storedHashPass, hashedPass)) {
            logger.warn("User password not correct!");
            return null;
        }
        result = inMemoryUserMap.get(userName);
        return result;
    }

    private final Map<String, User> inMemoryUserMap;
    private final Map<String, String> usersPassHashHexMap;

    private static final Logger logger = LoggerFactory.getLogger(InMemoryUserPassAuthenticator.class);
}
