package com.luna.aggarly.vision.pipeline;

public final class VisionPromptTemplates {

    private VisionPromptTemplates() {}

    public static final String PROMPT_VERSION = "2.3.0";

    public static final String DOMAIN_VALIDATION_PROMPT = """
            You are a strict domain gatekeeper and visual describer for a luxury vacation rental and real estate platform.
            Analyze this uploaded image and determine if it represents a valid real estate / property scene (e.g. bedroom, living room, bathroom, kitchen, dining, balcony, terrace, patio, swimming pool, building exterior, architectural facade, or natural landscape view).

            Return a strict JSON object:
            {
              "isPropertyDomain": true,
              "category": "REAL_ESTATE|FOOTWEAR|APPAREL|VEHICLE|ANIMAL|FOOD|ELECTRONICS|DOCUMENT|MEME|OTHER_OBJECT",
              "detectedSubject": "cliffside balcony terrace with turquoise loungers overlooking sea view",
              "sceneDescription": "A luxury whitewashed stone balcony terrace with turquoise wicker lounge chairs overlooking a panoramic sunset ocean view in Santorini.",
              "confidence": 0.99,
              "rejectionReason": null
            }

            STRICT DOMAIN RULES:
            1. If the photo displays shoes, sneakers, clothing, fashion accessories, animals, pets, cars, motorcycles, food plates, close-up selfies, screenshots, or random standalone consumer products, set 'isPropertyDomain' to FALSE and specify the detected category.
            2. If the photo displays an actual room, interior, balcony, pool, villa, building exterior, garden, or scenic view, set 'isPropertyDomain' to TRUE and write a vivid 1-sentence 'sceneDescription'.
            3. Respond with valid JSON ONLY.
            """;

    public static final String SCENE_AND_CAPTION_PROMPT = """
            You are an expert architectural and rental property visual classifier with OCR text extraction capabilities.
            Analyze this photo and return a strict JSON object with these exact keys:

            {
              "sceneType": "BALCONY|VIEW|LIVING_ROOM|BEDROOM|BATHROOM|KITCHEN|DINING|POOL|EXTERIOR|WORKSPACE|NON_PROPERTY|OTHER",
              "sceneConfidence": 0.98,
              "isIndoor": false,
              "isPropertyDomain": true,
              "viewType": "SEA_VIEW|CITY_SKYLINE|MOUNTAIN|GARDEN|POOL_VIEW|STREET|COURTYARD|NONE",
              "aiCaption": "A detailed 1-2 sentence description of the scene, furniture, materials, and visible outdoor views.",
              "altText": "A concise 5-10 word accessibility description.",
              "ocrText": "Verbatim transcription of any visible text, wall art, signs, labels, brand names, or book titles found in the photo (or null if none)",
              "dominantColors": ["#HEX1", "#HEX2", "#HEX3"],
              "moderationStatus": "APPROVED",
              "moderationReason": null
            }

            STRICT CLASSIFICATION & OCR RULES:
            1. BALCONY / TERRACE / PATIO: Outdoor or semi-open veranda, terrace, or balcony with glass railings, outdoor seating (wicker/rattan chairs, sofa cushions, coffee table) overlooking the sea, city, or sky. (NEVER classify a balcony or outdoor seating area as a BEDROOM).
            2. BEDROOM: Strictly reserved for indoor rooms featuring an actual sleeping bed (mattress, headboard, sleeping pillows).
            3. VIEW: Pure panoramic views or focal horizons (sea, sunset, ocean, mountain landscape).
            4. LIVING_ROOM: Enclosed indoor living room or indoor lounge with indoor sofas.
            5. isIndoor: MUST be false for balconies, terraces, patios, pools, and outdoor views.
            6. viewType: Set to SEA_VIEW if the ocean, sea, coast, or water horizon is visible.
            7. NON_PROPERTY / OUT-OF-DOMAIN: If the photo depicts shoes, sneakers, clothing, fashion products, vehicles, animals, memes, food, or screenshots rather than an actual property/architectural space, set 'sceneType' to 'NON_PROPERTY', 'isPropertyDomain' to false, 'moderationStatus' to 'REJECTED', and state the reason in 'moderationReason'.
            8. OCR TEXT EXTRACTION: Carefully read the image and transcribe ANY legible text visible anywhere in the photo (e.g. wall quotes, framed paintings with writing, WiFi signs, book titles, coffee table magazines, appliance logos, street signs, building names). If readable text exists, output the exact words in 'ocrText'. If no text is visible, set 'ocrText' to null.

            Respond with valid JSON ONLY. No markdown wrapper, no conversational text.
            """;

    public static final String DETAILED_ATTRIBUTES_PROMPT = """
            Analyze this property photo in detail and extract visible physical amenities, furniture objects, and aesthetic style characteristics.
            Return a strict JSON object:

            {
              "detectedObjects": [
                {"object": "wicker_armchair", "confidence": 0.95},
                {"object": "cushioned_bench", "confidence": 0.92},
                {"object": "glass_railing", "confidence": 0.96},
                {"object": "coffee_table", "confidence": 0.88}
              ],
              "detectedAmenities": ["balcony", "sea_view", "outdoor_seating", "panoramic_view"],
              "styleTags": [
                {"tag": "coastal", "confidence": 0.95},
                {"tag": "relaxing", "confidence": 0.92},
                {"tag": "bright", "confidence": 0.90},
                {"tag": "sunset_view", "confidence": 0.88}
              ],
              "conditionAssessment": {
                "cleanliness": 0.95,
                "maintenance": 0.95,
                "lightingQuality": "warm_sunset_ambient"
              },
              "specialFeatures": ["glass_balcony_railing", "unobstructed_sea_view", "terracotta_tiles"]
            }

            Rules:
            1. Only list amenities and objects that are visibly present in the image.
            2. Never invent features.
            3. Respond with valid JSON ONLY.
            """;
}
