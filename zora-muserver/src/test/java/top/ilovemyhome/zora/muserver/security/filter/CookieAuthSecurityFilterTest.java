package top.ilovemyhome.zora.muserver.security.filter;

import io.muserver.rest.Authorizer;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import top.ilovemyhome.zora.muserver.security.authenticator.TokenAuthenticator;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

public class CookieAuthSecurityFilterTest {

    private static final String COOKIE_NAME = "auth_token";
    private static final String VALID_TOKEN = "valid_token_123";
    private static final String INVALID_TOKEN = "invalid_token_456";

    @Mock
    private ContainerRequestContext mockContext;

    @Mock
    private TokenAuthenticator mockAuthenticator;

    @Mock
    private Authorizer mockAuthorizer;

    @Mock
    private UriInfo mockUriInfo;

    @Mock
    private Principal mockPrincipal;

    private CookieAuthSecurityFilter filter;

    @BeforeEach
    public void setUp() throws URISyntaxException {
        MockitoAnnotations.openMocks(this);

        // 初始化过滤器
        filter = new CookieAuthSecurityFilter(COOKIE_NAME, mockAuthenticator, mockAuthorizer);

        // 默认模拟为 HTTP 请求
        when(mockContext.getUriInfo()).thenReturn(mockUriInfo);
        when(mockUriInfo.getRequestUri()).thenReturn(new URI("http://example.com/api"));
    }

    @Test
    public void testFilter_NoCookie_Returns401() {
        // 模拟没有 Cookie
        when(mockContext.getCookies()).thenReturn(new HashMap<>());

        // 执行过滤
        filter.filter(mockContext);

        // 验证结果：应该调用 abortWith 返回 401
        verify(mockContext).abortWith(any(Response.class));
        verify(mockContext, never()).setSecurityContext(any());
    }

    @Test
    public void testFilter_InvalidToken_SetsNotLoggedInContext_HTTP() {
        // 模拟有 Cookie 但令牌无效
        Map<String, Cookie> cookies = new HashMap<>();
        cookies.put(COOKIE_NAME, new Cookie(COOKIE_NAME, INVALID_TOKEN));
        when(mockContext.getCookies()).thenReturn(cookies);

        // 模拟验证失败
        when(mockAuthenticator.authenticate(INVALID_TOKEN)).thenReturn(null);

        // 执行过滤
        filter.filter(mockContext);

        // 验证结果：不应该调用 abortWith，应该设置未登录的 SecurityContext
        verify(mockContext, never()).abortWith(any(Response.class));
        verify(mockContext).setSecurityContext(any());
    }

    @Test
    public void testFilter_ValidToken_SetsLoggedInContext_HTTP() {
        // 模拟有 Cookie 且令牌有效
        Map<String, Cookie> cookies = new HashMap<>();
        cookies.put(COOKIE_NAME, new Cookie(COOKIE_NAME, VALID_TOKEN));
        when(mockContext.getCookies()).thenReturn(cookies);

        // 模拟验证成功
        when(mockAuthenticator.authenticate(VALID_TOKEN)).thenReturn(mockPrincipal);

        // 执行过滤
        filter.filter(mockContext);

        // 验证结果：不应该调用 abortWith，应该设置已登录的 SecurityContext
        verify(mockContext, never()).abortWith(any(Response.class));
        verify(mockContext).setSecurityContext(any());
    }

    @Test
    public void testFilter_ValidToken_SetsLoggedInContext_HTTPS() throws URISyntaxException {
        // 模拟 HTTPS 请求
        when(mockUriInfo.getRequestUri()).thenReturn(new URI("https://example.com/api"));

        // 模拟有 Cookie 且令牌有效
        Map<String, Cookie> cookies = new HashMap<>();
        cookies.put(COOKIE_NAME, new Cookie(COOKIE_NAME, VALID_TOKEN));
        when(mockContext.getCookies()).thenReturn(cookies);

        // 模拟验证成功
        when(mockAuthenticator.authenticate(VALID_TOKEN)).thenReturn(mockPrincipal);

        // 执行过滤
        filter.filter(mockContext);

        // 验证结果：不应该调用 abortWith，应该设置已登录的 SecurityContext（HTTPS）
        verify(mockContext, never()).abortWith(any(Response.class));
        verify(mockContext).setSecurityContext(any());
    }

    @Test
    public void testFilter_CookieNameStartsWithMatch() {
        // 模拟 Cookie 名称以指定名称开头（验证 startsWith 逻辑）
        Map<String, Cookie> cookies = new HashMap<>();
        cookies.put(COOKIE_NAME + "_suffix", new Cookie(COOKIE_NAME + "_suffix", VALID_TOKEN));
        when(mockContext.getCookies()).thenReturn(cookies);

        // 模拟验证成功
        when(mockAuthenticator.authenticate(VALID_TOKEN)).thenReturn(mockPrincipal);

        // 执行过滤
        filter.filter(mockContext);

        // 验证结果：应该找到 Cookie 并设置 SecurityContext
        verify(mockContext, never()).abortWith(any(Response.class));
        verify(mockContext).setSecurityContext(any());
    }

    @Test
    public void testFilter_WrongCookieName_Returns401() {
        // 模拟有其他名称的 Cookie，但不是我们要找的
        Map<String, Cookie> cookies = new HashMap<>();
        cookies.put("wrong_cookie", new Cookie("wrong_cookie", VALID_TOKEN));
        when(mockContext.getCookies()).thenReturn(cookies);

        // 执行过滤
        filter.filter(mockContext);

        // 验证结果：应该调用 abortWith 返回 401
        verify(mockContext).abortWith(any(Response.class));
        verify(mockContext, never()).setSecurityContext(any());
    }

    @Test
    public void testAuthResponse_BuildsCorrectly() {
        // 直接验证 authResponse 的构建是否正确
        // 注意：由于 authResponse 是私有的，我们通过间接方式验证
        Map<String, Cookie> cookies = new HashMap<>();
        cookies.put("wrong_cookie", new Cookie("wrong_cookie", VALID_TOKEN));
        when(mockContext.getCookies()).thenReturn(cookies);

        // 创建一个 Answer 来捕获 abortWith 调用的 Response
        Response[] capturedResponse = new Response[1];
        doAnswer(invocation -> {
            capturedResponse[0] = invocation.getArgument(0);
            return null;
        }).when(mockContext).abortWith(any(Response.class));

        // 执行过滤
        filter.filter(mockContext);

        // 验证 authResponse 的属性
        assertNotNull(capturedResponse[0]);
        assertEquals(401, capturedResponse[0].getStatus());
        assertEquals(MediaType.TEXT_PLAIN_TYPE, capturedResponse[0].getMediaType());
    }
}
