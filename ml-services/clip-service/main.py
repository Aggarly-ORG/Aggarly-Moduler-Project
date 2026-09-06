import io
import os
import logging
import base64
from typing import List, Optional, Dict, Any
from fastapi import FastAPI, UploadFile, File, Request, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from PIL import Image
import torch

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("clip-service")

MODEL_NAME = os.getenv("CLIP_MODEL_NAME", "ViT-B-32")
PRETRAINED = os.getenv("CLIP_PRETRAINED", "laion2b_s34b_b79k")
IS_JINA = "jina" in MODEL_NAME.lower()

app = FastAPI(
    title="Aggarly Neural CLIP Embedding Service",
    version="2.0.0",
    description="High-performance neural vision & multimodal text embeddings powered by Jina CLIP v2 and OpenCLIP"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Global model state
device = "cuda" if torch.cuda.is_available() else ("mps" if torch.backends.mps.is_available() else "cpu")
logger.info(f"Initializing Vision Service (model={MODEL_NAME}, is_jina={IS_JINA}) on device: {device}")

try:
    if IS_JINA:
        from transformers import AutoModel
        logger.info(f"Loading Jina CLIP v2 model '{MODEL_NAME}' with trust_remote_code=True...")
        model = AutoModel.from_pretrained(MODEL_NAME, trust_remote_code=True)
        model = model.to(device).eval()
        tokenizer = None
        preprocess = None
        VECTOR_DIM = 768
        logger.info(f"Jina CLIP v2 model loaded successfully. Target vector dimension: {VECTOR_DIM}")
    else:
        import open_clip
        model, _, preprocess = open_clip.create_model_and_transforms(MODEL_NAME, pretrained=PRETRAINED)
        model = model.to(device).eval()
        tokenizer = open_clip.get_tokenizer(MODEL_NAME)
        VECTOR_DIM = model.visual.output_dim if hasattr(model.visual, "output_dim") else 512
        logger.info(f"OpenCLIP model loaded successfully. Output vector dimension: {VECTOR_DIM}")
except Exception as e:
    logger.error(f"Failed to load vision model: {e}")
    raise e


class EmbedResponse(BaseModel):
    vector: List[float]
    dim: int
    model: str

class BatchEmbedResponse(BaseModel):
    vectors: List[List[float]]
    dim: int
    count: int
    model: str

class HealthResponse(BaseModel):
    status: str
    model: str
    pretrained: str
    device: str
    dimension: int


@app.get("/health", response_model=HealthResponse)
def health_check():
    return {
        "status": "UP",
        "model": MODEL_NAME,
        "pretrained": "hf-hub" if IS_JINA else PRETRAINED,
        "device": str(device),
        "dimension": VECTOR_DIM
    }


@app.post("/embed/image", response_model=EmbedResponse)
async def embed_image_file(file: UploadFile = File(...)):
    """Computes a 768-dim (or 512-dim) neural vision embedding from an uploaded image file."""
    try:
        image_bytes = await file.read()
        if not image_bytes:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Empty image file received")
        
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")

        if IS_JINA:
            with torch.no_grad():
                res = model.encode_image(image, truncate_dim=VECTOR_DIM)
                vector = res.tolist() if hasattr(res, "tolist") else list(res)
        else:
            tensor = preprocess(image).unsqueeze(0).to(device)
            with torch.no_grad():
                image_features = model.encode_image(tensor)
                image_features /= image_features.norm(dim=-1, keepdim=True)
            vector = image_features.cpu().squeeze(0).tolist()

        return {"vector": vector, "dim": len(vector), "model": MODEL_NAME}
    except Exception as e:
        logger.error(f"Image embedding failed: {e}")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(e))


@app.post("/embed/image-base64", response_model=EmbedResponse)
async def embed_image_base64(request: Request):
    """Computes a neural vision embedding from a Base64-encoded image string."""
    try:
        body_bytes = await request.body()
        if not body_bytes or not body_bytes.strip():
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Empty image-base64 request body")

        try:
            import json
            body = json.loads(body_bytes.decode("utf-8", errors="replace"))
        except Exception:
            raw_str = body_bytes.decode("utf-8", errors="replace").strip()
            body = {"image_base64": raw_str}

        raw_b64 = body.get("image_base64") or body.get("imageBase64") or body.get("image") if isinstance(body, dict) else str(body)
        if not raw_b64:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Missing image_base64 field in request body")
        
        raw_b64 = str(raw_b64).strip()
        if "," in raw_b64:
            raw_b64 = raw_b64.split(",")[1]
        
        image_bytes = base64.b64decode(raw_b64)
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")

        if IS_JINA:
            with torch.no_grad():
                res = model.encode_image(image, truncate_dim=VECTOR_DIM)
                vector = res.tolist() if hasattr(res, "tolist") else list(res)
        else:
            tensor = preprocess(image).unsqueeze(0).to(device)
            with torch.no_grad():
                image_features = model.encode_image(tensor)
                image_features /= image_features.norm(dim=-1, keepdim=True)
            vector = image_features.cpu().squeeze(0).tolist()

        return {"vector": vector, "dim": len(vector), "model": MODEL_NAME}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Base64 image embedding failed: {e}")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(e))


@app.post("/embed/text", response_model=EmbedResponse)
async def embed_text(request: Request):
    """Computes a neural text embedding projected into the exact same multimodal latent space."""
    try:
        body_bytes = await request.body()
        if not body_bytes or not body_bytes.strip():
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Empty text embedding request body")

        try:
            import json
            body = json.loads(body_bytes.decode("utf-8", errors="replace"))
        except Exception:
            raw_str = body_bytes.decode("utf-8", errors="replace").strip()
            body = {"text": raw_str}

        text = body.get("text") or body.get("query") if isinstance(body, dict) else str(body)
        if not text or not str(text).strip():
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Text query cannot be empty")
        
        clean_text = str(text).strip()

        if IS_JINA:
            with torch.no_grad():
                res = model.encode_text(clean_text, truncate_dim=VECTOR_DIM)
                vector = res.tolist() if hasattr(res, "tolist") else list(res)
        else:
            tokens = tokenizer([clean_text]).to(device)
            with torch.no_grad():
                text_features = model.encode_text(tokens)
                text_features /= text_features.norm(dim=-1, keepdim=True)
            vector = text_features.cpu().squeeze(0).tolist()

        return {"vector": vector, "dim": len(vector), "model": MODEL_NAME}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Text embedding failed: {e}")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(e))


@app.post("/embed/batch-text", response_model=BatchEmbedResponse)
async def embed_batch_text(request: Request):
    """Computes batch text embeddings."""
    try:
        body_bytes = await request.body()
        if not body_bytes or not body_bytes.strip():
            return {"vectors": [], "dim": VECTOR_DIM, "count": 0, "model": MODEL_NAME}

        import json
        body = json.loads(body_bytes.decode("utf-8", errors="replace"))
        texts = body.get("texts") or []
        if not texts:
            return {"vectors": [], "dim": VECTOR_DIM, "count": 0, "model": MODEL_NAME}
        
        if IS_JINA:
            with torch.no_grad():
                res = model.encode_text(texts, truncate_dim=VECTOR_DIM)
                vectors = res.tolist() if hasattr(res, "tolist") else [list(v) for v in res]
        else:
            tokens = tokenizer(texts).to(device)
            with torch.no_grad():
                text_features = model.encode_text(tokens)
                text_features /= text_features.norm(dim=-1, keepdim=True)
            vectors = text_features.cpu().tolist()

        return {"vectors": vectors, "dim": len(vectors[0]) if vectors else VECTOR_DIM, "count": len(vectors), "model": MODEL_NAME}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Batch text embedding failed: {e}")
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(e))


if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("PORT", 8000))
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=False)
