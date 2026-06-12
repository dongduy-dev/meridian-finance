# Environment Variable Reference

This document templates the required and optional environment variables for the Project Meridian Spring Boot application.

| Variable Name | Description | Format / Type | Required? | Default Value |
|---|---|---|---|---|
| **Application Core** | | | | |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | String (`dev`, `prod`) | Yes | `dev` |
| `SERVER_PORT` | HTTP port for the application | Integer | No | `8080` |
| **Database (PostgreSQL)** | | | | |
| `DB_HOST` | Database host address | String | Yes | `localhost` |
| `DB_PORT` | Database port | Integer | No | `5432` |
| `DB_NAME` | Database name | String | Yes | `meridian_db` |
| `DB_USER` | Database username | String | Yes | - |
| `DB_PASSWORD` | Database password | String | Yes | - |
| `DB_POOL_MAX_SIZE` | HikariCP maximum pool size | Integer | No | `20` |
| **Security & Cryptography** | | | | |
| `JWT_PRIVATE_KEY_PATH` | Path to RSA private key for signing JWT | String (File Path) | Yes | - |
| `JWT_PUBLIC_KEY_PATH` | Path to RSA public key for verifying JWT | String (File Path) | Yes | - |
| `JWT_EXPIRATION_MINUTES` | Access token lifespan | Integer | No | `15` |
| `PII_ENCRYPTION_KEY` | 256-bit AES key (Base64) for DB encryption | String (Base64) | Yes | - |
| **OCR Service Integration** | | | | |
| `OCR_SERVICE_URL` | Base URL of the Python FastAPI OCR service | String (URL) | Yes | `http://localhost:8000` |
| `OCR_API_KEY` | Shared secret for inter-service auth | String | Yes | - |
| **Observability** | | | | |
| `LOGGING_LEVEL_ROOT` | Root log level | String | No | `INFO` |
| `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | Actuator endpoints exposed | String | No | `health,prometheus` |
