FROM python:3.12.11-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app

COPY requirements.lock pyproject.toml ./
COPY src ./src

RUN python -m pip install --no-cache-dir --requirement requirements.lock \
    && python -m pip install --no-cache-dir --no-build-isolation --no-deps .

RUN useradd --create-home --uid 10001 meridian-ocr
USER meridian-ocr

EXPOSE 8090

CMD ["uvicorn", "meridian_ocr.main:app", "--host", "0.0.0.0", "--port", "8090"]
