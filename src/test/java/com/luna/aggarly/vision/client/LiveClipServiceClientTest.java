package com.luna.aggarly.vision.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveClipServiceClientTest {

    @Test
    @DisplayName("Test live ClipServiceClient if Python service is running")
    void testLiveClipService() throws Exception {
        ClipServiceClient client = new ClipServiceClient(new ObjectMapper());
        if (!client.isAvailable()) {
            System.out.println("Python CLIP service is offline, skipping live test");
            return;
        }

        System.out.println("CLIP service is ONLINE on port 8000!");
        File sampleImg = new File("C:\\Users\\dell\\.gemini\\antigravity\\brain\\9ed0e240-f24f-4b33-a111-145ef536ab58\\.user_uploaded\\media_1787326978030.jpg");
        if (sampleImg.exists()) {
            byte[] bytes = Files.readAllBytes(sampleImg.toPath());
            Optional<float[]> vecOpt = client.embedImage(bytes);
            assertTrue(vecOpt.isPresent(), "Live CLIP service should return vector");
            assertEquals(512, vecOpt.get().length, "OpenCLIP vector should be 512 dimensions");
            System.out.println("Successfully received 512-dim neural vector from live CLIP service!");
        }
    }
}
