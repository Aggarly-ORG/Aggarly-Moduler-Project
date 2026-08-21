package com.luna.aggarly.notification.service.impl;

import com.luna.aggarly.notification.service.EmailSenderService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSenderServiceImpl implements EmailSenderService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    private final SpringTemplateEngine emailTemplateEngine;

    @Value("${spring.mail.username:support@aggarly.com}")
    private String fromEmail;

    @Override
    @Async("notificationTaskExecutor")
    public void sendHtmlEmail(String to, String subject, String templateName, Map<String, Object> templateModel) {
        log.info("Preparing HTML email to {}, subject: {}, template: {}", to, subject, templateName);
        try {
            Context context = new Context();
            if (templateModel != null) {
                context.setVariables(templateModel);
            }

            String htmlBody = emailTemplateEngine.process(templateName, context);

            if (mailSender != null) {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

                helper.setFrom(fromEmail);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);

                mailSender.send(message);
                log.info("HTML email successfully sent to {}", to);
            } else {
                log.warn("JavaMailSender not available. Simulated email sending to {}: Subject: {}", to, subject);
            }
        } catch (Exception e) {
            log.error("Failed to send HTML email to {}", to, e);
        }
    }
}
