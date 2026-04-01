package top.ilovemyhome.zora.muserver.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import top.ilovemyhome.zora.muserver.SharedTestResources;
import top.ilovemyhome.zora.muserver.security.authenticator.LdapClient;
import top.ilovemyhome.zora.muserver.security.core.CookieValueType;
import top.ilovemyhome.zora.muserver.security.core.User;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

public class AppSecurityContextTest {

    @Mock
    private LdapClient mockLdapClient;

    private List<User> testUsers = SharedTestResources.createTestingUsers();

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // 模拟LDAP认证
        when(mockLdapClient.authenticate("ldap_user", "password")).thenReturn(
            new User("3", "ldap_user", "LDAP User", List.of("USER"), null, null)
        );
    }

    @Test
    public void testBuilderPattern() {
        // 测试Builder模式的链式调用
        AppSecurityContext securityContext = new AppSecurityContext.Builder()
            .inMemoryUser(testUsers)
            .ldapClient(mockLdapClient)
            .jwtIssuer("test-issuer")
            .jwtSubject("test-subject")
            .jwtTtl(3600000)
            .jwtPublicKeyPath("classpath:key/public.key")
            .jwtPrivateKeyPath("classpath:key/private.key")
            .cookieName("auth-cookie")
            .cookieValueType(CookieValueType.JWT)
            .whiteList(List.of("/public/**"))
            .build();

        assertNotNull(securityContext);
        assertNotNull(securityContext.getAuthFilters());
        assertNotNull(securityContext.getFacetFilter());
    }

    @Test
    public void testInMemoryAuthentication() {
        // 测试仅配置内存认证
        AppSecurityContext securityContext = new AppSecurityContext.Builder()
            .inMemoryUser(testUsers)
            .whiteList(List.of("/public/**"))
            .build();

        assertNotNull(securityContext.getAuthFilters());
        assertEquals(1, securityContext.getAuthFilters().size());
        assertNotNull(securityContext.getFacetFilter());
    }

    @Test
    public void testLdapAuthentication() {
        // 测试仅配置LDAP认证
        AppSecurityContext securityContext = new AppSecurityContext.Builder()
            .ldapClient(mockLdapClient)
            .whiteList(List.of("/public/**"))
            .build();

        assertNotNull(securityContext.getAuthFilters());
        assertEquals(1, securityContext.getAuthFilters().size());
    }

    @Test
    public void testJwtAuthentication() {
        // 测试仅配置JWT认证
        AppSecurityContext securityContext = new AppSecurityContext.Builder()
            .jwtIssuer("test-issuer")
            .jwtSubject("test-subject")
            .jwtTtl(3600000)
            .jwtPublicKeyPath("classpath:key/public.key")
            .jwtPrivateKeyPath("classpath:key/private.key")
            .whiteList(List.of("/public/**"))
            .build();

        assertNotNull(securityContext.getAuthFilters());
        assertEquals(1, securityContext.getAuthFilters().size());
    }

    @Test
    public void testCookieAuthentication() {
        // 测试仅配置Cookie认证
        AppSecurityContext securityContext = new AppSecurityContext.Builder()
            .cookieName("auth-cookie")
            .cookieValueType(CookieValueType.J_SESSION_ID)
            .whiteList(List.of("/public/**"))
            .build();

        assertNotNull(securityContext.getAuthFilters());
        assertEquals(1, securityContext.getAuthFilters().size());
    }

    @Test
    public void testMultipleAuthenticationMethods() {
        // 测试配置多种认证方式
        AppSecurityContext securityContext = new AppSecurityContext.Builder()
            .inMemoryUser(testUsers)
            .jwtIssuer("test-issuer")
            .jwtSubject("test-subject")
            .jwtTtl(3600000)
            .jwtPublicKeyPath("classpath:key/public.key")
            .jwtPrivateKeyPath("classpath:key/private.key")
            .cookieName("auth-cookie")
            .cookieValueType(CookieValueType.JWT)
            .whiteList(List.of("/public/**"))
            .build();

        assertNotNull(securityContext.getAuthFilters());
        assertEquals(3, securityContext.getAuthFilters().size()); // 内存认证 + JWT认证 + Cookie认证
    }

    @Test
    public void testEmptyConfiguration() {
        // 测试空配置
        AppSecurityContext securityContext = new AppSecurityContext.Builder()
            .whiteList(List.of("/public/**"))
            .build();

        assertNotNull(securityContext.getAuthFilters());
        assertEquals(0, securityContext.getAuthFilters().size()); // 没有配置任何认证方式
        assertNotNull(securityContext.getFacetFilter());
    }

    @Test
    public void testNullWhiteList() {
        // 测试空白名单
        AppSecurityContext securityContext = new AppSecurityContext.Builder()
            .inMemoryUser(testUsers)
            .build();

        assertNotNull(securityContext.getAuthFilters());
        assertEquals(1, securityContext.getAuthFilters().size());
        assertNotNull(securityContext.getFacetFilter());
    }

    @Test
    public void testCookieWithJwtToken() {
        // 测试Cookie认证使用JWT令牌
        AppSecurityContext securityContext = new AppSecurityContext.Builder()
            .jwtIssuer("test-issuer")
            .jwtSubject("test-subject")
            .jwtTtl(3600000)
            .jwtPublicKeyPath("classpath:key/public.key")
            .jwtPrivateKeyPath("classpath:key/private.key")
            .cookieName("jwt-cookie")
            .cookieValueType(CookieValueType.JWT)
            .whiteList(List.of("/public/**"))
            .build();

        assertNotNull(securityContext.getAuthFilters());
        assertEquals(2, securityContext.getAuthFilters().size()); // JWT认证 + Cookie JWT认证
    }
}
