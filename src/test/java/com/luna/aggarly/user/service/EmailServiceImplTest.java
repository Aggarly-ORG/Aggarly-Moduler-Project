package com.luna.aggarly.user.service;

import com.luna.aggarly.user.service.impl.EmailServiceImpl;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private SpringTemplateEngine templateEngine;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "from", "noreply@aggarly.com");
    }

    @Test
    @DisplayName("Should create and send MimeMessage successfully")
    void shouldSendMimeMessageSuccessfully() throws Exception {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.send("user@example.com", "Test Subject", "<h1>Test HTML</h1>");

        verify(javaMailSender, times(1)).send(mimeMessage);
    }

    @Test
    @DisplayName("Should process Thymeleaf OTP template")
    void shouldProcessOtpTemplate() {
        when(templateEngine.process(eq("verify-email"), any(Context.class)))
                .thenReturn("<html>123456</html>");

        String resultHtml = emailService.otpTemplate("123456");

        assertThat(resultHtml).isEqualTo("<html>123456</html>");
        verify(templateEngine, times(1)).process(eq("verify-email"), any(Context.class));
    }
}
