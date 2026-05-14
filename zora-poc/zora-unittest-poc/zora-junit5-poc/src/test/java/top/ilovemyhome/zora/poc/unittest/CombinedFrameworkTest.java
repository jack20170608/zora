package top.ilovemyhome.zora.poc.unittest;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Combined scenario: demonstrates JUnit 5 + AssertJ + Mockito working together
 * in a realistic user registration service test.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("User Registration Service")
class CombinedFrameworkTest {

    private static final Logger LOG = LoggerFactory.getLogger(CombinedFrameworkTest.class);

    interface UserRepository {
        boolean existsByUsername(String username);
        void save(User user);
    }

    interface EmailService {
        void sendWelcomeEmail(String email, String username);
    }

    record User(String username, String email, String password) {
    }

    static class UserRegistrationService {
        private static final int MIN_PASSWORD_LENGTH = 6;
        private final UserRepository userRepository;
        private final EmailService emailService;

        UserRegistrationService(UserRepository userRepository, EmailService emailService) {
            this.userRepository = userRepository;
            this.emailService = emailService;
        }

        void register(String username, String email, String password) {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("Username must not be empty");
            }
            if (email == null || !email.contains("@")) {
                throw new IllegalArgumentException("Email must be valid");
            }
            if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
                throw new IllegalArgumentException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
            }
            if (userRepository.existsByUsername(username)) {
                throw new IllegalStateException("Username already taken: " + username);
            }
            userRepository.save(new User(username, email, password));
            emailService.sendWelcomeEmail(email, username);
        }
    }

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        LOG.info("Initializing test context for user registration");
    }

    @Nested
    @DisplayName("Successful registration")
    class SuccessfulRegistration {

        @Test
        @DisplayName("should save user and send welcome email")
        void shouldSaveUserAndSendEmail() {
            when(userRepository.existsByUsername("alice")).thenReturn(false);

            registrationService.register("alice", "alice@example.com", "secret123");

            verify(userRepository).save(argThat(user ->
                user.username().equals("alice") &&
                user.email().equals("alice@example.com")
            ));
            verify(emailService).sendWelcomeEmail("alice@example.com", "alice");
            verifyNoMoreInteractions(userRepository, emailService);
        }
    }

    @Nested
    @DisplayName("Validation failures")
    class ValidationFailures {

        @ParameterizedTest(name = "invalid username: ''{0}''")
        @CsvSource({
            ", Username must not be empty",
            "'  ', Username must not be empty"
        })
        @DisplayName("should reject invalid username")
        void shouldRejectInvalidUsername(String username, String expectedMessage) {
            assertThatThrownBy(() ->
                registrationService.register(username, "test@example.com", "password")
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);

            verifyNoInteractions(userRepository, emailService);
        }

        @ParameterizedTest(name = "invalid email: ''{0}''")
        @CsvSource({
            "invalid-email, Email must be valid",
            ", Email must be valid"
        })
        @DisplayName("should reject invalid email")
        void shouldRejectInvalidEmail(String email, String expectedMessage) {
            assertThatThrownBy(() ->
                registrationService.register("bob", email, "password")
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);

            verifyNoInteractions(userRepository, emailService);
        }

        @ParameterizedTest(name = "password: ''{0}'' (length {1})")
        @CsvSource({
            "short, 5",
            "abc, 3",
            ", 0"
        })
        @DisplayName("should reject short password")
        void shouldRejectShortPassword(String password, int length) {
            assertThatThrownBy(() ->
                registrationService.register("bob", "bob@example.com", password)
            )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password must be at least");

            verifyNoInteractions(userRepository, emailService);
        }
    }

    @Nested
    @DisplayName("Business rule failures")
    class BusinessRuleFailures {

        @Test
        @DisplayName("should reject duplicate username")
        void shouldRejectDuplicateUsername() {
            when(userRepository.existsByUsername("existing")).thenReturn(true);

            assertThatThrownBy(() ->
                registrationService.register("existing", "new@example.com", "password123")
            )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Username already taken");

            verify(userRepository).existsByUsername("existing");
            verifyNoMoreInteractions(userRepository);
            verifyNoInteractions(emailService);
        }
    }

    @Nested
    @DisplayName("Edge cases and interaction patterns")
    class EdgeCases {

        @Test
        @DisplayName("should handle repository exception gracefully")
        void shouldHandleRepositoryException() {
            when(userRepository.existsByUsername(anyString()))
                .thenThrow(new RuntimeException("DB connection lost"));

            assertThatThrownBy(() ->
                registrationService.register("crash", "crash@example.com", "password123")
            )
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB connection lost");

            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("verify exact call count and order")
        void verifyCallOrderAndCount() {
            when(userRepository.existsByUsername("order")).thenReturn(false);

            registrationService.register("order", "order@example.com", "password123");

            var inOrder = inOrder(userRepository, emailService);
            inOrder.verify(userRepository).existsByUsername("order");
            inOrder.verify(userRepository).save(any(User.class));
            inOrder.verify(emailService).sendWelcomeEmail("order@example.com", "order");
        }
    }
}
