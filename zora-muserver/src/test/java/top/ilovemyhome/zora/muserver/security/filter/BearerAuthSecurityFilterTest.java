package top.ilovemyhome.zora.muserver.security.filter;

import com.google.common.net.HttpHeaders;
import io.muserver.rest.MuRuntimeDelegate;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import top.ilovemyhome.zora.muserver.SharedTestResources;
import top.ilovemyhome.zora.muserver.security.authenticator.JwtAuthenticator;
import top.ilovemyhome.zora.muserver.security.authorizer.SimpleRoleAuthorizer;
import top.ilovemyhome.zora.muserver.security.core.User;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class BearerAuthSecurityFilterTest {
    static {
        MuRuntimeDelegate.ensureSet();
    }

    @Mock
    private ContainerRequestContext requestContext;
    @Mock
    private UriInfo uriInfo;

    @Mock
    private SimpleRoleAuthorizer simpleRoleAuthorizer;

    @Mock
    private JwtAuthenticator jwtAuthenticator;

    private BearerAuthSecurityFilter bearerAuthSecurityFilter;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        doThrow(WebApplicationException.class).when(requestContext).abortWith(any(Response.class));
        when(uriInfo.getRequestUri()).thenReturn(URI.create("http://localhost/api/test"));
        when(requestContext.getUriInfo()).thenReturn(uriInfo);
        bearerAuthSecurityFilter = new BearerAuthSecurityFilter(jwtAuthenticator, simpleRoleAuthorizer);
    }

    @Test
    public void testAuthenticateWithEmptyBearerToken() {
        when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(null);
        assertThrows(WebApplicationException.class, () -> bearerAuthSecurityFilter.filter(requestContext));
        when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Basic foo");
        assertThrows(WebApplicationException.class, () -> bearerAuthSecurityFilter.filter(requestContext));

    }

    @Test
    public void testAuthenticateWithValidBearerToken() {
        User adminUser = SharedTestResources.createTestingUsers().get(0);
        String validToken = "valid-token";
        when(jwtAuthenticator.authenticate(validToken)).thenReturn(adminUser);
        when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + validToken);
        bearerAuthSecurityFilter.filter(requestContext);
        verify(requestContext, times(1)).setSecurityContext(
            argThat(securityContext -> securityContext.getUserPrincipal().equals(adminUser)));
    }

    @Test
    public void testAuthenticateWithInvalidBearerToken() {
        String inValidToken = "Invalid-token";
        when(jwtAuthenticator.authenticate(inValidToken)).thenReturn(null);
        when(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer " + inValidToken);
        bearerAuthSecurityFilter.filter(requestContext);
        verify(requestContext, times(1)).setSecurityContext(
            argThat(MuServerSecurityContext.notLoggedInHttpContext::equals));
    }


}
