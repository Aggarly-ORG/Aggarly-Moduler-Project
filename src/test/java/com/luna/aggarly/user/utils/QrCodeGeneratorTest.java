package com.luna.aggarly.user.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QrCodeGeneratorTest {

    @Test
    @DisplayName("Should generate non-empty Base64 string for text input")
    void shouldGenerateBase64StringForTextInput() throws Exception {
        String input = "otpauth://totp/Aggarly:test@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Aggarly";
        String base64Qr = QrCodeGenerator.generateBase64(input);

        assertThat(base64Qr).isNotNull().isNotBlank();
    }
}
