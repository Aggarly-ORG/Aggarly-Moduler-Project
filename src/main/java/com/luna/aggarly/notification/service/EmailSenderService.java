package com.luna.aggarly.notification.service;

import java.util.Map;

public interface EmailSenderService {

    void sendHtmlEmail(String to, String subject, String templateName, Map<String, Object> templateModel);
}
