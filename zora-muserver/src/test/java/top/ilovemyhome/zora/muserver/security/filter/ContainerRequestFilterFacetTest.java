package top.ilovemyhome.zora.muserver.security.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

public class ContainerRequestFilterFacetTest {

    private static final String TEST_PATH = "/api/resource";
    private static final String WHITE_LIST_PATH = "/api/public/**";

    @Mock
    private ContainerRequestContext mockContext;

    @Mock
    private ContainerRequestFilter mockFilter1;

    @Mock
    private ContainerRequestFilter mockFilter2;

    @Mock
    private ContainerRequestFilter mockFilter3;

    @Mock
    private UriInfo mockUriInfo;

    private ContainerRequestFilterFacet filterFacet;

    @BeforeEach
    public void setUp() throws URISyntaxException {
        MockitoAnnotations.openMocks(this);

        // 默认模拟请求路径
        when(mockContext.getUriInfo()).thenReturn(mockUriInfo);
        when(mockUriInfo.getRequestUri()).thenReturn(new URI("http://example.com" + TEST_PATH));
    }

    @Test
    public void testFilter_NoAuthFilters_ShouldPass() {
        // 配置：没有认证过滤器
        List<ContainerRequestFilter> authFilters = new ArrayList<>();
        List<String> whiteList = new ArrayList<>();

        filterFacet = new ContainerRequestFilterFacet(whiteList, authFilters);

        // 执行过滤
        filterFacet.filter(mockContext);

        // 验证：不应该调用任何过滤器或abortWith
        verify(mockContext, never()).abortWith(any(Response.class));
        verifyNoInteractions(mockFilter1, mockFilter2, mockFilter3);
    }

    @Test
    public void testFilter_PathInWhiteList_ShouldPass() throws URISyntaxException {
        // 配置：有认证过滤器，但路径在白名单中
        List<ContainerRequestFilter> authFilters = List.of(mockFilter1, mockFilter2);
        List<String> whiteList = List.of(WHITE_LIST_PATH, "/api/login");

        // 模拟请求路径匹配白名单
        when(mockUriInfo.getRequestUri()).thenReturn(new URI("http://example.com/api/public/data"));

        filterFacet = new ContainerRequestFilterFacet(whiteList, authFilters);

        // 执行过滤
        filterFacet.filter(mockContext);

        // 验证：不应该调用任何过滤器或abortWith
        verify(mockContext, never()).abortWith(any(Response.class));
        verifyNoInteractions(mockFilter1, mockFilter2);
    }

    @Test
    public void testFilter_FirstFilterSucceeds_ShouldPass() throws IOException {
        // 配置：多个认证过滤器
        List<ContainerRequestFilter> authFilters = List.of(mockFilter1, mockFilter2);
        List<String> whiteList = new ArrayList<>();

        filterFacet = new ContainerRequestFilterFacet(whiteList, authFilters);

        // 执行过滤
        filterFacet.filter(mockContext);

        // 验证：只应该调用第一个过滤器，不应该调用abortWith
        verify(mockFilter1).filter(mockContext);
        verify(mockFilter2, never()).filter(mockContext);
        verify(mockContext, never()).abortWith(any(Response.class));
    }

    @Test
    public void testFilter_FirstFilterThrowsException_SecondFilterSucceeds_ShouldPass() throws IOException {
        // 配置：多个认证过滤器
        List<ContainerRequestFilter> authFilters = List.of(mockFilter1, mockFilter2);
        List<String> whiteList = new ArrayList<>();

        filterFacet = new ContainerRequestFilterFacet(whiteList, authFilters);

        // 模拟第一个过滤器抛出异常
        doThrow(new RuntimeException("Filter 1 failed")).when(mockFilter1).filter(mockContext);

        // 执行过滤
        filterFacet.filter(mockContext);

        // 验证：应该调用两个过滤器，不应该调用abortWith
        verify(mockFilter1).filter(mockContext);
        verify(mockFilter2).filter(mockContext);
        verify(mockContext, never()).abortWith(any(Response.class));
    }

    @Test
    public void testFilter_AllFiltersFail_ShouldAbortWith401() throws IOException {
        // 配置：多个认证过滤器
        List<ContainerRequestFilter> authFilters = List.of(mockFilter1, mockFilter2, mockFilter3);
        List<String> whiteList = new ArrayList<>();

        filterFacet = new ContainerRequestFilterFacet(whiteList, authFilters);

        // 模拟所有过滤器抛出异常
        doThrow(new RuntimeException("Filter 1 failed")).when(mockFilter1).filter(mockContext);
        doThrow(new RuntimeException("Filter 2 failed")).when(mockFilter2).filter(mockContext);
        doThrow(new RuntimeException("Filter 3 failed")).when(mockFilter3).filter(mockContext);

        // 创建一个Answer来捕获abortWith调用的Response
        Response[] capturedResponse = new Response[1];
        doAnswer(invocation -> {
            capturedResponse[0] = invocation.getArgument(0);
            return null;
        }).when(mockContext).abortWith(any(Response.class));

        // 执行过滤
        filterFacet.filter(mockContext);

        // 验证：应该调用所有过滤器，并调用abortWith返回401
        verify(mockFilter1).filter(mockContext);
        verify(mockFilter2).filter(mockContext);
        verify(mockFilter3).filter(mockContext);
        verify(mockContext).abortWith(any(Response.class));

        // 验证401响应的属性
        assertEquals(401, capturedResponse[0].getStatus());
        assertEquals(MediaType.TEXT_PLAIN_TYPE, capturedResponse[0].getMediaType());
    }

    @Test
    public void testFilter_SomeFiltersFail_SomeSucceed_ShouldPass() throws IOException {
        // 配置：多个认证过滤器
        List<ContainerRequestFilter> authFilters = List.of(mockFilter1, mockFilter2, mockFilter3);
        List<String> whiteList = new ArrayList<>();

        filterFacet = new ContainerRequestFilterFacet(whiteList, authFilters);

        // 模拟前两个过滤器失败，第三个成功
        doThrow(new RuntimeException("Filter 1 failed")).when(mockFilter1).filter(mockContext);
        doThrow(new RuntimeException("Filter 2 failed")).when(mockFilter2).filter(mockContext);

        // 执行过滤
        filterFacet.filter(mockContext);

        // 验证：应该调用所有三个过滤器，不应该调用abortWith
        verify(mockFilter1).filter(mockContext);
        verify(mockFilter2).filter(mockContext);
        verify(mockFilter3).filter(mockContext);
        verify(mockContext, never()).abortWith(any(Response.class));
    }

    @Test
    public void testCheckWhiteList_PathMatchesPattern_ShouldReturnTrue() {
        // 创建过滤器实例以测试checkWhiteList方法
        List<ContainerRequestFilter> authFilters = new ArrayList<>();
        List<String> whiteList = List.of(
            "/api/public/**",
            "/api/login",
            "/api/resources/*/details"
        );

        filterFacet = new ContainerRequestFilterFacet(whiteList, authFilters);

        // 由于checkWhiteList是private方法，我们通过反射测试它
        java.lang.reflect.Method method;
        try {
            method = ContainerRequestFilterFacet.class.getDeclaredMethod("checkWhiteList", String.class);
            method.setAccessible(true);
            // 测试匹配的路径
            boolean result1 = (boolean) method.invoke(filterFacet, "/api/public/data");
            boolean result2 = (boolean) method.invoke(filterFacet, "/api/login");
            boolean result3 = (boolean) method.invoke(filterFacet, "/api/resources/123/details");

            // 验证结果
            assertEquals(true, result1);
            assertEquals(true, result2);
            assertEquals(true, result3);

        } catch (Exception e) {
            assertFalse(true, "checkWhiteList method should not throw exception");
        }
    }

    @Test
    public void testCheckWhiteList_PathDoesNotMatchPattern_ShouldReturnFalse() {
        // 创建过滤器实例以测试checkWhiteList方法
        List<ContainerRequestFilter> authFilters = new ArrayList<>();
        List<String> whiteList = List.of(
            "/api/public/**",
            "/api/login"
        );

        filterFacet = new ContainerRequestFilterFacet(whiteList, authFilters);

        // 由于checkWhiteList是private方法，我们通过反射测试它
        java.lang.reflect.Method method;
        try {
            method = ContainerRequestFilterFacet.class.getDeclaredMethod("checkWhiteList", String.class);
            method.setAccessible(true);

            // 测试不匹配的路径
            boolean result1 = (boolean) method.invoke(filterFacet, "/api/protected/data");
            boolean result2 = (boolean) method.invoke(filterFacet, "/api/logout");

            // 验证结果
            assertEquals(false, result1);
            assertEquals(false, result2);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
