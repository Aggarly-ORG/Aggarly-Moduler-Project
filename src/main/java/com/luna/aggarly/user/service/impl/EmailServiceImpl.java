package com.luna.aggarly.user.service.impl;

import com.luna.aggarly.user.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender javaMailSender;

    @Value("${app.mail.from}")
    private String from;
    private final SpringTemplateEngine templateEngine;

    @Override
    public void send(String to, String subject, String html) throws MessagingException {
        MimeMessage message= javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message,true,"UTF-8");
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html,true);
        javaMailSender.send(message);
    }


    @Override
    public String otpTemplate(String otp) {

        Context context = new Context();

        context.setVariable("otp", otp);
        context.setVariable("expiration", "10 minutes");

        return templateEngine.process(
                "verify-email",
                context
        );
    }
}
