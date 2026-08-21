package com.luna.aggarly.vision.pipeline;

import com.luna.aggarly.filestorage.service.FileStorageService;
import com.luna.aggarly.vision.exception.ImagePreprocessingException;
import com.luna.aggarly.vision.exception.VisionRetryPolicy;
import com.luna.aggarly.vision.pipeline.records.PreprocessedImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imgscalr.Scalr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImagePreprocessor {

    private final FileStorageService fileStorageService;

    @Value("${aggarly.vision.preprocessing.max-width-px:1024}")
    private int maxWidthPx = 1024;

    @Value("${aggarly.vision.preprocessing.max-height-px:1024}")
    private int maxHeightPx = 1024;

    private static final List<String> ALLOWED_EXTENSIONS = List.of("jpg", "jpeg", "png", "webp");

    public PreprocessedImage preprocess(UUID imageId, UUID propertyId, String objectKey) {
        log.debug("Preprocessing image id={}, propertyId={}, objectKey={}", imageId, propertyId, objectKey);
        try (InputStream inputStream = fileStorageService.getFileStream(objectKey)) {
            if (inputStream == null) {
                throw new ImagePreprocessingException(
                        "File stream not found for objectKey: " + objectKey,
                        VisionRetryPolicy.RETRYABLE
                );
            }
            byte[] rawBytes = inputStream.readAllBytes();
            return preprocessBytes(imageId, propertyId, rawBytes, objectKey);
        } catch (ImagePreprocessingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch/preprocess image from storage for key: {}", objectKey, e);
            throw new ImagePreprocessingException("Error reading image from storage: " + e.getMessage(), e, VisionRetryPolicy.RETRYABLE);
        }
    }

    public PreprocessedImage preprocessBytes(UUID imageId, UUID propertyId, byte[] rawBytes, String objectKey) {
        if (rawBytes == null || rawBytes.length == 0) {
            throw new ImagePreprocessingException("Image byte array is empty", VisionRetryPolicy.NON_RETRYABLE);
        }

        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(rawBytes));
            if (original == null) {
                throw new ImagePreprocessingException("Failed to decode image bytes: unsupported or corrupted format", VisionRetryPolicy.NON_RETRYABLE);
            }

            int originalWidth = original.getWidth();
            int originalHeight = original.getHeight();

            if (originalWidth < 100 || originalHeight < 100) {
                throw new ImagePreprocessingException("Image resolution is excessively small: " + originalWidth + "x" + originalHeight, VisionRetryPolicy.NON_RETRYABLE);
            }

            // Resize if exceeds bounds
            BufferedImage resized = original;
            if (originalWidth > maxWidthPx || originalHeight > maxHeightPx) {
                resized = Scalr.resize(original, Scalr.Method.QUALITY, Scalr.Mode.AUTOMATIC, maxWidthPx, maxHeightPx);
            }

            // Strip alpha channel & normalize to RGB
            BufferedImage rgbImage = new BufferedImage(resized.getWidth(), resized.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgbImage.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, resized.getWidth(), resized.getHeight());
            g.drawImage(resized, 0, 0, null);
            g.dispose();

            // Encode as clean JPEG
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            boolean written = ImageIO.write(rgbImage, "jpg", outputStream);
            if (!written) {
                throw new ImagePreprocessingException("Failed to encode processed image to JPEG", VisionRetryPolicy.NON_RETRYABLE);
            }

            byte[] preprocessedBytes = outputStream.toByteArray();

            return new PreprocessedImage(
                    imageId,
                    propertyId,
                    preprocessedBytes,
                    rgbImage.getWidth(),
                    rgbImage.getHeight(),
                    "JPEG",
                    objectKey
            );
        } catch (ImagePreprocessingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Image preprocessing error: {}", e.getMessage(), e);
            throw new ImagePreprocessingException("Image preprocessing failure: " + e.getMessage(), e, VisionRetryPolicy.NON_RETRYABLE);
        }
    }
}
