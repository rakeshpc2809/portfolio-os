FROM python:3.12-slim

# Copy official pre-built uv binary for ultra-fast dependency installation
COPY --from=ghcr.io/astral-sh/uv:latest /uv /bin/uv

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    curl \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
# Fast parallel dependency resolution and installation
RUN uv pip install --system --no-cache -r requirements.txt

COPY . .

EXPOSE 8000 8001

CMD ["python", "app.py"]
