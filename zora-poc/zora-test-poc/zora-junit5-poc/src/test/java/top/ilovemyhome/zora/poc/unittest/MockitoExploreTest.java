package top.ilovemyhome.zora.poc.unittest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exploration of Mockito mocking and verification features.
 */
@ExtendWith(MockitoExtension.class)
class MockitoExploreTest {

    private static final Logger LOG = LoggerFactory.getLogger(MockitoExploreTest.class);

    interface UserService {
        String getUserName(int userId);
        boolean isActive(int userId);
    }

    @Mock
    private UserService userService;

    @BeforeEach
    void setUp() {
        LOG.info("Setting up mocks");
    }

    @Test
    @DisplayName("Basic mock and stub")
    void basicMockAndStub() {
        when(userService.getUserName(1)).thenReturn("Alice");
        when(userService.getUserName(2)).thenReturn("Bob");

        assertThat(userService.getUserName(1)).isEqualTo("Alice");
        assertThat(userService.getUserName(2)).isEqualTo("Bob");
    }

    @Test
    @DisplayName("Argument matchers")
    void argumentMatchers() {
        when(userService.getUserName(anyInt())).thenReturn("Unknown");
        when(userService.getUserName(eq(42))).thenReturn("The Answer");

        assertThat(userService.getUserName(99)).isEqualTo("Unknown");
        assertThat(userService.getUserName(42)).isEqualTo("The Answer");
    }

    @Test
    @DisplayName("Verify interactions")
    void verifyInteractions() {
        userService.getUserName(1);
        userService.getUserName(1);
        userService.isActive(1);

        verify(userService, times(2)).getUserName(1);
        verify(userService, atLeastOnce()).isActive(anyInt());
        verify(userService, never()).getUserName(999);
    }

    @Test
    @DisplayName("Spy on real object")
    void spyDemo() {
        List<String> realList = new java.util.ArrayList<>();
        realList.add("one");

        List<String> spyList = spy(realList);

        assertThat(spyList.size()).isEqualTo(1);

        when(spyList.size()).thenReturn(100);
        assertThat(spyList.size()).isEqualTo(100);

        spyList.add("two");
        verify(spyList).add("two");
    }

    @Test
    @DisplayName("Mock with @InjectMocks")
    void injectMocksDemo() {
        // Demonstrate that @Mock annotation is properly initialized by the extension
        assertThat(userService).isNotNull();
        when(userService.isActive(anyInt())).thenReturn(true);
        assertThat(userService.isActive(1)).isTrue();
    }
}
