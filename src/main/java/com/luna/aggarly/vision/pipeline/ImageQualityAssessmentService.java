package com.luna.aggarly.vision.pipeline;

import com.luna.aggarly.vision.entity.enums.ImageQualityGrade;
import com.luna.aggarly.vision.pipeline.records.QualityAssessmentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

@Slf4j
@Service
public class ImageQualityAssessmentService {

    public QualityAssessmentResult assess(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return new QualityAssessmentResult(
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    ImageQualityGrade.REJECTED,
                    true, false, false, false, false, false
            );
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                return new QualityAssessmentResult(
                        0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                        ImageQualityGrade.REJECTED,
                        true, false, false, false, false, false
                );
            }

            int width = image.getWidth();
            int height = image.getHeight();

            // 1. Sharpness via Laplacian Variance on Luminance
            double sharpnessScore = computeLaplacianVariance(image);
            boolean isBlurry = sharpnessScore < 60.0;

            // 2. Brightness & Exposure Analysis
            double meanBrightness = computeMeanBrightness(image);
            boolean isDark = meanBrightness < 40.0;
            boolean isOverexposed = meanBrightness > 225.0;

            // 3. Screenshot & UI Chrome Heuristics (Color Entropy)
            double colorEntropy = computeColorEntropy(image);
            boolean isScreenshot = colorEntropy < 2.5 && (width == 1080 || width == 1170 || width == 1284 || height == 2532);

            // 4. Collage Detection (Repeated uniform divider lines)
            boolean isCollage = detectCollageDividers(image);

            // 5. Calculate Three Quality Dimensions
            // Normalized sharpness [0.0 - 1.0] (capped at 500 variance)
            double normSharpness = Math.min(1.0, sharpnessScore / 400.0);
            
            // Exposure factor [0.0 - 1.0]
            double normExposure = 1.0 - (Math.abs(meanBrightness - 128.0) / 128.0);
            normExposure = Math.max(0.1, normExposure);

            // Resolution factor [0.0 - 1.0]
            double resolutionPixels = width * height;
            double normResolution = Math.min(1.0, resolutionPixels / (1024.0 * 768.0));

            // Dimension 1: Technical Quality (Sensor noise, sharpness, resolution, clean rendering)
            double technicalQualityScore = (normSharpness * 0.45) + (normExposure * 0.35) + (normResolution * 0.20);

            // Dimension 2: Visual Usability (Is property content recognizable, even if moody/dark?)
            double visualUsabilityScore = (normSharpness * 0.60) + (isScreenshot ? 0.30 : 0.80) * 0.40;
            if (isDark) {
                // Night shot or moody lighting is still usable for visual impression
                visualUsabilityScore = Math.max(0.50, visualUsabilityScore);
            }

            // Dimension 3: Searchability Score (How informative is this image for listing search?)
            double searchabilityScore = (technicalQualityScore * 0.60) + (visualUsabilityScore * 0.40);
            if (isCollage || isScreenshot) {
                searchabilityScore *= 0.70;
            }

            // Composite Quality Score
            double qualityScore = (technicalQualityScore * 0.40) + (visualUsabilityScore * 0.40) + (searchabilityScore * 0.20);
            qualityScore = Math.min(1.0, Math.max(0.0, qualityScore));

            // Assign Quality Grade
            ImageQualityGrade grade;
            if (qualityScore >= 0.80) {
                grade = ImageQualityGrade.EXCELLENT;
            } else if (qualityScore >= 0.65) {
                grade = ImageQualityGrade.GOOD;
            } else if (qualityScore >= 0.45) {
                grade = ImageQualityGrade.ACCEPTABLE;
            } else if (qualityScore >= 0.25) {
                grade = ImageQualityGrade.POOR;
            } else {
                grade = ImageQualityGrade.REJECTED;
            }

            boolean passedQualityGate = (grade != ImageQualityGrade.REJECTED);

            return new QualityAssessmentResult(
                    qualityScore,
                    technicalQualityScore,
                    visualUsabilityScore,
                    searchabilityScore,
                    sharpnessScore,
                    meanBrightness,
                    grade,
                    isBlurry,
                    isDark,
                    isOverexposed,
                    isScreenshot,
                    isCollage,
                    passedQualityGate
            );
        } catch (Exception e) {
            log.warn("Quality assessment computation error: {}", e.getMessage());
            return new QualityAssessmentResult(
                    0.50, 0.50, 0.50, 0.50, 100.0, 128.0,
                    ImageQualityGrade.ACCEPTABLE,
                    false, false, false, false, false, true
            );
        }
    }

    private double computeLaplacianVariance(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width < 3 || height < 3) return 0.0;

        int step = Math.max(1, width / 200); // Subsample for fast processing
        double sum = 0.0;
        double sumSq = 0.0;
        int count = 0;

        for (int y = step; y < height - step; y += step) {
            for (int x = step; x < width - step; x += step) {
                int center = getLuminance(image.getRGB(x, y));
                int left = getLuminance(image.getRGB(x - step, y));
                int right = getLuminance(image.getRGB(x + step, y));
                int top = getLuminance(image.getRGB(x, y - step));
                int bottom = getLuminance(image.getRGB(x, y + step));

                int laplacian = 4 * center - left - right - top - bottom;
                sum += laplacian;
                sumSq += (double) laplacian * laplacian;
                count++;
            }
        }

        if (count == 0) return 0.0;
        double mean = sum / count;
        return (sumSq / count) - (mean * mean);
    }

    private double computeMeanBrightness(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int step = Math.max(1, width / 100);
        long totalLuminance = 0;
        int count = 0;

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                totalLuminance += getLuminance(image.getRGB(x, y));
                count++;
            }
        }
        return count == 0 ? 128.0 : (double) totalLuminance / count;
    }

    private double computeColorEntropy(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int step = Math.max(1, width / 80);
        int[] histogram = new int[64]; // 4x4x4 RGB bins
        int total = 0;

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int rgb = image.getRGB(x, y);
                int r = ((rgb >> 16) & 0xFF) / 64;
                int g = ((rgb >> 8) & 0xFF) / 64;
                int b = (rgb & 0xFF) / 64;
                int bin = (r << 4) | (g << 2) | b;
                histogram[bin]++;
                total++;
            }
        }

        double entropy = 0.0;
        for (int count : histogram) {
            if (count > 0) {
                double p = (double) count / total;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }
        return entropy;
    }

    private boolean detectCollageDividers(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (height < 200 || width < 200) return false;

        int dividerCount = 0;
        // Check horizontal lines
        for (int y = height / 5; y < (4 * height) / 5; y += 10) {
            boolean isWhiteLine = true;
            for (int x = 0; x < width; x += 20) {
                if (getLuminance(image.getRGB(x, y)) < 240) {
                    isWhiteLine = false;
                    break;
                }
            }
            if (isWhiteLine) {
                dividerCount++;
                if (dividerCount >= 2) return true;
            }
        }
        return false;
    }

    private int getLuminance(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }
}
