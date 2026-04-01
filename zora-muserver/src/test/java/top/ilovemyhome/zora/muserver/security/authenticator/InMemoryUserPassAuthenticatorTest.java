package top.ilovemyhome.zora.muserver.security.authenticator;

import org.junit.jupiter.api.Test;
import top.ilovemyhome.zora.muserver.SharedTestResources;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;


public class InMemoryUserPassAuthenticatorTest {

    InMemoryUserPassAuthenticator authenticator = new InMemoryUserPassAuthenticator(SharedTestResources.createTestingUsers());

    @Test
    public void testAuthenticate() {
        assertThat(authenticator.authenticate("bar", "bar")).isNull();
        assertThat(authenticator.authenticate("admin", "123456")).isNull();
        assertThat(authenticator.authenticate("admin", "1")).isEqualTo(SharedTestResources.createTestingUsers().get(0));
        assertThat(authenticator.authenticate("ro", "2")).isEqualTo(SharedTestResources.createTestingUsers().get(1));
        assertThat(authenticator.authenticate("rw", "3")).isEqualTo(SharedTestResources.createTestingUsers().get(2));
    }
}
