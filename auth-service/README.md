# Life Admin Manager — Auth Service

Spring Boot JWT authentication service for the Life Admin Manager app.

## Stack
- Java 17 · Spring Boot 3.2 · Spring Security
- JWT (jjwt 0.12) · BCrypt password hashing
- In-memory store (swap → PostgreSQL in Phase 2)

---

## Run locally

```bash
mvn spring-boot:run
# Server starts on http://localhost:8080/api/v1
```

## Run with Docker

```bash
docker build -t life-admin/auth-service .
docker run -p 8080:8080 life-admin/auth-service
```

---

## Dummy Credentials (dev only)

| Email                    | Password      | Role  | Tier    |
|--------------------------|---------------|-------|---------|
| admin@lifeadmin.com      | Admin@1234    | ADMIN | PREMIUM |
| user@lifeadmin.com       | User@1234     | USER  | FREE    |
| premium@lifeadmin.com    | Premium@123   | USER  | PREMIUM |

---

## API Reference

Base URL: `http://localhost:8080/api/v1`

### POST /auth/register
```json
{
  "fullName": "John Doe",
  "email": "john@example.com",
  "password": "Secure@123"
}
```

### POST /auth/login
```json
{
  "email": "user@lifeadmin.com",
  "password": "User@1234"
}
```
**Response:**
```json
{
  "success": true,
  "message": "Login successful.",
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "dummy-user-001",
      "email": "user@lifeadmin.com",
      "fullName": "Free Tier User",
      "role": "USER",
      "tier": "FREE",
      "emailVerified": true
    }
  }
}
```

### POST /auth/refresh
```json
{ "refreshToken": "eyJ..." }
```

### POST /auth/logout  *(requires Bearer token)*
```json
{ "refreshToken": "eyJ..." }
```

### POST /auth/logout-all  *(requires Bearer token)*
Revokes all sessions for the current user.

### GET /auth/me  *(requires Bearer token)*
Returns current user profile.

### POST /auth/change-password  *(requires Bearer token)*
```json
{
  "currentPassword": "User@1234",
  "newPassword": "NewPass@456"
}
```

### GET /auth/validate  *(requires Bearer token)*
Lightweight endpoint for other microservices to validate a token.

---

## Curl Examples

```bash
# Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@lifeadmin.com","password":"User@1234"}' \
  | jq -r '.data.accessToken')

# Get profile
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/auth/me
```

---

## Phase 2 Migration Notes

| Component          | Current (MVP)           | Phase 2                        |
|--------------------|-------------------------|--------------------------------|
| `DummyUserStore`   | In-memory ConcurrentMap | JPA `UserRepository` + Postgres |
| `RefreshTokenStore`| In-memory + @Scheduled  | Redis `SETEX` with auto-TTL    |
| Google OAuth       | Stub (TODO)             | Spring OAuth2 + Google IdP     |
| Email verification | Flag only               | SendGrid / SES email flow      |
| Rate limiting      | TODO                    | Bucket4j + Redis               |

---

## Token Design

```
Access Token  — 15 min TTL  — used in Authorization header for every request
Refresh Token — 7 day TTL   — stored client-side, used only to get new access token
```

Refresh tokens **rotate on every use** (old token is invalidated, new one issued).
Password changes invalidate **all** refresh tokens for the user.
