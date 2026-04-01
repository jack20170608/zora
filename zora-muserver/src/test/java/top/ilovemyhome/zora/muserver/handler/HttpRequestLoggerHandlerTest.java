package top.ilovemyhome.zora.muserver.handler;

import io.muserver.MuRequest;
import io.muserver.MuResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;
import top.ilovemyhome.zora.muserver.SharedTestResources;

import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class HttpRequestLoggerHandlerTest {

    @Mock
    private MuRequest mockRequest;

    @Mock
    private MuResponse mockResponse;

    private HttpRequestLoggerHandler handler;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        SharedTestResources.configureStandardMockRequest(mockRequest
            , SharedTestResources.createStandardHeaders()
            , Map.of("id", "100", "name", "PeaNotes")
            , Map.of("session-id", "token-1234" ));
        handler = new HttpRequestLoggerHandler(true);
    }

    @Test
    public void testConstructor() {
        assertThat(handler).isNotNull();
    }

    @Test
    public void testHandleWithValidRequest() throws Exception {
        boolean result = handler.handle(mockRequest, mockResponse);
        assertThat(result).isFalse();
    }


    @Test
    public void testHandleWithNullRequest() throws Exception {
        // When & Then: Should handle gracefully or throw appropriate exception
        assertThrows(Exception.class, () -> {
            handler.handle(null, mockResponse);
        });
    }

    @Test
    public void testLoggerOutput() {
        assertNotNull(LoggerFactory.getLogger(HttpRequestLoggerHandler.class));
    }
}
