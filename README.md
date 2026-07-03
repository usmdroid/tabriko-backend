# TabrikO Backend MVP

Spring Boot 3 + Java 21 API for the TabrikO personalized greeting marketplace.

## Quick start — local (needs JDK 21 + PostgreSQL)

```bash
# 1. Copy env template
cp .env.example .env

# 2. Create local database
createdb tabriko   # or use your PostgreSQL client

# 3. Build and run
mvn spring-boot:run
```

Swagger UI: http://localhost:8080/swagger-ui.html

## Quick start — docker-compose

```bash
cp .env.example .env        # edit if needed
docker-compose up --build
```

App: http://localhost:8080  
Swagger: http://localhost:8080/swagger-ui.html

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/tabriko` | JDBC URL |
| `DB_USERNAME` | `tabriko` | DB user |
| `DB_PASSWORD` | `tabriko` | DB password |
| `JWT_ACCESS_SECRET` | (dev default) | Min 32 chars — change in production |
| `JWT_REFRESH_SECRET` | (dev default) | Min 32 chars — change in production |
| `JWT_ACCESS_EXPIRY_MS` | `3600000` | Access token TTL (1 hour) |
| `JWT_REFRESH_EXPIRY_MS` | `2592000000` | Refresh token TTL (30 days) |
| `COMMISSION_PERCENT` | `15` | Platform commission % |
| `MEDIA_UPLOAD_DIR` | `./uploads` | Local upload directory |
| `MEDIA_BASE_URL` | `http://localhost:8080/files` | Public base URL for media |
| `FIREBASE_CREDENTIALS_PATH` | _(empty)_ | Path to Firebase service-account JSON; empty = mock OTP/FCM |
| `PORT` | `8080` | Server port |

## Seeded demo accounts

All use OTP `123456` in dev mode.

| Phone | Role | Notes |
|---|---|---|
| `+998901234567` | SUPERADMIN | Admin user |
| `+998901234568` | MODERATOR | Moderator user |
| `+998901111001` | CREATOR | Jahongir Xoliqov (Xonanda, verified, top) |
| `+998901111002` | CREATOR | Malika Rahimova (Bloger, verified, top) |
| `+998901111003` | CREATOR | Bekzod Komilов (Aktyor, verified) |
| `+998909999999` | CLIENT | Demo client |
| Any new number | CLIENT | Auto-registered on first OTP verify |

## Auth flow

```
POST /api/v1/auth/send-otp      { "phone": "+998901234567" }
POST /api/v1/auth/verify-otp    { "phone": "...", "code": "123456" }
  → { "accessToken": "...", "refreshToken": "...", "user": {...} }

POST /api/v1/auth/refresh        { "refreshToken": "..." }
  → { "accessToken": "...", "refreshToken": "..." }
```

Add `Authorization: Bearer <accessToken>` to all authenticated requests.

## Response contract

Every response (success and error) uses:
```json
{
  "success": true,
  "httpStatus": 200,
  "code": 0,
  "message": { "code": 0, "text": "OK" },
  "data": <payload or null>
}
```

## Architecture

```
controller  →  service  →  repository  →  PostgreSQL (JPA + Flyway)
                  ↓
          infrastructure/
            firebase/   (OtpService, PushNotificationService — mock in dev)
            payment/    (PaymentGateway — mock stub, replace with Click/Payme)
            media/      (MediaStorageService — local disk in dev)
```

## Build

```bash
mvn -q -DskipTests package
```

Produces `target/tabriko-backend-1.0.0-SNAPSHOT.jar`.
