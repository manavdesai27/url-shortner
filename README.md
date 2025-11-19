# 🔗 URL Shortener

A minimal yet efficient URL shortening service built with **Spring Boot**, **PostgreSQL**, and **Redis**. It mimics the functionality of services like Bit.ly, with support for redirection, caching, and click tracking.

---

## 🚀 Features

- ✅ Generate a unique short URL for any long URL
- ✅ Redirect to the original URL using the short code
- ✅ Cache short-to-original URL mappings in Redis (1-hour TTL)
- ✅ Track how many times each short URL is clicked
- ✅ RESTful API design with proper exception handling
- ✅ Built for extensibility: supports analytics, expiration, rate-limiting, etc.

---

## 🛠️ Tech Stack

- **Java 17**
- **Spring Boot**
- **PostgreSQL**
- **Redis**
- **Docker** (for Redis)
- **pgAdmin** (for DB exploration)

---

## 📦 Endpoints

### 1. Create a short URL

```http
POST /api/shorten
Content-Type: application/json

{
  "originalUrl": "https://github.com"
}

{
  "shortCode": "abc123"
}
```

---

### 2. Redirect to original URL

```http
GET /{shortCode}
```

Returns a `302 Found` with a `Location` header to redirect to the original URL.

---

### 3. Get click count for a short code

```http
GET /analytics/{shortCode}
```

**Response**
```json
{
  "shortCode": "abc123",
  "clicks": 42
}
```


---

## 📊 Database Design

**Table: `url_mapping`**

| Column       | Type      | Notes                  |
|--------------|-----------|------------------------|
| id           | BIGINT    | Primary Key            |
| short_code   | VARCHAR   | Unique, indexed        |
| original_url | TEXT      |                        |
| click_count  | INTEGER   | Tracks usage           |
| created_at   | TIMESTAMP | Defaults to `now()`    |

---

## 🧠 Design Highlights

- **Caching**: URLs are cached in Redis to avoid hitting the DB repeatedly
- **Persistence**: All mappings and click counts are stored in PostgreSQL
- **Exception Handling**: Custom `UrlNotFoundException` for unknown short codes
- **Scalability**: Foundation for future features like custom aliases, expiration, and analytics

---

## ✅ Running Locally

### Prerequisites
- Java 17+
- PostgreSQL running locally (or via Docker)
- Redis (preferably via Docker)

### Start Redis (Docker)
```bash
docker run -d -p 6379:6379 --name redis redis
```

### Run the App
```bash
./mvnw spring-boot:run
```

---

## 📈 Future Enhancements
- Expiring short URLs
- Per-user short links with auth
- Geo/IP-based analytics
- Rate limiting & abuse protection
- Frontend UI

---

## 🔍 Observability & Correlated Logging

This project includes end-to-end logging so you can see which request/flow triggered which DB queries and cache lookups. Key pieces:

### 1) Request correlation (MDC)
- `CorrelationIdFilter` adds these MDC fields to every log:
  - `requestId` (from `X-Request-ID` header if provided, otherwise a UUID)
  - `user` (authenticated username if present)
  - `method`, `path`, `clientIp`
- Response echoes `X-Request-ID` so you can correlate across services.

Logback pattern (see `src/main/resources/logback-spring.xml`) includes MDC fields:
```
%d %-5level [%thread] reqId=%X{requestId} user=%X{user} method=%X{method} path=%X{path} ip=%X{clientIp} %logger - %msg%n
```

### 2) Request lifecycle logs
- `RequestLoggingFilter` logs:
  - `REQ START` with method, path, client IP, user, and requestId.
  - `REQ END` with HTTP status and duration in ms.

Example:
```
INFO  REQ START method=POST path=/shorten clientIp=127.0.0.1 user=john requestId=...
INFO  REQ END   method=POST path=/shorten status=200 durationMs=23 requestId=...
```

### 3) Service/repository timing (AOP)
- `MethodTimingAspect` logs timing for all `service` and `repository` methods:
```
INFO  CALL UrlShortenerService.shortenUrl(..) durationMs=5
INFO  CALL UrlMappingRepository.findActiveByShortCode(..) durationMs=2
```

### 4) Cache visibility
- Explicit logging added to `UrlShortenerService` for Redis lookups/sets:
  - `CACHE GET` + HIT/MISS
  - `CACHE SET` with TTL
- Rate limiting responses are logged as `RATE_LIMITED ...` with key details.

### 5) SQL visibility (profile: `debug-sql`)
- Default: SQL logging off to reduce noise (`spring.jpa.show-sql=false`).
- Enable detailed SQL + bind parameters:
  - Activate profile:
    ```bash
    ./mvnw spring-boot:run -Dspring-boot.run.profiles=debug-sql
    # or
    SPRING_PROFILES_ACTIVE=debug-sql ./mvnw spring-boot:run
    ```
  - Config source: `src/main/resources/application-debug-sql.properties`:
    - `spring.jpa.show-sql=true`
    - `logging.level.org.hibernate.SQL=DEBUG`
    - `logging.level.org.hibernate.orm.jdbc.bind=TRACE` (Hibernate 6 bind params)

### 6) Redis wire protocol visibility (profile: `debug-cache`)
- Enable Redis driver and Spring Data Redis debug:
  - Activate profile:
    ```bash
    ./mvnw spring-boot:run -Dspring-boot.run.profiles=debug-cache
    # or
    SPRING_PROFILES_ACTIVE=debug-cache ./mvnw spring-boot:run
    ```
  - Config source: `src/main/resources/application-debug-cache.properties`:
    - `logging.level.org.springframework.data.redis=DEBUG`
    - `logging.level.io.lettuce.core.protocol=DEBUG`
- Note: This can be verbose; use in dev/troubleshooting.

### 7) Combine profiles
You can combine them:
```bash
SPRING_PROFILES_ACTIVE=debug-sql,debug-cache ./mvnw spring-boot:run
```

### 8) Tips
- Include `X-Request-ID` in your client requests to propagate correlation across components.
- Tail logs and grep by `reqId` to see the full flow:
```bash
# Example if logging to console:
./mvnw spring-boot:run | grep reqId=YOUR-ID
```

---

## 🧑‍💻 Author

Built as a hands-on learning project to strengthen backend system design and real-time performance handling.
