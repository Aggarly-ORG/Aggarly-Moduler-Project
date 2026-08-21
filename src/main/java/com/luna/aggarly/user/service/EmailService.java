package com.luna.aggarly.user.service;

import jakarta.mail.MessagingException;

public interface EmailService {
    void send(String to,String subject,String html) throws MessagingException;

    String otpTemplate(String otp);
}
