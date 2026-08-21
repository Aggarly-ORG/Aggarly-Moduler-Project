#!/bin/bash
# Pull all vision models required for Aggarly Vision Module
echo "Pulling LLaVA 7B (Perception VLM)..."
docker exec aggarly-ollama ollama pull llava:7b

echo "Pulling nomic-embed-text (Embeddings)..."
docker exec aggarly-ollama ollama pull nomic-embed-text

echo "Pulling gemma3:4b (LLM Reranker)..."
docker exec aggarly-ollama ollama pull gemma3:4b

echo "All vision models pulled successfully."
