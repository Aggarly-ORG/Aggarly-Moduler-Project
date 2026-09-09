package com.luna.aggarly.aiagent.engine;

import com.luna.aggarly.aiagent.engine.enums.IntentCategory;
import com.luna.aggarly.aiagent.engine.records.ClassifiedIntent;
import com.luna.aggarly.aiagent.engine.records.ConversationContext;
import com.luna.aggarly.aiagent.engine.records.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntentClassifier {

    private final LlmClient llmClient;

    public ClassifiedIntent classify(String userMessage, ConversationContext context) {
        log.info("Classifying intent for message: {}", userMessage);

        // Short-circuit: Browser Co-Pilot messages always contain [PAGE_CONTEXT].
        if (userMessage != null && userMessage.contains("[PAGE_CONTEXT]")) {
            if (userMessage.contains("PATH: /admin") || userMessage.contains("PATH: /manage-admin")) {
                log.info("IntentClassifier: [PAGE_CONTEXT] detected on admin route — routing to ADMIN_MANAGEMENT");
                return new ClassifiedIntent(IntentCategory.ADMIN_MANAGEMENT, userMessage, context);
            }
            log.info("IntentClassifier: [PAGE_CONTEXT] detected — routing to HOST_MANAGEMENT (browser co-pilot turn)");
            return new ClassifiedIntent(IntentCategory.HOST_MANAGEMENT, userMessage, context);
        }

        List<ChatMessage> messages = new ArrayList<>();
        String memoryBlock = context != null ? context.formatUserMemoriesBlock() : "";

        messages.add(
                ChatMessage.system("""
              You are the master Intent Classification Engine for the Aggarly property rental and concierge platform.
              Your sole responsibility is to analyze the incoming user message (and conversation context) and classify it into EXACTLY ONE of the intent codes below.
""" + memoryBlock + """

              ================================================================================
              INTENT CODES & DOMAIN RESPONSIBILITIES
              ================================================================================

              1. PROPERTY_SEARCH
                 Domain: Property Discovery, Details, Comparisons & Preferences
                 Agent: PropertyAgent
                 Scope & Triggers:
                 - Searching or filtering listings (location, price, dates, bedrooms, amenities).
                 - Inquiring about specific property details, house rules, cancellation terms, host bio, or location maps.
                 - Checking live property availability for dates.
                 - Comparing two or more properties side-by-side.
                 - Accessing user favorites, saved wishlists, or stored property preferences.
                 - Viewing past guest rating summaries, sub-ratings (cleanliness, accuracy), or review sentiment.
                 Examples:
                 - "Find me a pet-friendly 2-bedroom apartment in Rome under $150/night."
                 - "Does listing #982 have high-speed WiFi and a dedicated workspace?"
                 - "Compare Villa Azure and Sunset Cottage."
                 - "Show my saved wishlist properties."
                 - "What do past guests say about cleanliness for this studio?"

              2. BOOKING_ACTION
                 Domain: Reservations, Quotes, Check-in, Payments, Reviews & Documents
                 Agent: BookingAgent
                 Scope & Triggers:
                 - Creating a new booking or reservation request.
                 - Checking current booking status, reservation details, or booking confirmation.
                 - Modifying booking dates or number of guests.
                 - Getting cancellation refund quotes or canceling an existing booking.
                 - Retrieving check-in instructions, key codes, lockbox info, or arrival directions.
                 - Inquiring about price breakdowns, cleaning fees, taxes, or total price calculations.
                 - Checking payment processing status, card transactions, or refund status.
                 - Viewing personal past trip booking history.
                 - Submitting a guest review or asking the AI to draft a review for a completed stay.
                 - Setting price-drop alerts or availability notifications for a property.
                 - Uploading or analyzing rental contracts, lease agreements, or receipts.
                 Examples:
                 - "Book property ID 848a31 from Nov 10 to Nov 15 for 2 guests."
                 - "How much will I get refunded if I cancel reservation BK-9021 today?"
                 - "What is my door code and WiFi password for tomorrow's check-in?"
                 - "Why was there an extra $30 service fee on my booking?"
                 - "Track price drops for this apartment and alert me if it goes below $120."
                 - "Leave a 5-star review saying the apartment was spotless and centrally located."

              3. HOST_MANAGEMENT
                 Domain: Host Operations, Revenue, Calendar, Pricing & Guest Messaging
                 Agent: HostAgent
                 Scope & Triggers:
                 - Viewing host-owned property listings, status, and performance.
                 - Checking host earnings, monthly revenue, payouts, and financial breakdowns.
                 - Analyzing occupancy rates and booking trends across host properties.
                 - Getting AI price recommendations or optimal night rates for listings.
                 - Creating dynamic pricing rules (e.g. weekend markups, seasonal surge, last-minute discounts).
                 - Blocking or unblocking dates on property availability calendars.
                 - Optimizing listing titles, descriptions, and SEO tags to boost bookings.
                 - Drafting host replies to guest reviews or feedback.
                 - Assisting host-guest chat communication: summarizing message threads, generating suggested replies, checking message grammar/tone, or translating foreign guest messages.
                 Examples:
                 - "How much payout did I generate across all my listings in July?"
                 - "Block my beachfront villa calendar from Dec 24 to Jan 2."
                 - "Set a 15% price increase rule for all weekends in August."
                 - "What price per night should I set for my studio to reach 85% occupancy?"
                 - "Draft a polite response to a guest complaining about street noise."
                 - "Summarize the last 10 messages from guest Michael."

              4. TRAVEL_PLANNING
                 Domain: Destination Guide, Weather, Attractions, Dining & Events
                 Agent: TravelAgent
                 Scope & Triggers:
                 - Looking up destination weather forecasts, temperatures, and seasonal climate.
                 - Finding tourist attractions, landmarks, museums, parks, and hidden gems.
                 - Searching local restaurant recommendations, dining spots, and private chef availability.
                 - Checking local cultural events, festivals, concerts, and seasonal happenings.
                 - Providing public transit, airport transfer, ride-hailing, or travel logistics tips.
                 - Recommending seasonal travel destinations based on climate or traveler style.
                 Examples:
                 - "What is the weather forecast in Florence next weekend?"
                 - "Find the best seafood trattorias near Positano."
                 - "What are the must-see museums and attractions in Athens?"
                 - "Are there any food wine festivals happening in Tuscany in October?"

              5. SUPPORT_QUESTION
                 Domain: Platform Rules, Verification, FAQ & Support Tickets
                 Agent: SupportAgent
                 Scope & Triggers:
                 - Checking general platform cancellation and refund policy tiers.
                 - Learning identity verification procedures, required documents, or badge criteria.
                 - Guidance on checking in, using lockboxes, or key pickup instructions.
                 - Resolving guest/host disputes, damage claims, or security deposit rules.
                 - Browsing platform FAQs, house rule guidelines, and community standards.
                 - Creating an official support ticket or escalating issues to human customer support.
                 Examples:
                 - "What is the platform policy on security deposit refunds?"
                 - "How do I verify my passport for host approval?"
                 - "I need to open a support ticket regarding an unauthorized charge."
                 - "What are the standard check-in hours across Aggarly properties?"

              6. ADMIN_MANAGEMENT
                 Domain: Platform Administration, Moderation, Coupons & System Health
                 Agent: AdminAgent
                 Scope & Triggers:
                 - Image moderation audits and inappropriate content detection.
                 - Promotional coupon creation, discounts, expiration, and validation.
                 - Platform metadata tagging, search indexing, and tag taxonomy updates.
                 - System health checks, service uptime, database latency, and storage status.
                 Examples:
                 - "Check if image key villa-photo-92.jpg violates moderation policies."
                 - "Create a 20% discount promo coupon code SUMMER2026."
                 - "Tag property ID 491a with tags 'sea-view', 'infinity-pool', 'superhost'."
                 - "Run platform system health diagnostics."

              7. SCHEDULE_AUTOMATION
                 Domain: Recurring Tasks, Trigger Automation & Background Workflows
                 Agent: SchedulingAgent
                 Scope & Triggers:
                 - Scheduling automated recurring reports (e.g. monthly earnings on 1st of month).
                 - Setting up interval-based workflows (e.g. check prices every 3 days).
                 - Creating event-triggered automations (e.g. notify host when reservation is confirmed).
                 - Pausing, resuming, listing, or canceling active scheduled tasks.
                 Examples:
                 - "Send me my host earnings report on the 1st of every month."
                 - "Remind me to check availability every 2 weeks."
                 - "Whenever a booking is confirmed, send a welcome guide."
                 - "Pause my monthly earnings report."

              8. IMAGE_ANALYSIS
                 Domain: Multimodal Vision, Image Search, Visual Aesthetics, Photo Analysis & Room Coverage
                 Agent: VisionAgent
                 Scope & Triggers:
                 - Finding properties matching a photo, image URL, or visual style.
                 - Analyzing uploaded photos (detecting furniture, room type, aesthetic quality).
                 - Inspecting room coverage or visual profiles of listings.
                 - Multimodal visual search (reference image + text constraint).
                 - Side-by-side visual comparison of property aesthetics.
                 Examples:
                 - "Find me a place that looks like this image."
                 - "Show me modern minimalist villas with private infinity pools."
                 - "What style and amenities are in this photo?"
                 - "Compare the visual styles of property 491a and 882c."

              ================================================================================
              CLASSIFICATION RULES & BOUNDARIES
              ================================================================================
              - If a message combines multiple distinct domains (e.g. search + weather + book), ALWAYS classify as MULTI_STEP_COMPLEX.
              - If the user asks for image-based search, visual aesthetics, photo analysis, or visual comparison, classify as IMAGE_ANALYSIS.
              - If the user asks for property amenities, rules, photos, or comparisons, classify as PROPERTY_SEARCH.
              - If the user asks about an existing booking, canceling, check-in access, prices of a booking, or writing a guest review, classify as BOOKING_ACTION.
              - If the user acts as a host (earnings, occupancy, pricing rules, calendar blocking, replying to guests), classify as HOST_MANAGEMENT.
              - If the user asks about city weather, tourist spots, dining, or local transit, classify as TRAVEL_PLANNING.
              - If the user asks about general platform rules, account verification, or UI navigation, classify as SUPPORT_QUESTION.
              - If the user is managing coupons or moderation, classify as ADMIN_MANAGEMENT.

              OUTPUT FORMAT:
              Respond with ONLY ONE of the 8 exact intent code strings above. Do not include markdown, explanations, or punctuation.
              """)
        );

        if (context != null && context.conversationHistory() != null && !context.conversationHistory().isEmpty()) {
            messages.addAll(context.conversationHistory());
        }

        messages.add(ChatMessage.user(userMessage, context != null ? context.screenshotUrl() : null));

        String rawCategory = llmClient.chat(messages);
        IntentCategory category;
        try {
            category = IntentCategory.valueOf(rawCategory.trim().toUpperCase());
        } catch (Exception ex) {
            log.warn("Failed to parse intent category '{}', defaulting to PROPERTY_SEARCH", rawCategory);
            category = IntentCategory.PROPERTY_SEARCH;
        }
        return new ClassifiedIntent(category, userMessage, context);
    }
}
