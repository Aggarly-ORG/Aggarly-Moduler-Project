package com.luna.aggarly.user.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class QrCodeGenerator {

    public static String generateBase64(String text) throws Exception {

        QRCodeWriter writer = new QRCodeWriter();

        BitMatrix matrix = writer.encode(
                text,
                BarcodeFormat.QR_CODE,
                250,
                250
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        MatrixToImageWriter.writeToStream(matrix, "PNG", out);

        return Base64.getEncoder()
                .encodeToString(out.toByteArray());
    }
}