# Infrastructure, Security & Observability Architecture

---

## Part 1: Infrastructure Evolution (Phase 3-4)

### Redis Strategy

**When to introduce Redis (Phase 3):**

| Use Case | Implementation | TTL |
|---|---|---|
| **JWT blacklist** | Set of revoked token JTIs | Match token expiry (15 min) |
| **Rate limiting** | Sliding window counters per user/IP | 1 minute windows |
| **Idempotency key cache** | Fast dedup check before hitting DB | 24 hours |
| **Session cache** | Reduce DB reads for auth context | 30 minutes |
| **Loan status cache** | Cache frequently polled loan statuses | 30 seconds (short!) |

**What NOT to cache:**
- Financial amounts (stale money values are dangerous)
- Approval decisions (must always be fresh)
- Audit events (never cache write-path data)

```java
// Example: Rate limiting with Redis
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final StringRedisTemplate redis;
    
    @Override
    protected void doFilterInternal(HttpServletRequest req, ...) {
        String key = "rate:" + extractClientId(req);
        Long count = redis.opsForValue().increment(key);
        if (count == 1) redis.expire(key, Duration.ofMinutes(1));
        if (count > 100) {
            response.setStatus(429);
            return;
        }
        filterChain.doFilter(req, response);
    }
}
```

### Elasticsearch Strategy (Phase 4)

| Index | Source | Sync Strategy | Use Case |
|---|---|---|---|
| `loans` | loan_applications table | CDC via Debezium or app-level events | Loan search by officer |
| `audit_events` | audit_events table | Async event listener | Compliance search |
| `customers` | customers table | App-level events | Customer lookup |

**Data Sync Pattern (App-Level, simpler than CDC):**

```java
@EventListener
public void onLoanStatusChanged(LoanStatusChangedEvent event) {
    var doc = loanSearchMapper.toSearchDocument(event);
    elasticsearchClient.index(i -> i
        .index("loans")
        .id(event.loanId().toString())
        .document(doc));
}
```

**Failure handling:** If ES is down, log the failure and continue. ES is a read-optimization, not the source of truth. Add a reconciliation job that runs nightly to catch missed events.

### Event Publication Registry — Transactional Outbox (Phase 1)

Spring Modulith's Event Publication Registry replaces a bespoke outbox implementation.
Adding `spring-modulith-events-jdbc` to the classpath activates it automatically.

**How it works:**

| Step | What happens |
|---|---|
| 1 | A use case (e.g. `SubmitLoanService`) calls `eventPublisher.publishEvent(new LoanSubmittedEvent(...))` inside a `@Transactional` method |
| 2 | Spring Modulith intercepts the call and writes a row to `event_publication` **in the same transaction** |
| 3 | If the transaction rolls back, the event row is also rolled back — no phantom events |
| 4 | After the transaction commits, Spring delivers the event to all `@ApplicationModuleListener` consumers |
| 5 | Each listener marks its row complete in `event_publication` upon success |
| 6 | If the JVM crashes between steps 4 and 5, the row survives with `completion_date = NULL` |
| 7 | On next startup, a scheduler calls `IncompleteEventPublications.resubmitIncompletePublicationsOlderThan(Duration)` to replay those rows |

**Schema (Flyway migration — `db/migration/shared/`):**

```sql
CREATE TABLE event_publication (
    id                UUID        NOT NULL,
    listener_id       TEXT        NOT NULL,
    event_type        TEXT        NOT NULL,
    serialized_event  TEXT        NOT NULL,
    publication_date  TIMESTAMPTZ NOT NULL,
    completion_date   TIMESTAMPTZ,
    PRIMARY KEY (id)
);

CREATE INDEX idx_event_publication_incomplete
    ON event_publication (publication_date)
    WHERE completion_date IS NULL;
```

> [!WARNING]
> Set `spring.modulith.events.jdbc.schema-initialization.enabled=false` in
> `application.yml`. Flyway owns all DDL — do not let Spring auto-create this table.

> [!IMPORTANT]
> All `@ApplicationModuleListener` consumers must be idempotent. Because events may be
> replayed, every listener must guard against processing the same event twice (e.g.
> check whether the resulting record already exists before inserting it).

**What NOT to persist in the outbox:**
- Events that are purely informational with no downstream state change
- High-volume polling events (use a separate polling table instead)

### Database Performance & Connection Pooling (Phase 1)

> [!WARNING]
> Without proper indexing, checking a customer's loan history or enforcing row-level security will result in a full table scan.

```sql
-- V1_0_2__add_loan_customer_index.sql
CREATE INDEX idx_loan_apps_customer ON loan_applications(customer_id);
```

```sql
-- V007__add_missing_indexes.sql
CREATE INDEX idx_loan_apps_status ON loan_applications(status);
CREATE INDEX idx_loan_apps_status_customer ON loan_applications(customer_id, status);
CREATE INDEX idx_approval_requests_loan ON approval_requests(loan_id);
CREATE INDEX idx_approval_requests_assignee ON approval_requests(assignee_id, status);
CREATE INDEX idx_documents_loan ON documents(loan_id);
CREATE INDEX idx_audit_events_entity ON audit_events(entity_type, entity_id);
CREATE INDEX idx_audit_events_actor ON audit_events(actor_id, created_at);
CREATE INDEX idx_ocr_jobs_pending ON ocr_jobs(status, priority DESC, created_at) WHERE status = 'PENDING';
```

#### HikariCP Pool Sizing for Virtual Threads

With Java 25 virtual threads, the default HikariCP pool of 10 is too small and can cause thread pinning contention. You must explicitly configure the pool size.

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true
  datasource:
    hikari:
      maximum-pool-size: 30
      minimum-idle: 5
      connection-timeout: 20000
      leak-detection-threshold: 30000 # 30 seconds
```

#### Flyway Migration Modularization

Flyway migrations must be partitioned by bounded context to support independent module evolution and future microservice extraction.

```yaml
# application.yml
spring:
  flyway:
    locations: classpath:db/migration/shared, classpath:db/migration/identity, classpath:db/migration/loan, classpath:db/migration/document
```

#### Global Pagination Limits

To prevent unbounded result sets from causing OutOfMemory (OOM) errors on listing endpoints, a global maximum page size must be enforced.

```yaml
# application.yml
spring:
  data:
    web:
      pageable:
        default-page-size: 20
        max-page-size: 100
```

---

## Part 2: Security Architecture

### Authentication Flow

```mermaid
sequenceDiagram
    participant Client
    participant API as Spring Boot
    participant DB as PostgreSQL

    Client->>API: POST /api/v1/auth/login {email, password}
    API->>DB: Verify credentials (bcrypt)
    API-->>Client: { accessToken (15min) } + Set-Cookie: refresh_token (7d)

    Note over Client: Subsequent requests

    Client->>API: GET /api/v1/loans (Authorization: Bearer <accessToken>)
    API->>API: Validate JWT signature + expiry
    API->>API: Extract roles from claims
    API-->>Client: 200 OK { loans }

    Note over Client: Token refresh

    Client->>API: POST /api/v1/auth/refresh (Cookie: refresh_token)
    API->>DB: Validate refresh token (not revoked)
    API-->>Client: { newAccessToken } + Set-Cookie: new_refresh_token
```

### Token Storage Strategy

| Token | Storage | Rationale |
|---|---|---|
| **Access token** | JavaScript memory (variable/context) | Short-lived (15min). Lost on page refresh — forces re-auth via refresh cookie. Immune to XSS persistence. |
| **Refresh token** | httpOnly + Secure + SameSite=Strict cookie | Invisible to JavaScript. Cannot be exfiltrated by XSS. Only sent to `/api/v1/auth` path. |

> [!CAUTION]
> **NEVER store tokens in localStorage or sessionStorage.** Any XSS vulnerability in the React app or its dependencies would allow token theft.

```java
// shared/infrastructure/security/CookieUtil.java
public final class CookieUtil {

    private CookieUtil() {}

    public static ResponseCookie createRefreshTokenCookie(String refreshToken, Duration maxAge) {
        return ResponseCookie.from("refresh_token", refreshToken)
            .httpOnly(true)
            .secure(true)            // HTTPS only
            .path("/api/v1/auth")    // Only sent to auth endpoints
            .maxAge(maxAge)
            .sameSite("Strict")
            .build();
    }

    public static ResponseCookie deleteRefreshTokenCookie() {
        return ResponseCookie.from("refresh_token", "")
            .httpOnly(true)
            .secure(true)
            .path("/api/v1/auth")
            .maxAge(0)
            .sameSite("Strict")
            .build();
    }
}
```

```java
// identity/infrastructure/adapter/in/web/AuthController.java — modified login
@PostMapping("/login")
public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
    AuthResult result = authUseCase.authenticate(request.email(), request.password());

    ResponseCookie cookie = CookieUtil.createRefreshTokenCookie(
        result.refreshToken(), Duration.ofDays(7));

    // Access token returned in body (stored in JS memory, NOT localStorage)
    // Refresh token set as httpOnly cookie (invisible to JS)
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(ApiResponse.success(new AuthResponse(result.accessToken(), result.expiresIn())));
}
```

### JWT Structure

```json
{
  "sub": "user-uuid",
  "email": "user@example.com",
  "roles": ["LOAN_OFFICER"],
  "permissions": ["loan:read", "loan:review", "approval:submit"],
  "iat": 1718000000,
  "exp": 1718000900,
  "jti": "unique-token-id"
}
```

| Parameter | Value | Rationale |
|---|---|---|
| Access token TTL | 15 minutes | Short-lived for security |
| Refresh token TTL | 7 days | Stored in DB, revocable |
| Algorithm | RS256 | Asymmetric — services can verify without the signing key |
| Refresh rotation | Yes | Each refresh issues new pair, old refresh is invalidated |

#### Permission Strings Embedded in the JWT

The `permissions` array in the JWT is populated at login from `RolePermissionRegistry`,
which is the single source of truth for role→permission mapping. Controllers evaluate
these via `@PreAuthorize("hasAuthority('...')")`.

**Permission naming convention:** `<resource>:<action>`

| Permission | Description |
|---|---|
| `loan:submit` | Submit a new loan application |
| `loan:read` | Read loan applications (service layer restricts Customer to own) |
| `loan:review` | Start review on a submitted application |
| `loan:cancel` | Cancel an application (service layer restricts Customer to own) |
| `loan:disburse` | Trigger disbursement of an approved loan |
| `loan:product:manage` | Create and deactivate loan products |
| `approval:read` | Read approval requests |
| `approval:submit` | Submit an approval decision within delegation limit |
| `approval:override` | Override a prior approval decision |
| `document:upload` | Upload documents |
| `document:read` | Read and download documents (service layer restricts Customer to own) |
| `document:verify` | Mark a document as verified |
| `customer:read` | Read customer profiles |
| `customer:update` | Update customer profile data |
| `audit:read` | Read audit event logs |
| `admin:user:manage` | Create, suspend, and manage user accounts |
| `admin:config` | Read and update system configuration |
| `admin:data:read-all` | Read all data across all customers (break-glass access) |

#### Role → Permission Matrix

| Permission | Customer | Loan Officer | Manager | Administrator |
|---|:---:|:---:|:---:|:---:|
| `loan:submit` | ✅ | — | — | — |
| `loan:read` | ✅ own | ✅ | ✅ | ✅ |
| `loan:review` | — | ✅ | ✅ | ✅ |
| `loan:cancel` | ✅ own | ✅ | ✅ | ✅ |
| `loan:disburse` | — | ✅ | ✅ | ✅ |
| `loan:product:manage` | — | — | ✅ | ✅ |
| `approval:read` | — | ✅ | ✅ | ✅ |
| `approval:submit` | — | ✅ | ✅ | ✅ |
| `approval:override` | — | — | ✅ | ✅ |
| `document:upload` | ✅ | — | — | — |
| `document:read` | ✅ own | ✅ | ✅ | ✅ |
| `document:verify` | — | ✅ | ✅ | ✅ |
| `customer:read` | — | ✅ | ✅ | ✅ |
| `customer:update` | — | — | ✅ | ✅ |
| `audit:read` | — | — | ✅ | ✅ |
| `admin:user:manage` | — | — | — | ✅ |
| `admin:config` | — | — | — | ✅ |
| `admin:data:read-all` | — | — | — | ✅ |

> [!NOTE]
> **"own" means the permission is granted but the service layer enforces row-level
> filtering.** A `CUSTOMER` with `loan:read` is a valid token holder; `QueryLoanService`
> checks the caller's role and returns only their own records. The permission grants
> entry to the endpoint; the query predicate enforces ownership. This pattern applies
> to `loan:read`, `loan:cancel`, and `document:read`.

### RSA Key Management

**Key Rotation & Storage Strategy:**
RS256 requires asymmetric keys. The private key must be protected and never committed to version control. The application loads keys via properties and uses an external rotation script to generate them.

```yaml
# application.yml
jwt:
  private-key-path: ${JWT_PRIVATE_KEY_PATH:classpath:keys/dev-only.pem}
  public-key-path: ${JWT_PUBLIC_KEY_PATH:classpath:keys/dev-only.pub}
  access-token-ttl: 15m
  refresh-token-ttl: 7d
  issuer: meridian-lending
```

```java
// shared/infrastructure/security/JwtProperties.java
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    Resource privateKeyPath,
    Resource publicKeyPath,
    Duration accessTokenTtl,
    Duration refreshTokenTtl,
    String issuer
) {}
```

```java
// shared/infrastructure/security/JwtTokenProvider.java
@Component
public class JwtTokenProvider {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final JwtProperties props;

    public JwtTokenProvider(JwtProperties props) throws Exception {
        this.props = props;
        this.privateKey = loadPrivateKey(props.privateKeyPath());
        this.publicKey = loadPublicKey(props.publicKeyPath());
    }

    public String generateAccessToken(UUID userId, String email, Set<String> roles, Set<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(userId.toString())
            .claim("email", email)
            .claim("roles", roles)
            .claim("permissions", permissions)
            .issuer(props.issuer())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(props.accessTokenTtl())))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();
    }

    public Jws<Claims> parseToken(String token) {
        return Jwts.parser()
            .verifyWith(publicKey)
            .requireIssuer(props.issuer())
            .build()
            .parseSignedClaims(token);
    }

    private RSAPrivateKey loadPrivateKey(Resource resource) throws Exception {
        String pem = resource.getContentAsString(StandardCharsets.UTF_8);
        pem = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                 .replace("-----END PRIVATE KEY-----", "")
                 .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
            .generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private RSAPublicKey loadPublicKey(Resource resource) throws Exception {
        String pem = resource.getContentAsString(StandardCharsets.UTF_8);
        pem = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                 .replace("-----END PUBLIC KEY-----", "")
                 .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(decoded));
    }
}
```

Key generation script (add to project root as `generate-keys.sh`):

```bash
#!/bin/bash
mkdir -p src/main/resources/keys
openssl genpkey -algorithm RSA -out src/main/resources/keys/dev-only.pem -pkeyopt rsa_keygen_bits:2048
openssl pkey -in src/main/resources/keys/dev-only.pem -pubout -out src/main/resources/keys/dev-only.pub
echo "src/main/resources/keys/" >> .gitignore
echo "Keys generated. NEVER commit these to version control."
```

### Network Security, Rate Limiting & Idempotency (Phase 1)

**Strategy:** Protect authentication and public-facing endpoints from credential stuffing and spam using an in-memory Bucket4j rate limiter before the authentication filter. Ensure mutation operations (POST, PUT, PATCH, DELETE) are protected by a centralized idempotency filter to prevent duplicate financial transactions.

#### Idempotency Framework

```sql
-- V004__create_idempotency_keys.sql
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    request_path    VARCHAR(255) NOT NULL,
    request_method  VARCHAR(10) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    response_status INT,
    response_body   JSONB,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP NOT NULL
);
CREATE INDEX idx_idempotency_expires ON idempotency_keys(expires_at);
```

```java
// shared/infrastructure/web/IdempotencyFilter.java
@Component
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {
    
    private final JdbcTemplate jdbcTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String method = request.getMethod();
        if (!Set.of("POST", "PUT", "PATCH", "DELETE").contains(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Idempotency-Key header is required for mutation endpoints.");
            return;
        }

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        String currentHash = calculateHash(requestWrapper);

        // 1. Try to insert key (locks it for this request)
        try {
            jdbcTemplate.update(
                "INSERT INTO idempotency_keys (idempotency_key, request_path, request_method, request_hash, expires_at) " +
                "VALUES (?, ?, ?, ?, NOW() + INTERVAL '24 HOURS')",
                idempotencyKey, request.getRequestURI(), method, currentHash
            );
        } catch (DuplicateKeyException e) {
            // 2. Key exists, fetch previous response or indicate processing
            Map<String, Object> existing = jdbcTemplate.queryForMap(
                "SELECT request_hash, response_status, response_body FROM idempotency_keys WHERE idempotency_key = ?", idempotencyKey
            );
            
            String storedHash = (String) existing.get("request_hash");
            if (!currentHash.equals(storedHash)) {
                response.setStatus(422);
                response.setContentType("application/json");
                response.getWriter().write("{\"errorCode\": \"IDEM_001\", \"message\": \"Request body does not match original request for this idempotency key.\"}");
                return;
            }

            Integer responseStatus = (Integer) existing.get("response_status");
            if (responseStatus == null) {
                response.sendError(HttpStatus.CONFLICT.value(), "Request is currently being processed.");
                return;
            }
            
            if (responseStatus == 500 && "\"FAILED_SERVER_ERROR\"".equals(existing.get("response_body"))) {
                response.setStatus(409);
                response.setContentType("application/json");
                response.getWriter().write("{\"errorCode\": \"IDEM_002\", \"message\": \"Previous request encountered a server error. Manual verification required.\"}");
                return;
            }
            
            response.setStatus(responseStatus);
            response.setContentType("application/json");
            String responseBody = (String) existing.get("response_body");
            if (responseBody != null) {
                response.getWriter().write(responseBody);
            }
            return;
        }

        // 3. Capture response
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        boolean unhandledError = false;
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } catch (Exception ex) {
            unhandledError = true;
            throw ex;
        } finally {
            // 4. Update key with response
            if (unhandledError || responseWrapper.getStatus() >= 500) {
                // Do not remove key on server error; record failure state to prevent unsafe duplicate retries
                jdbcTemplate.update(
                    "UPDATE idempotency_keys SET response_status = ?, response_body = ?::jsonb WHERE idempotency_key = ?",
                    500, "\"FAILED_SERVER_ERROR\"", idempotencyKey
                );
            } else {
                jdbcTemplate.update(
                    "UPDATE idempotency_keys SET response_status = ?, response_body = ?::jsonb WHERE idempotency_key = ?",
                    responseWrapper.getStatus(), new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8), idempotencyKey
                );
            }
            if (!unhandledError) {
                responseWrapper.copyBodyToResponse();
            }
        }
    }

    private String calculateHash(ContentCachingRequestWrapper request) throws IOException {
        byte[] body = request.getContentAsByteArray();
        if (body.length == 0) {
            body = request.getInputStream().readAllBytes();
        }
        
        String payload = request.getMethod() + request.getRequestURI() + new String(body, StandardCharsets.UTF_8);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
```

#### Idempotency Test Verification

```java
// loan/infrastructure/adapter/in/web/LoanControllerIdempotencyTest.java
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoanControllerIdempotencyTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void submitLoan_shouldBeIdempotent_whenSameKeyUsedMultipleTimes() {
        // Arrange
        String idempotencyKey = java.util.UUID.randomUUID().toString();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        String requestJson = """
            { "customerId": "c123", "productId": "p456", "amount": 50000000, "currency": "VND" }
        """;
        HttpEntity<String> request = new HttpEntity<>(requestJson, headers);

        // Act - Submit twice
        ResponseEntity<LoanApplicationDto> firstResponse = restTemplate.postForEntity("/api/v1/loans", request, LoanApplicationDto.class);
        ResponseEntity<LoanApplicationDto> secondResponse = restTemplate.postForEntity("/api/v1/loans", request, LoanApplicationDto.class);

        // Assert
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        
        // Ensure the exact same resource ID was returned, not a duplicate
        assertThat(firstResponse.getBody().id()).isEqualTo(secondResponse.getBody().id());
    }
}
```

#### Rate Limiting

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>0.14.0</version>
</dependency>
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

```java
// shared/infrastructure/security/RateLimitFilter.java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // Before auth filter
public class RateLimitFilter extends OncePerRequestFilter {

    private record RateLimitConfig(int tokens, Duration period) {}

    private static final Map<String, RateLimitConfig> RATE_LIMITS = Map.of(
        "/api/v1/auth/login",    new RateLimitConfig(5, Duration.ofMinutes(1)),
        "/api/v1/auth/register", new RateLimitConfig(3, Duration.ofMinutes(1)),
        "/api/v1/auth/refresh",  new RateLimitConfig(10, Duration.ofMinutes(1)),
        "/api/v1/loans",         new RateLimitConfig(10, Duration.ofMinutes(1)),
        "/api/v1/documents",     new RateLimitConfig(20, Duration.ofMinutes(1))
    );

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
        .expireAfterAccess(2, TimeUnit.MINUTES)
        .maximumSize(50_000)
        .build();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        RateLimitConfig config = RATE_LIMITS.get(path);
        if (config == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractClientIp(request);
        String key = clientIp + ":" + path;

        Bucket bucket = buckets.get(key, k -> {
            Bandwidth bandwidth = Bandwidth.builder()
                .capacity(config.tokens())
                .refillGreedy(config.tokens(), config.period())
                .build();
            return Bucket.builder().addLimit(bandwidth).build();
        });

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            long retryAfter = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.addHeader("Retry-After", String.valueOf(retryAfter));
            response.getWriter().write(
                "{\"status\":\"error\",\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Retry after " + retryAfter + " seconds.\"}");
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }


}
```

### CORS Configuration

**Strategy:** Explicitly configure Allowed Origins via properties to ensure safe cross-origin requests from the React frontend, avoiding insecure wildcards (`*`).

```yaml
# application.yml
app:
  cors:
    allowed-origins: ${CORS_ORIGINS:http://localhost:5173}

# application-prod.yml
app:
  cors:
    allowed-origins: https://meridian-lending.vn
```

```java
// shared/infrastructure/config/CorsConfig.java
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);  // NEVER use "*"
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH"));
        config.setAllowedHeaders(List.of(
            "Authorization", "Content-Type", "Idempotency-Key", "X-Request-ID"));
        config.setExposedHeaders(List.of("X-Rate-Limit-Remaining", "Retry-After"));
        config.setAllowCredentials(true);  // Required for httpOnly cookies
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

```java
// In SecurityConfig.java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http,
        CorsConfigurationSource corsSource) throws Exception {
    return http
        .cors(cors -> cors.configurationSource(corsSource))
        .csrf(csrf -> csrf.disable()) // Stateless JWT — CSRF not needed
        // ... other config
        .build();
}
```

### RBAC Model

```
ADMIN
├── All permissions
│
MANAGER
├── loan:read, loan:review, loan:approve
├── approval:read, approval:submit, approval:override
├── customer:read
├── document:read
├── report:read, report:export
│
LOAN_OFFICER
├── loan:read, loan:create, loan:review
├── approval:read, approval:submit
├── customer:read, customer:create, customer:update
├── document:read, document:upload
│
CUSTOMER
├── loan:read (own), loan:create (own)
├── document:read (own), document:upload (own)
├── customer:read (own), customer:update (own)
```

### Endpoint Authorization Matrix

| Method | Path | Required Permission | Row-Level Filter |
|---|---|---|---|
| POST | /api/v1/auth/login | permitAll | — |
| POST | /api/v1/auth/register | permitAll | — |
| POST | /api/v1/auth/refresh | permitAll (cookie-based) | — |
| POST | /api/v1/auth/logout | authenticated | — |
| GET | /api/v1/loans | loan:read | CUSTOMER: own only |
| GET | /api/v1/loans/{id} | loan:read | CUSTOMER: own only |
| POST | /api/v1/loans | loan:create | — |
| PUT | /api/v1/loans/{id}/review | loan:review | — |
| GET | /api/v1/loan-products | loan:read | — |
| POST | /api/v1/loan-products | loan-product:manage | ADMIN/MANAGER only |
| GET | /api/v1/approvals | approval:read | — |
| POST | /api/v1/approvals/{id}/decision | approval:submit | — |
| GET | /api/v1/customers | customer:read | CUSTOMER: own only |
| GET | /api/v1/customers/{id} | customer:read | CUSTOMER: own only |
| POST | /api/v1/customers | customer:create | — |
| PUT | /api/v1/customers/{id} | customer:update | CUSTOMER: own only |
| POST | /api/v1/documents | document:upload | — |
| GET | /api/v1/documents/{id} | document:read | CUSTOMER: own only |
| GET | /api/v1/audit | audit:read | ADMIN/MANAGER only |

### Authorization Enforcement

```java
// Method-level security
@PreAuthorize("hasPermission(#loanId, 'LoanApplication', 'loan:approve')")
public LoanApplicationDto approveLoan(LoanApplicationId loanId, ...) { ... }

// Row-level security for CUSTOMER role
// Customers can only see their own loans
@Query("SELECT l FROM LoanJpaEntity l WHERE l.customerId = :customerId")
Page<LoanJpaEntity> findByCustomer(@Param("customerId") UUID customerId, Pageable p);
```

### Security and Data Isolation Testing

```java
// loan/infrastructure/adapter/in/web/LoanControllerSecurityTest.java
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoanController.class)
class LoanControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QueryLoanUseCase queryLoanUseCase;

    @Test
    @WithMockUser(username = "customer-1", roles = "CUSTOMER")
    void getLoan_shouldReturn403_whenAccessingAnotherCustomersLoan() throws Exception {
        // Assume Custom PermissionEvaluator throws AccessDenied or evaluates to false
        mockMvc.perform(get("/api/v1/loans/loan-uuid-for-customer-2"))
               .andExpect(status().isForbidden());
    }

    @Test
    void getLoan_shouldReturn401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/loans/any-uuid"))
               .andExpect(status().isUnauthorized());
    }
}
```

### Audit Logging (Security Events)

Every security-sensitive action must produce an immutable audit record. To comply with Vietnamese financial regulations and ensure non-repudiation, the audit trail must be enforced as append-only at the database level.

```sql
-- V005__enforce_audit_immutability.sql
CREATE OR REPLACE FUNCTION prevent_audit_modifications()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Modifying or deleting audit events is strictly prohibited for compliance reasons.';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_audit_update
BEFORE UPDATE OR DELETE ON audit_events
FOR EACH ROW EXECUTE FUNCTION prevent_audit_modifications();
```

```java
@Aspect
@Component
public class SecurityAuditAspect {
    @AfterReturning("@annotation(Audited)")
    public void auditAction(JoinPoint jp) {
        var user = SecurityContextHolder.getContext().getAuthentication();
        auditService.log(AuditEvent.builder()
            .actor(user.getName())
            .action(jp.getSignature().getName())
            .resource(extractResource(jp))
            .timestamp(Instant.now())
            .ipAddress(extractIp())
            .build());
    }
}
```

#### Data Retention and Archival Strategy

To comply with Vietnamese financial regulations requiring 5-10 year retention periods, while maintaining performance on active queries, the `audit_events` table is partitioned by year.

```sql
-- V1_0_3__partition_audit_events.sql
-- Rename existing table and recreate as partitioned
ALTER TABLE audit_events RENAME TO audit_events_old;

CREATE TABLE audit_events (
    id UUID NOT NULL,
    actor VARCHAR(255) NOT NULL,
    action VARCHAR(255) NOT NULL,
    resource VARCHAR(255) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    ip_address VARCHAR(45),
    payload JSONB,
    PRIMARY KEY (id, timestamp) -- Partition key must be part of PK
) PARTITION BY RANGE (timestamp);

-- Create partitions for current and next year
CREATE TABLE audit_events_2026 PARTITION OF audit_events
    FOR VALUES FROM ('2026-01-01 00:00:00') TO ('2027-01-01 00:00:00');

CREATE TABLE audit_events_2027 PARTITION OF audit_events
    FOR VALUES FROM ('2027-01-01 00:00:00') TO ('2028-01-01 00:00:00');
    
-- Insert old data
INSERT INTO audit_events SELECT * FROM audit_events_old;
DROP TABLE audit_events_old;
```

### Secrets Management

| Environment | Strategy |
|---|---|
| Local dev | `.env` file (gitignored) + Docker Compose env vars |
| Staging | Docker secrets or environment variables |
| Production | Cloud provider secrets manager (AWS Secrets Manager / GCP Secret Manager) |

> [!CAUTION]
> **Never commit** JWT signing keys, database passwords, or API keys to version control. Use Spring Boot's `@ConfigurationProperties` with environment variable binding.

### Data Protection

```java
// Encrypt sensitive fields at rest using JPA AttributeConverter
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptionService.encrypt(attribute); // AES-256-GCM
    }
    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptionService.decrypt(dbData);
    }
}

// Usage on entity
@Convert(converter = EncryptedStringConverter.class)
private String nationalId;  // CCCD/CMND encrypted at rest
```

### File Upload Security

**Strategy:** Enforce strict file upload security by validating magic bytes (not just extensions), sanitizing filenames against path traversal, and enforcing file size limits to prevent storage exhaustion.

```yaml
# application.yml
app:
  document:
    max-file-size: 10MB
    allowed-content-types:
      - application/pdf
      - image/jpeg
      - image/png
    storage-base-path: ${DOCUMENT_STORAGE_PATH:./data/documents}

# Spring multipart config
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 15MB
```

```java
// document/application/service/UploadDocumentService.java
@Service
@Transactional
@RequiredArgsConstructor
public class UploadDocumentService implements UploadDocumentUseCase {

    private final DocumentRepository documentRepo;
    private final FileStoragePort storagePort;

    @Value("${app.document.allowed-content-types}")
    private List<String> allowedContentTypes;

    @Override
    public Document upload(UploadDocumentCommand command) {
        // 1. Validate content type by magic bytes, NOT file extension
        String detectedType = detectContentType(command.fileContent());
        if (!allowedContentTypes.contains(detectedType)) {
            throw new DomainException("File type not allowed: " + detectedType
                + ". Allowed: " + allowedContentTypes);
        }

        // 2. Sanitize filename — strip path components, limit length
        String safeFilename = sanitizeFilename(command.originalFilename());

        // 3. Generate storage path with UUID to prevent collisions
        String storagePath = command.customerId().value()
            + "/" + UUID.randomUUID() + "/" + safeFilename;

        // 4. Store file
        StorageReference ref = storagePort.store(command.fileContent(), storagePath, detectedType);

        // 5. Create domain object
        Document doc = Document.create(
            command.customerId(), command.loanId(), command.documentType(),
            safeFilename, ref, command.fileContent().length, detectedType);

        return documentRepo.save(doc);
    }

    private String detectContentType(byte[] content) {
        // PDF: starts with %PDF
        if (content.length >= 4
                && content[0] == 0x25 && content[1] == 0x50
                && content[2] == 0x44 && content[3] == 0x46) {
            return "application/pdf";
        }
        // JPEG: starts with FF D8 FF
        if (content.length >= 3
                && (content[0] & 0xFF) == 0xFF
                && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // PNG: starts with 89 50 4E 47
        if (content.length >= 4
                && (content[0] & 0xFF) == 0x89
                && content[1] == 0x50 && content[2] == 0x4E && content[3] == 0x47) {
            return "image/png";
        }
        return "application/octet-stream"; // Unknown — will be rejected
    }

    private String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "unnamed";
        }
        // Strip path separators
        String name = original.replace("\\", "/");
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) name = name.substring(lastSlash + 1);

        // Remove non-safe characters (allow Vietnamese Unicode + alphanumeric + dot + dash)
        name = name.replaceAll("[^\\p{L}\\p{N}.\\-_]", "_");

        // Limit length
        if (name.length() > 100) name = name.substring(0, 100);

        // Prevent hidden files
        if (name.startsWith(".")) name = "_" + name;

        return name;
    }
}
```

---

## Part 3: Observability Architecture

### Stack Recommendation

| Layer | Tool | Phase |
|---|---|---|
| **Logging** | SLF4J + Logback (JSON format) | Phase 1 |
| **Metrics** | Micrometer + Prometheus | Phase 3 |
| **Tracing** | OpenTelemetry + Jaeger (or Zipkin) | Phase 3 |
| **Health** | Spring Boot Actuator | Phase 1 |
| **Dashboards** | Grafana | Phase 3 |

### Structured Logging (Phase 1)

```java
// shared/infrastructure/logging/PiiMaskingConverter.java
// Custom Logback converter that masks PII patterns in log messages
public class PiiMaskingConverter extends ClassicConverter {

    // Patterns for Vietnamese PII
    private static final Pattern NATIONAL_ID = Pattern.compile("\\b(\\d{3})\\d{6}(\\d{3})?\\b");
    private static final Pattern PHONE = Pattern.compile("(0|\\+84)(\\d{2})\\d{5}(\\d{2})");
    private static final Pattern EMAIL = Pattern.compile("([^@\\s]{2})[^@\\s]*(@[^\\s]+)");

    @Override
    public String convert(ILoggingEvent event) {
        String msg = event.getFormattedMessage();
        msg = NATIONAL_ID.matcher(msg).replaceAll("$1***$2");
        msg = PHONE.matcher(msg).replaceAll("$1$2*****$3");
        msg = EMAIL.matcher(msg).replaceAll("$1***$2");
        return msg;
    }
}
```

```java
// shared/infrastructure/logging/PiiValueMasker.java
// Masks PII in structured JSON log values
public class PiiValueMasker implements ValueMasker {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
        "nationalId", "national_id", "cccd", "cmnd",
        "phoneNumber", "phone_number", "phone",
        "email", "emailAddress",
        "fullName", "full_name", "name",
        "accountNumber", "account_number",
        "password", "passwordHash"
    );

    @Override
    public Object mask(JsonStreamContext context, Object value) {
        if (context.hasCurrentName() && SENSITIVE_KEYS.contains(context.getCurrentName())) {
            if (value instanceof CharSequence s && s.length() > 4) {
                return s.subSequence(0, 2) + "***" + s.subSequence(s.length() - 2, s.length());
            }
            return "***";
        }
        return null; // null = no masking
    }
}
```

```xml
<!-- logback-spring.xml -->
<configuration>
    <!-- Register custom PII masking converter -->
    <conversionRule conversionWord="maskedMsg"
                    converterClass="com.lending.platform.shared.infrastructure.logging.PiiMaskingConverter"/>

    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>loanId</includeMdcKeyName>
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>requestId</includeMdcKeyName>
            <!-- Use masking on message field -->
            <jsonGeneratorDecorator class="net.logstash.logback.mask.MaskingJsonGeneratorDecorator">
                <valueMasker class="com.lending.platform.shared.infrastructure.logging.PiiValueMasker"/>
            </jsonGeneratorDecorator>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>
</configuration>
```

```java
// shared/infrastructure/logging/MdcLoggingFilter.java
package com.lending.platform.shared.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String traceId = request.getHeader(TRACE_ID_HEADER);
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString();
            }
            
            MDC.put(REQUEST_ID_KEY, UUID.randomUUID().toString());
            MDC.put(TRACE_ID_KEY, traceId);
            
            // Add traceId to response for client tracking
            response.setHeader(TRACE_ID_HEADER, traceId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

```java
// shared/infrastructure/config/AsyncConfig.java
package com.lending.platform.shared.infrastructure.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.Map;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "applicationTaskExecutor")
    public Executor taskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("Async-");
        executor.setVirtualThreads(true);
        executor.setTaskDecorator(new MdcTaskDecorator());
        return executor;
    }

    // Ensures MDC context propagates from web thread to async thread
    public static class MdcTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        }
    }
}
```

```java
// Manual business context added within the service layer
MDC.put("userId", authenticatedUser.getId());
log.info("Loan application submitted", kv("loanId", loanId), kv("amount", amount));
// Output: {"message":"Loan application submitted","loanId":"...","amount":5000000,"userId":"...","requestId":"...","traceId":"..."}
```
### Log Level & Slow Query Detection (Phase 1)

```yaml
# application.yml
logging:
  level:
    root: ${LOG_LEVEL_ROOT:INFO}
    com.lending.platform: ${LOG_LEVEL_APP:INFO}
    org.springframework.security: ${LOG_LEVEL_SECURITY:INFO}
    # Log slow queries via Hibernate (requires hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS)
    org.hibernate.SQL_SLOW: INFO

spring:
  jpa:
    properties:
      hibernate:
        session:
          events:
            log:
              LOG_QUERIES_SLOWER_THAN_MS: ${SLOW_QUERY_THRESHOLD_MS:500}
```

### Key Metrics (Phase 3)

```java
// Custom business metrics
@Component
public class LoanMetrics {
    private final Counter applicationsSubmitted;
    private final Counter applicationsApproved;
    private final Timer approvalLatency;

    public LoanMetrics(MeterRegistry registry) {
        this.applicationsSubmitted = Counter.builder("loans.submitted.total").register(registry);
        this.applicationsApproved = Counter.builder("loans.approved.total").register(registry);
        this.approvalLatency = Timer.builder("loans.approval.duration").register(registry);
    }
}
```
### Graceful Shutdown (Phase 1)

```yaml
# application.yml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s # Give virtual threads time to complete in-flight transactions
```
### Startup Verification (Phase 1)

```java
// shared/infrastructure/config/StartupVerifier.java
package com.lending.platform.shared.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
public class StartupVerifier implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupVerifier.class);

    private final DataSource dataSource;
    
    @Value("${jwt.privateKeyPath}")
    private Resource privateKey;
    
    @Value("${jwt.publicKeyPath}")
    private Resource publicKey;

    public StartupVerifier(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("Running environmental startup verification...");

        // 1. Verify Database Connectivity
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(2)) {
                throw new IllegalStateException("Database connection is not valid");
            }
            log.info("Database connectivity verified.");
        } catch (Exception e) {
            log.error("Failed to connect to database on startup.", e);
            System.exit(1);
        }

        // 2. Verify JWT Keys
        if (!privateKey.exists() || !publicKey.exists()) {
            log.error("CRITICAL: JWT RSA keys are missing. Application cannot sign or verify tokens.");
            System.exit(1);
        }
        
        log.info("Startup verification completed successfully. Application is ready to serve traffic.");
    }
}
```

### Health Checks (Phase 1)

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  endpoint:
    health:
      show-details: when-authorized
  health:
    db:
      enabled: true
    diskspace:
      enabled: true
```

```java
// Custom health indicator for OCR service (Phase 2)
@Component
public class OcrServiceHealthIndicator extends AbstractHealthIndicator {
    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            var response = ocrClient.healthCheck();
            builder.up().withDetail("ocrService", response.status());
        } catch (Exception e) {
            builder.down().withException(e);
        }
    }
}
```

```java
// document/infrastructure/health/FileStorageHealthIndicator.java
package com.lending.platform.document.infrastructure.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class FileStorageHealthIndicator implements HealthIndicator {

    @Value("${app.document.storage-base-path:./data/documents}")
    private String storagePath;

    @Override
    public Health health() {
        try {
            Path path = Paths.get(storagePath);
            if (!Files.exists(path)) {
                return Health.down().withDetail("error", "Storage path does not exist: " + storagePath).build();
            }
            if (!Files.isWritable(path)) {
                return Health.down().withDetail("error", "Storage path is not writable: " + storagePath).build();
            }
            
            long freeSpace = Files.getFileStore(path).getUsableSpace();
            long threshold = 100 * 1024 * 1024; // 100 MB minimum
            
            if (freeSpace < threshold) {
                return Health.down()
                    .withDetail("error", "Low disk space")
                    .withDetail("freeSpaceBytes", freeSpace)
                    .build();
            }

            return Health.up()
                .withDetail("path", storagePath)
                .withDetail("freeSpaceBytes", freeSpace)
                .build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
```

### Docker Compose (Phase 1)

```yaml
# docker-compose.yml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: ${DB_USER:-meridian}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-secret}
      POSTGRES_DB: ${DB_NAME:-meridian_db}
    volumes:
      - pgdata:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER:-meridian} -d ${DB_NAME:-meridian_db}"]
      interval: 10s
      timeout: 5s
      retries: 5
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: postgres
      DB_PORT: 5432
      DB_USER: ${DB_USER:-meridian}
      DB_PASSWORD: ${DB_PASSWORD:-secret}
      LOG_LEVEL_ROOT: WARN
      LOG_LEVEL_APP: INFO
    depends_on:
      postgres:
        condition: service_healthy
    volumes:
      - document_data:/app/data/documents
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 2G

  ocr-service:
    image: meridian/ocr-service:latest
    environment:
      - OCR_API_KEY=${OCR_API_KEY:-default-secret-change-me}
      - DB_HOST=postgres
      - DB_PORT=5432
      - DB_USER=${DB_USER:-meridian}
      - DB_PASSWORD=${DB_PASSWORD:-secret}
      - DB_NAME=${DB_NAME:-meridian_db}
    volumes:
      - document_data:/app/data/documents:ro # :ro ensures read-only access
    depends_on:
      postgres:
        condition: service_healthy
    restart: unless-stopped
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 4G # OCR typically requires more memory

volumes:
  pgdata:
  document_data:
```

---

## Part 4: API Design & Operations

### API Versioning Strategy

We use URI versioning (`/api/v1/`). When introducing v2:
1. Create a new controller `LoanControllerV2`.
2. Do not modify the existing `LoanController` (v1).
3. If the domain model changes, use an Adapter in the v1 controller to map the new domain model back to the old v1 DTO.
4. Mark v1 endpoints as `@Deprecated` and add a `Sunset` HTTP header to notify clients.
