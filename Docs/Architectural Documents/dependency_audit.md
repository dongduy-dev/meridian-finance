# Dependency Compatibility Audit — Java 25 + Spring Boot 4.0.x

**Audit Date:** June 10, 2026  
**Target Stack:** Java 25 (LTS) + Spring Boot 4.0.6

---

## Audit Summary

| Status | Count |
|---|---|
| ✅ Fully Compatible | 13 |
| ⚠️ Compatible with Gotchas | 1 |
| ❌ Incompatible | 0 |


---

## Full Dependency Matrix

| # | Dependency | Version | Java 25 | Spring Boot 4 | Status | Notes |
|---|---|---|---|---|---|---|
| 1 | **Spring Modulith** | 2.0.6 | ✅ | ✅ | ✅ | Use 2.0.x line — designed for Boot 4 |
| 1b | **Spring Modulith Events JDBC** | 2.0.6 (managed) | ✅ | ✅ | ✅ | Add alongside `spring-modulith-bom`; provides transactional outbox via `event_publication` table |
| 2 | **Spring Security** | 7.x (managed) | ✅ | ✅ | ✅ | Bundled with Boot 4, no manual version needed |
| 3 | **Spring Data JPA** | 4.x (managed) | ✅ | ✅ | ✅ | Bundled with Boot 4 |
| 4 | **Hibernate** | 7.x (managed) | ✅ | ✅ | ✅ | Managed by Spring Boot BOM |
| 5 | **Flyway** | 11.x | ✅ | ⚠️ | ⚠️ | See below |
| 6 | **PostgreSQL Driver** | 42.7.x (managed) | ✅ | ✅ | ✅ | Managed by Spring Boot BOM |
| 7 | **ArchUnit** | 1.4.2 | ✅ | ✅ | ✅ | Bytecode analyzer — version agnostic to Spring |
| 8 | **JJWT** | 0.13.0 | ✅ | ✅ | ✅ | Works with Jackson 3 (Boot 4 default) |
| 9 | **Lombok** | 1.18.40+ | ✅ | ✅ | ✅ | Must be ≥1.18.40 for Java 25 |
| 10 | **MapStruct** | 1.7.x | ✅ | ✅ | ✅ | Full Jakarta EE 11 support |
| 11 | **Bucket4j** | 0.14+ | ✅ | ✅ | ✅ | Rate limiting starter updated for Boot 4 |
| 12 | **Micrometer** | 1.15.x (managed) | ✅ | ✅ | ✅ | Managed by Spring Boot BOM |
| 13 | **OpenTelemetry** | via Boot starter | ✅ | ✅ | ✅ | `spring-boot-starter-opentelemetry` available |
| 14 | **Jackson** | 3.x (managed) | ✅ | ✅ | ✅ | Boot 4 defaults to Jackson 3 |

---

## Flyway Configuration Change in Spring Boot 4

> In Spring Boot 4, Flyway is **no longer auto-configured** by just adding `flyway-core`. Must use the new starter.

**Before (Spring Boot 3.x):**
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

**After (Spring Boot 4.0.x):**
```xml
<!-- Required: Spring Boot Flyway Starter -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>
<!-- Required: PostgreSQL-specific Flyway module -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

If only `flyway-core` added, migrations will silently not run. This is the #1 migration pitfall for Boot 4.

---

## Lombok: Consider Phasing Out

> Java 25 now provides Records, Sealed Classes, and Pattern Matching. For **new code**, prefer Java Records for DTOs/VOs over Lombok `@Data`. Keep Lombok for entities and builders where Records don't fit. This reduces annotation processor dependency chain.

```java
// Prefer: Java Record for DTOs (no Lombok needed)
public record CreateLoanCommand(
    UUID customerId,
    UUID productId,
    BigDecimal amount,
    String currency,
    String idempotencyKey
) {}

// Keep Lombok for: JPA entities, builders
@Entity @Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class LoanJpaEntity { ... }
```

---

## Recommended Maven BOM Configuration

```xml
<properties>
    <java.version>25</java.version>
    <spring-modulith.version>2.0.6</spring-modulith.version>
    <archunit.version>1.4.2</archunit.version>
    <jjwt.version>0.13.0</jjwt.version>
    <mapstruct.version>1.7.0</mapstruct.version>
    <lombok.version>1.18.44</lombok.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- Spring Modulith BOM -->
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-bom</artifactId>
            <version>${spring-modulith.version}</version>
            <scope>import</scope>
            <type>pom</type>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## Annotation Processor Order (Critical)

When using both Lombok and MapStruct, **order matters** in `maven-compiler-plugin`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <!-- Lombok MUST come before MapStruct -->
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>${lombok.version}</version>
            </path>
            <path>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>${mapstruct.version}</version>
            </path>
            <!-- Lombok-MapStruct binding -->
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok-mapstruct-binding</artifactId>
                <version>0.2.0</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

---

## Spring Boot 4 Testing Change

> `@MockBean` and `@SpyBean` are **deprecated** in Spring Boot 4. Use the new Mockito-based annotations instead.

```java
// Deprecated (Spring Boot 3.x)
@MockBean
private LoanRepository loanRepository;

// Spring Boot 4
@MockitoBean
private LoanRepository loanRepository;

@MockitoSpyBean
private LoanEventPublisher eventPublisher;
```

---

## Final Verdict

**No blockers.** Java 25 + Spring Boot 4.0.x is fully compatible with every dependency in the Meridian architecture. The only action item is using `spring-boot-starter-flyway` instead of raw `flyway-core`.
