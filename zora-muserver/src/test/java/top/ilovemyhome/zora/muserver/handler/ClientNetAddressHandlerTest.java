package top.ilovemyhome.zora.muserver.handler;

import io.muserver.MuRequest;
import io.muserver.MuResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import top.ilovemyhome.zora.muserver.security.core.CIDRValidator;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClientNetAddressHandlerTest {

    @Mock
    private MuRequest mockRequest;

    @Mock
    private MuResponse mockResponse;

    @Mock
    private Predicate<MuRequest> mockPredicate;

    @Mock
    private Function<MuRequest, String> mockAddressGetter;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testConstructor() {
        // Test constructor with valid parameters
        List<CIDRValidator> validators = List.of(new CIDRValidator("192.168.1.0/24"));
        Function<MuRequest, String> addressGetter = req -> "192.168.1.1";
        Predicate<MuRequest> predicate = req -> true;
        ClientNetAddressHandler handler = new ClientNetAddressHandler(validators, addressGetter, predicate);
        assertNotNull(handler);
    }

    @Test
    public void testHandleWhenPredicateReturnsFalse() {
        // Test handle method when predicate returns false
        List<CIDRValidator> validators = List.of(new CIDRValidator("192.168.1.0/24"));
        Function<MuRequest, String> addressGetter = req -> "192.168.1.1";
        Predicate<MuRequest> predicate = req -> false; // Always false
        ClientNetAddressHandler handler = new ClientNetAddressHandler(validators, addressGetter, predicate);
        boolean result = handler.handle(mockRequest, mockResponse);
        assertFalse(result); // Should return true when not handled
        verify(mockResponse, never()).status(anyInt());
        verify(mockResponse, never()).write(anyString());
    }

    @Test
    public void testHandleWhenClientAddressIsAllowed() {
        // Test handle method when client address is allowed
        List<CIDRValidator> validators = List.of(new CIDRValidator("192.168.1.0/24"));
        Function<MuRequest, String> addressGetter = req -> "192.168.1.100";
        Predicate<MuRequest> predicate = req -> true;

        when(mockRequest.clientIP()).thenReturn("192.168.1.100");

        ClientNetAddressHandler handler = new ClientNetAddressHandler(validators, addressGetter, predicate);

        boolean result = handler.handle(mockRequest, mockResponse);
        assertFalse(result); // Should return true when not blocked
        verify(mockResponse, never()).status(anyInt());
        verify(mockResponse, never()).write(anyString());
    }

    @Test
    public void testHandleWhenClientAddressIsNotAllowed() {
        // Test handle method when client address is not allowed
        List<CIDRValidator> validators = List.of(new CIDRValidator("192.168.1.0/24"));
        Function<MuRequest, String> addressGetter = req -> "10.0.0.1";
        Predicate<MuRequest> predicate = req -> true;

        when(mockRequest.clientIP()).thenReturn("10.0.0.1");

        ClientNetAddressHandler handler = new ClientNetAddressHandler(validators, addressGetter, predicate);

        boolean result = handler.handle(mockRequest, mockResponse);
        assertTrue(result); // Should return false when blocked
        verify(mockResponse, times(1)).status(403);
        verify(mockResponse, times(1)).write("Not allowed.");
    }

    @Test
    public void testHandleWithMultipleValidators() {
        // Test handle method with multiple CIDR validators
        List<CIDRValidator> validators = List.of(
            new CIDRValidator("192.168.1.0/24"),
            new CIDRValidator("10.0.0.0/8")
        );
        Function<MuRequest, String> addressGetter = req -> "10.0.0.1";
        Predicate<MuRequest> predicate = req -> true;

        when(mockRequest.clientIP()).thenReturn("10.0.0.1");

        ClientNetAddressHandler handler = new ClientNetAddressHandler(validators, addressGetter, predicate);

        boolean result = handler.handle(mockRequest, mockResponse);
        assertFalse(result); // Should be allowed by second validator
        verify(mockResponse, never()).status(anyInt());
        verify(mockResponse, never()).write(anyString());
    }

    @Test
    public void testHandleWithRemoteAddressGetter() {
        // Test handle method with remote address getter
        List<CIDRValidator> validators = List.of(new CIDRValidator("192.168.1.0/24"));
        Function<MuRequest, String> addressGetter = ClientNetAddressHandler.BY_REMOATE_ADDRESS;
        Predicate<MuRequest> predicate = req -> true;
        when(mockRequest.remoteAddress()).thenReturn("192.168.1.50");
        ClientNetAddressHandler handler = new ClientNetAddressHandler(validators, addressGetter, predicate);
        boolean result = handler.handle(mockRequest, mockResponse);
        assertThat(result).isFalse(); // Should be allowed
        verify(mockResponse, never()).status(anyInt());
        verify(mockResponse, never()).write(anyString());
    }

    @Test
    public void testHandleWithInvalidIpAddress() {
        // Test handle method with invalid IP address
        List<CIDRValidator> validators = List.of(new CIDRValidator("192.168.1.0/24"));
        Function<MuRequest, String> addressGetter = req -> "invalid-ip";
        Predicate<MuRequest> predicate = req -> true;
        ClientNetAddressHandler handler = new ClientNetAddressHandler(validators, addressGetter, predicate);
        boolean result = handler.handle(mockRequest, mockResponse);
        assertThat(result).isTrue();
        verify(mockResponse, times(1)).status(403);
        verify(mockResponse, times(1)).write("Not allowed.");
    }
}
