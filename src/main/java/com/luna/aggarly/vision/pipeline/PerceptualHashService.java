package com.luna.aggarly.vision.pipeline;

import com.luna.aggarly.vision.entity.PropertyImageAiMetadata;
import com.luna.aggarly.vision.entity.enums.DuplicateClassification;
import com.luna.aggarly.vision.pipeline.records.PhashResult;
import com.luna.aggarly.vision.repository.PropertyImageAiMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imgscalr.Scalr;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerceptualHashService {

    private final PropertyImageAiMetadataRepository metadataRepository;

    private static final int EXACT_DUPLICATE_HAMMING_THRESHOLD = 0;
    private static final int NEAR_DUPLICATE_HAMMING_THRESHOLD = 8;

    public String computeDHash(byte[] imageBytes) {
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) {
                return "";
            }

            // 1. Resize to 9x8 grayscale
            BufferedImage small = Scalr.resize(original, Scalr.Method.SPEED, Scalr.Mode.FIT_EXACT, 9, 8);
            BufferedImage gray = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = gray.createGraphics();
            g.drawImage(small, 0, 0, null);
            g.dispose();

            // 2. Compute difference between adjacent pixels in each row (8 comparisons * 8 rows = 64 bits)
            long hash = 0L;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int leftPixel = gray.getRaster().getSample(x, y, 0);
                    int rightPixel = gray.getRaster().getSample(x + 1, y, 0);
                    if (leftPixel > rightPixel) {
                        hash |= (1L << (y * 8 + x));
                    }
                }
            }

            return String.format("%016x", hash);
        } catch (Exception e) {
            log.warn("Failed to compute perceptual hash: {}", e.getMessage());
            return "";
        }
    }

    public PhashResult checkDuplicate(UUID propertyId, UUID currentImageId, String currentHash) {
        if (currentHash == null || currentHash.isBlank()) {
            return new PhashResult(currentHash, DuplicateClassification.UNIQUE, null);
        }

        // Fetch existing metadata for this property to find duplicate candidates
        List<PropertyImageAiMetadata> existingList = metadataRepository.findByPropertyId(propertyId);
        long currentHashVal = parseHexToLong(currentHash);

        UUID exactDuplicateId = null;
        UUID nearDuplicateId = null;
        int lowestDistance = Integer.MAX_VALUE;

        for (PropertyImageAiMetadata meta : existingList) {
            if (meta.getPropertyImage() != null && meta.getPropertyImage().getId().equals(currentImageId)) {
                continue;
            }

            String otherHash = meta.getPerceptualHash();
            if (otherHash == null || otherHash.isBlank()) {
                continue;
            }

            long otherHashVal = parseHexToLong(otherHash);
            int distance = Long.bitCount(currentHashVal ^ otherHashVal);

            if (distance <= EXACT_DUPLICATE_HAMMING_THRESHOLD) {
                exactDuplicateId = meta.getPropertyImage().getId();
                lowestDistance = 0;
                break;
            } else if (distance <= NEAR_DUPLICATE_HAMMING_THRESHOLD && distance < lowestDistance) {
                lowestDistance = distance;
                nearDuplicateId = meta.getPropertyImage().getId();
            }
        }

        if (exactDuplicateId != null) {
            log.info("Found EXACT duplicate for image {} -> existing image {}", currentImageId, exactDuplicateId);
            return new PhashResult(currentHash, DuplicateClassification.EXACT_DUPLICATE, exactDuplicateId);
        }

        if (nearDuplicateId != null) {
            log.info("Found NEAR duplicate (distance={}) for image {} -> existing image {}", lowestDistance, currentImageId, nearDuplicateId);
            return new PhashResult(currentHash, DuplicateClassification.NEAR_DUPLICATE, nearDuplicateId);
        }

        return new PhashResult(currentHash, DuplicateClassification.UNIQUE, null);
    }

    public int computeHammingDistance(String hash1, String hash2) {
        if (hash1 == null || hash2 == null || hash1.length() != 16 || hash2.length() != 16) {
            return 64;
        }
        long h1 = parseHexToLong(hash1);
        long h2 = parseHexToLong(hash2);
        return Long.bitCount(h1 ^ h2);
    }

    private long parseHexToLong(String hex) {
        try {
            return new BigInteger(hex, 16).longValue();
        } catch (Exception e) {
            return 0L;
        }
    }
}
