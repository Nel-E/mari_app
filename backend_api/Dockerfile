FROM python:3.12-slim

WORKDIR /app

RUN pip install --no-cache-dir \
    "fastapi>=0.111" \
    "uvicorn[standard]>=0.30" \
    "pydantic-settings>=2.3"

COPY mari_api/ ./mari_api/

ENV MARI_DATA_DIR=/data/app_updates

EXPOSE 8000

CMD ["uvicorn", "mari_api.main:app", "--host", "0.0.0.0", "--port", "8000"]
