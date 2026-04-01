package top.ilovemyhome.zora.muserver.security.authenticator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.ilovemyhome.zora.muserver.SharedTestResources;
import top.ilovemyhome.zora.muserver.security.core.User;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class JwtAuthenticatorImplTest {

    JwtAuthenticatorImpl jwtAuthenticator;
    JwtAuthenticatorImpl jwtAuthenticator2;

    @BeforeEach
    public void setUp(){
        jwtAuthenticator = new JwtAuthenticatorImpl(
            "foo"
            , "foo-subject"
            , 1000 * 60* 60 * 24
            , "classpath:key/public.key"
            , "classpath:key/private.key");

        jwtAuthenticator2 = new JwtAuthenticatorImpl(
            "foo"
            , "foo-subject"
            , 1000 * 60* 60 * 24
            , "classpath:key2/public_key.pem"
            , "classpath:key2/private_key.pem");
    }

    @Test
    public void testAuthenticate() {
        User user = SharedTestResources.createTestingUsers().get(0);
        String jwt = jwtAuthenticator.generateJwt(user);
        assertThat(jwtAuthenticator.authenticate(jwt)).isEqualTo(user);
    }

    @Test
    public void testBadOne(){
        assertThat(jwtAuthenticator.authenticate(null)).isNull();
        assertThat(jwtAuthenticator.authenticate("")).isNull();
        assertThat(jwtAuthenticator.authenticate("bad")).isNull();
    }

    @Test
    public void testBadSignature(){
        User user = SharedTestResources.createTestingUsers().get(0);
        String jwt = jwtAuthenticator.generateJwt(user);
        jwt = jwt.substring(0, jwt.length() - 1);
        assertThat(jwtAuthenticator.authenticate(jwt)).isNull();
    }

    @Test
    public void testTokenExpired(){
        String expiredToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImI5NDI4NThhLTY1MTctNDU3OS1iYTk2LWE4NDgzNDM4OWY1YiJ9.eyJpc3MiOiJmb28iLCJleHAiOjE3NjYxMTQ2ODksImlhdCI6MTc2NjExNDY4OCwic3ViIjoiZm9vLXN1YmplY3QiLCJkaXNwbGF5TmFtZSI6IkFkbWluIiwicm9sZXMiOiJhZG1pbiIsImlkIjoiMSIsIm5hbWUiOiJhZG1pbiJ9.pZVbZr9bnYt2eIOflDAaRyWaLoN-lJkY0NViQsEzeGvRdK8eN5BMy9QsRim0hA6mEoQlZngrgYp4HVO7FqdWXzS2i58WcenJiEgHjoSVpQd74ZLVsWU34Z5zk4tAJgD5IW_MYS77m3WM-1uKM6hjy2ESXEA1MGkwd3quUrd1NR3Xe6U4Qu1DQtrOTeszk_Zbi01yQ8934FR8jgXQXRKspRK7ID1UsKL0sVcC2i9J1TnoA9LmCZsBK9fmook9nL-mncco9FLc70QJgTY4_CU4E1yEvjZ1aQpgTdMm7bk7gUi6fiVFVxCuhNkN_oFrsmv-zhMH2-URcDOBDwUzP9rMSQ";
        assertThat(jwtAuthenticator.authenticate(expiredToken)).isNull();
    }

    @Test
    public void testBadSignature2(){
        User user = SharedTestResources.createTestingUsers().get(0);
        String jwt = jwtAuthenticator2.generateJwt(user);
        assertThat(jwtAuthenticator.authenticate(jwt)).isNull();
    }
}
