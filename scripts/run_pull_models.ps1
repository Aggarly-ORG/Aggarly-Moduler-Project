# PowerShell script to pull Ollama vision models
Write-Host "Pulling LLaVA 7B (Perception VLM)..." -ForegroundColor Cyan
docker exec aggarly-ollama ollama pull llava:7b

Write-Host "Pulling nomic-embed-text (Embeddings)..." -ForegroundColor Cyan
docker exec aggarly-ollama ollama pull nomic-embed-text

Write-Host "Pulling gemma3:4b (LLM Reranker)..." -ForegroundColor Cyan
docker exec aggarly-ollama ollama pull gemma3:4b

Write-Host "All vision models pulled successfully." -ForegroundColor Green
