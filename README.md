# Aggarly Modular Enterprise Platform

<p align="center">
  <img src="https://img.shields.io/badge/Spring_Boot-3.4.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Qdrant-Vector_DB-FF004F?style=for-the-badge&logo=qdrant&logoColor=white" alt="Qdrant" />
  <img src="https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis&logoColor=white" alt="Redis" />
  <img src="https://img.shields.io/badge/Ollama-Multi--Modal_AI-000000?style=for-the-badge&logo=ollama&logoColor=white" alt="Ollama" />
</p>

---

## 🌟 Overview

**Aggarly** is an enterprise-grade modular vacation rental and intelligent property concierge platform built with **Spring Boot 3**, **Spring AI**, **PostgreSQL**, **Redis**, and **Qdrant Vector Database**.

It combines multi-agent conversational AI, multimodal visual property search, real-time STOMP messaging, and granular role-based security.

---

## 🏗️ Architecture & Modules

```
com.luna.aggarly
├── aiagent        # Multi-Agent Concierge (Property, Booking, Travel, Support, Scheduling, Host, Admin)
├── vision         # Multimodal Vector Search, Image Perception, Quality Scoring & Qdrant Multi-Vectors
├── property       # Listings, Media Management, Amenities, Pricing & Address
├── booking        # Reservations, Instant Booking, Cancellation Policies & Date Locks
├── payment        # Stripe Gateway, Transactions, Refunds & Host Payouts
├── user           # Auth (JWT, MFA/TOTP, OAuth2, Phone Validation, RBAC)
├── notification   # STOMP WebSocket, Email (Async), Push Notifications
├── review         # Guest/Host Reviews, Ratings & Content Moderation
├── filestorage    # S3 / MinIO / Local Multi-Provider File Storage
└── common         # Global Exception Handlers, Standardized ApiResponse<T>, Base Entities
```

---

## 🚀 Key Capabilities

1. **Multi-Agent AI Concierge**:
   - 7 specialized autonomous agents with dynamic tool injection and zero prompt hardcoding.
   - Fallback AI concierge thread resolution for null/unassigned conversations.

2. **Multimodal Visual & Vector Search**:
   - 3-Channel Vector Retrieval (`image_vector` 768d + `caption_vector` 768d + `description_vector` 768d).
   - Dynamic Active-Weight Normalization & Cross-Modal Reranking.
   - Zero-dependency semantic fallback for offline local environments.

3. **Enterprise Security & Reliability**:
   - Stateless JWT Authentication with `@AuthenticationPrincipal UserPrincipal`.
   - Centralized boundary enforcement in `SecurityConfig.java`.
   - Comprehensive Flyway migrations (`V1` to `V33`).

---

## 🛠️ Getting Started

### Prerequisites
- **JDK 17+**
- **Docker & Docker Compose** (PostgreSQL 16, Redis 7, Qdrant)
- **Maven 3.9+** (or `./mvnw`)
- *(Optional)* **Ollama** (`ollama pull nomic-embed-text minimax-m3`)

### Local Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/Aggarly-ORG/Aggarly-Moduler-Project.git
   cd Aggarly-Moduler-Project
   ```

2. **Start Infrastructure Containers**:
   ```bash
   docker-compose up -d
   ```

3. **Run the Application**:
   ```bash
   ./mvnw spring-boot:run
   ```

4. **Access Swagger UI & API Docs**:
   ```
   http://localhost:8081/swagger-ui/index.html
   ```

---

## 🧪 Running Tests

```bash
# Run all unit & integration tests
./mvnw test

# Verify full build
./mvnw clean verify
```

---

## 📄 License
Copyright © 2026 Aggarly Organization. All rights reserved.
