package top.ilovemyhome.zora.muserver.helper;

import io.muserver.MuRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import top.ilovemyhome.zora.muserver.SharedTestResources;

import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static top.ilovemyhome.zora.muserver.SharedTestResources.configureStandardMockRequest;

public class MuRequestHelperTest {

    private MuRequest mockRequest;
    @BeforeEach
    public void setUp() {
        mockRequest = Mockito.mock(MuRequest.class);
        configureStandardMockRequest(mockRequest
            , SharedTestResources.createStandardHeaders()
            , Map.of("id", "100", "name", "PeaNotes")
            , Map.of("sessionId", "abc123", "jwt", "somefoobar" ));
    }

    @Test
    public void testDumpHostInfo() {
        String hostInfo = MuRequestHelper.dumpHostInfo();
        System.out.println(hostInfo);
        assertTrue(hostInfo.contains("Hostname: "));
        assertTrue(hostInfo.contains("IP: "));
    }

    @Test
    public void testDumpRequestInfoWithCompleteRequest() {
        String result = MuRequestHelper.dumpRequestInfo(mockRequest);
        System.out.println(result);
        // Then: Verify the output contains expected information
        assertThat(result).isNotNull();
        assertThat(result).contains("Protocol: HTTP/1.1");
        assertThat(result).contains("Method: POST");
        assertThat(result).contains("URI: http://localhost:8080/api/test");
        assertThat(result).contains("ClientIp: 192.168.1.100");
        assertThat(result).contains("RemoteAddress: 192.168.1.100");
        assertThat(result).contains("ContextPath: /api");
    }

    @Test
    public void testDumpHeaders() {
        String result = MuRequestHelper.dumpHeaderInfo(mockRequest);
        System.out.println(result);
        // Then: 使用 AssertJ 验证所有 header 信息都正确包含在结果中
        assertThat(result).isNotNull()
            .contains("User-Agent: Mozilla/5.0")
            .contains("Accept: application/json")
            .contains("Content-Type: application/json")
            .contains("Accept-Encoding: gzip, deflate")
            .contains("Accept-Language: en-US,en;q=0.9")
            .contains("Cache-Control: no-cache")
            .contains("Connection: keep-alive");
    }

    @Test
    public void testDumpCookies() {
        String result = MuRequestHelper.dumpCookies(mockRequest);
        System.out.println(result);
        assertThat(result).isNotNull()
            .contains("sessionId: abc123");
        assertThat(result).isNotNull()
            .contains("jwt: somefoobar");
    }
}
