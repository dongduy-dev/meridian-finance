# Module Implementation Examples
## Code Examples — Shared Kernel

### Domain Layer: Value Objects (CCCD/CMND Validation)

```java
// shared/domain/model/NationalId.java
public record NationalId(String value) {
    private static final Pattern CMND_PATTERN = Pattern.compile("^\\d{9}$");
    private static final Pattern CCCD_PATTERN = Pattern.compile("^\\d{12}$");

    public NationalId {
        if (value == null || value.isBlank()) {
            throw new DomainException("National ID cannot be empty");
        }
        
        if (!CMND_PATTERN.matcher(value).matches() && !CCCD_PATTERN.matcher(value).matches()) {
            throw new DomainException("National ID must be exactly 9 digits (CMND) or 12 digits (CCCD)");
        }

        if (value.length() == 12) {
            validateCccdStructure(value);
        }
    }

    private void validateCccdStructure(String cccd) {
        String provinceCode = cccd.substring(0, 3);
        int centuryGenderCode = Character.getNumericValue(cccd.charAt(3));
        
        int code = Integer.parseInt(provinceCode);
        if (code < 1 || code > 96) {
            throw new DomainException("Invalid CCCD province code: " + provinceCode);
        }
        if (centuryGenderCode < 0 || centuryGenderCode > 9) {
            throw new DomainException("Invalid CCCD century/gender code");
        }
    }
}
```

---

## Code Examples — Identity Module

### Domain Layer: Output Port

```java
// identity/domain/port/out/RefreshTokenRepository.java
public interface RefreshTokenRepository {
    void save(RefreshToken token);
    Optional<RefreshToken> findByHash(String tokenHash);
    void deleteByHash(String tokenHash);
    void deleteAllByUserId(UserId userId);
}
```

### Infrastructure Layer: JPA Adapter

```java
// identity/infrastructure/adapter/out/persistence/RefreshTokenJpaEntity.java
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenJpaEntity extends BaseJpaEntity {
    
    @Column(name = "user_id_hash", nullable = false)
    private String userIdHash;
    
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;
    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    
    // Getters, Setters, NoArgsConstructor omitted for brevity
}

// identity/infrastructure/adapter/out/persistence/JpaRefreshTokenRepository.java
@Repository
@RequiredArgsConstructor
public class JpaRefreshTokenRepository implements RefreshTokenRepository {
    
    private final SpringDataRefreshTokenRepository jpaRepo;
    private final RefreshTokenPersistenceMapper mapper;

    @Override
    public void save(RefreshToken token) {
        jpaRepo.save(mapper.toJpaEntity(token));
    }

    @Override
    public Optional<RefreshToken> findByHash(String tokenHash) {
        return jpaRepo.findByTokenHash(tokenHash)
                .map(mapper::toDomain);
    }

    @Override
    public void deleteByHash(String tokenHash) {
        jpaRepo.deleteByTokenHash(tokenHash);
    }

    @Override
    public void deleteAllByUserId(UserId userId) {
        // Assume mapper handles hashing or we query by user id hash
        jpaRepo.deleteAllByUserIdHash(userId.value().toString());
    }
}

interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshTokenJpaEntity, UUID> {
    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);
    void deleteByTokenHash(String tokenHash);
    void deleteAllByUserIdHash(String userIdHash);
}
```

---

## Code Examples — Customer Module (PDPA Compliance)

### Domain Layer: Consent and Anonymization

```java
// customer/domain/model/Consent.java
public record Consent(
    boolean termsAccepted,
    boolean dataProcessingAccepted,
    Instant consentedAt,
    String ipAddress
) {
    public Consent {
        if (!termsAccepted || !dataProcessingAccepted) {
            throw new DomainException("Explicit consent for terms and data processing is required under Decree 13");
        }
        Objects.requireNonNull(consentedAt);
        Objects.requireNonNull(ipAddress);
    }
}

// customer/domain/model/Customer.java
public class Customer {
    private CustomerId id;
    private PersonalInfo personalInfo;
    private EmploymentInfo employmentInfo;
    private NationalId nationalId;
    private PhoneNumber phoneNumber;
    private EmailAddress emailAddress;
    private Consent pdpaConsent;
    private boolean anonymized;

    // Called when a user invokes their "Right to Delete" under PDPA (Decree 13/2023/ND-CP)
    public void anonymize() {
        this.personalInfo = PersonalInfo.anonymized();
        this.employmentInfo = EmploymentInfo.anonymized();
        this.nationalId = NationalId.anonymized();
        this.phoneNumber = PhoneNumber.anonymized();
        this.emailAddress = EmailAddress.anonymized();
        this.anonymized = true;
    }
    
    public CustomerId getId() { return id; }
    public PersonalInfo getPersonalInfo() { return personalInfo; }
    public EmploymentInfo getEmploymentInfo() { return employmentInfo; }
    public NationalId getNationalId() { return nationalId; }
    public PhoneNumber getPhoneNumber() { return phoneNumber; }
    public EmailAddress getEmailAddress() { return emailAddress; }
    public Consent getPdpaConsent() { return pdpaConsent; }
    public boolean isAnonymized() { return anonymized; }
}
```

---

## Code Examples — Loan Module

### Domain Layer: Rich Entity

```java
// loan/domain/model/LoanApplication.java
public class LoanApplication {
    private LoanApplicationId id;
    private CustomerId customerId;
    private LoanProductId productId;
    private Money requestedAmount;
    private Money approvedAmount;
    private LoanStatus status;
    private List<StatusTransition> statusHistory;
    private int version; // Optimistic locking for concurrent state change protection

    // ── Valid transition map (defined once, enforced everywhere) ──
    private static final Map<LoanStatus, Set<LoanStatus>> VALID_TRANSITIONS = Map.of(
        LoanStatus.DRAFT,             Set.of(LoanStatus.SUBMITTED, LoanStatus.CANCELLED),
        LoanStatus.SUBMITTED,         Set.of(LoanStatus.UNDER_REVIEW, LoanStatus.CANCELLED),
        LoanStatus.UNDER_REVIEW,      Set.of(LoanStatus.PENDING_APPROVAL, LoanStatus.REJECTED),
        LoanStatus.PENDING_APPROVAL,  Set.of(LoanStatus.APPROVED, LoanStatus.REJECTED),
        LoanStatus.APPROVED,          Set.of(LoanStatus.DISBURSED, LoanStatus.CANCELLED),
        LoanStatus.DISBURSED,         Set.of(LoanStatus.COMPLETED),
        // Terminal states — no transitions out
        LoanStatus.COMPLETED,         Set.of(),
        LoanStatus.REJECTED,          Set.of(),
        LoanStatus.CANCELLED,         Set.of()
    );

    public static LoanApplication create(CustomerId customerId, LoanProductId productId, Money amount) {
        if (amount == null || !amount.isPositive()) {
            throw new DomainException("Requested amount must be positive");
        }
        var app = new LoanApplication();
        app.id = LoanApplicationId.generate();
        app.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        app.productId = Objects.requireNonNull(productId, "productId must not be null");
        app.requestedAmount = amount;
        app.status = LoanStatus.DRAFT;
        app.statusHistory = new ArrayList<>();
        app.version = 0;
        return app;
    }

    public void submit() {
        transitionTo(LoanStatus.SUBMITTED);
    }

    public void startReview(UserId reviewer) {
        Objects.requireNonNull(reviewer, "reviewer must not be null");
        transitionTo(LoanStatus.UNDER_REVIEW);
    }

    public void sendForApproval() {
        transitionTo(LoanStatus.PENDING_APPROVAL);
    }

    public void approve(Money approvedAmount, UserId approver) {
        Objects.requireNonNull(approvedAmount, "approvedAmount must not be null");
        Objects.requireNonNull(approver, "approver must not be null");
        if (approvedAmount.isGreaterThan(this.requestedAmount)) {
            throw new DomainException("Approved amount cannot exceed requested amount");
        }
        if (!approvedAmount.isPositive()) {
            throw new DomainException("Approved amount must be positive");
        }
        this.approvedAmount = approvedAmount;
        transitionTo(LoanStatus.APPROVED);
    }

    public void reject(UserId rejector, String reason) {
        Objects.requireNonNull(rejector, "rejector must not be null");
        if (reason == null || reason.isBlank()) {
            throw new DomainException("Rejection reason is required");
        }
        transitionTo(LoanStatus.REJECTED);
    }

    public void disburse() {
        if (this.approvedAmount == null) {
            throw new DomainException("Cannot disburse without approved amount");
        }
        transitionTo(LoanStatus.DISBURSED);
    }

    public void complete() {
        transitionTo(LoanStatus.COMPLETED);
    }

    public void cancel(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new DomainException("Cancellation reason is required");
        }
        transitionTo(LoanStatus.CANCELLED);
    }

    private void transitionTo(LoanStatus target) {
        Set<LoanStatus> allowed = VALID_TRANSITIONS.getOrDefault(this.status, Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidStateTransitionException(this.status, target);
        }
        LoanStatus previous = this.status;
        this.status = target;
        this.statusHistory.add(StatusTransition.of(previous, target, Instant.now()));
    }

    // Getters (no setters — state changes only through business methods)
    public LoanApplicationId getId() { return id; }
    public CustomerId getCustomerId() { return customerId; }
    public LoanProductId getProductId() { return productId; }
    public Money getRequestedAmount() { return requestedAmount; }
    public Money getApprovedAmount() { return approvedAmount; }
    public LoanStatus getStatus() { return status; }
    public List<StatusTransition> getStatusHistory() { return Collections.unmodifiableList(statusHistory); }
    public boolean isTerminal() { return VALID_TRANSITIONS.getOrDefault(status, Set.of()).isEmpty(); }
}
```

### Domain Layer: Value Objects & Domain Services (SBV Compliance)

```java
// loan/domain/model/InterestRate.java
public record InterestRate(BigDecimal annualRate) {
    private static final BigDecimal SBV_MAX_RATE = new BigDecimal("0.20"); // 20% per year

    public InterestRate {
        if (annualRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new DomainException("Interest rate cannot be negative");
        }
        if (annualRate.compareTo(SBV_MAX_RATE) > 0) {
            throw new DomainException("Interest rate exceeds SBV regulatory maximum of 20% per annum");
        }
    }
}

// loan/domain/service/EirCalculationService.java
public class EirCalculationService {
    /**
     * Calculates the Effective Interest Rate (EIR) according to SBV Circular 39/2016/TT-NHNN.
     */
    public BigDecimal calculateEffectiveInterestRate(Money principal, InterestRate nominalRate, Money upfrontFees, int termMonths) {
        BigDecimal netDisbursed = principal.amount().subtract(upfrontFees.amount());
        if (netDisbursed.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        
        // Simplified IRR calculation for Circular 39 approximation
        BigDecimal monthlyRate = nominalRate.annualRate().divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        BigDecimal totalInterest = principal.amount().multiply(monthlyRate).multiply(BigDecimal.valueOf(termMonths));
        BigDecimal totalRepayment = principal.amount().add(totalInterest);
        
        BigDecimal eir = totalRepayment.subtract(netDisbursed)
                .divide(netDisbursed, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(12))
                .divide(BigDecimal.valueOf(termMonths), 6, RoundingMode.HALF_UP);
                
        return eir.setScale(4, RoundingMode.HALF_UP);
    }
}
```

### Domain Layer: Pure Unit Testing

```java
// loan/domain/model/LoanApplicationTest.java
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LoanApplicationTest {

    @Test
    void submit_shouldTransitionToSubmitted_whenInDraftState() {
        // Arrange
        LoanApplication loan = LoanApplication.create(
            CustomerId.generate(), LoanProductId.generate(), Money.vnd(50_000_000)
        );
        
        // Act
        loan.submit();
        
        // Assert
        assertThat(loan.getStatus()).isEqualTo(LoanStatus.SUBMITTED);
        assertThat(loan.getStatusHistory()).hasSize(1);
        assertThat(loan.getStatusHistory().get(0).toStatus()).isEqualTo(LoanStatus.SUBMITTED);
    }

    @Test
    void disburse_shouldThrowException_whenNotInApprovedState() {
        // Arrange
        LoanApplication loan = LoanApplication.create(
            CustomerId.generate(), LoanProductId.generate(), Money.vnd(50_000_000)
        );
        loan.submit(); // Now in SUBMITTED state
        
        // Act & Assert
        assertThatThrownBy(loan::disburse)
            .isInstanceOf(InvalidStateTransitionException.class)
            .hasMessageContaining("Cannot transition from SUBMITTED to DISBURSED");
    }
}
```

### Domain Layer: Port (Use Case Interface)

> [!IMPORTANT]
> Use case ports live in the domain layer. They MUST return application-layer DTOs (not domain objects) and accept domain commands. DTO mapping happens in the application service. Controllers must never import domain entities.

```java
// loan/domain/port/in/SubmitLoanUseCase.java
public interface SubmitLoanUseCase {
    LoanApplicationDto submit(SubmitLoanCommand command);
}

// loan/domain/port/in/command/SubmitLoanCommand.java — Pure Java record in domain layer
public record SubmitLoanCommand(
    CustomerId customerId,
    LoanProductId productId,
    Money requestedAmount,
    String idempotencyKey
) {}
```

### Domain Layer: Output Port

```java
// loan/domain/port/out/LoanRepository.java
public interface LoanRepository {
    LoanApplication save(LoanApplication application);
    Optional<LoanApplication> findById(LoanApplicationId id);
    Page<LoanApplication> findByCustomer(CustomerId customerId, Pageable pageable);
}
```

### Application Layer: Use Case Implementation

```java
// loan/application/service/SubmitLoanService.java
@Service
@Transactional
@RequiredArgsConstructor
public class SubmitLoanService implements SubmitLoanUseCase {

    private final LoanRepository loanRepository;
    private final CustomerQueryPort customerPort;
    private final LoanEventPublisher eventPublisher;
    private final IdempotencyService idempotencyService;
    private final LoanMapper loanMapper;

    @Override
    public LoanApplicationDto submit(SubmitLoanCommand command) {
        // Idempotency check
        return idempotencyService.executeIdempotent(
            command.idempotencyKey(),
            () -> {
                // Verify customer eligibility
                customerPort.findById(command.customerId())
                    .orElseThrow(() -> new EntityNotFoundException("Customer", command.customerId()));

                // Create domain object — command already uses domain value objects
                var application = LoanApplication.create(
                    command.customerId(),
                    command.productId(),
                    command.requestedAmount()
                );
                application.submit();

                // Persist
                var saved = loanRepository.save(application);

                // Publish domain event (carries sufficient context for Approval module)
                eventPublisher.publish(new LoanSubmittedEvent(
                    saved.getId(), saved.getCustomerId(),
                    saved.getProductId(), saved.getRequestedAmount(),
                    Instant.now()));

                // Map to DTO before returning
                return loanMapper.toDto(saved);
            }
        );
    }
}
```

### Application Layer: Port Mock Isolation

```java
// loan/application/service/SubmitLoanServiceTest.java
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.Optional;
import java.util.function.Supplier;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@SpringJUnitConfig(classes = {SubmitLoanService.class})
class SubmitLoanServiceTest {

    @Autowired
    private SubmitLoanService submitLoanService;

    @MockitoBean
    private LoanRepository loanRepository;

    @MockitoBean
    private CustomerQueryPort customerQueryPort;

    @MockitoBean
    private LoanEventPublisher eventPublisher;

    @MockitoBean
    private IdempotencyService idempotencyService;

    @Test
    void submit_shouldSaveAndPublishEvent() {
        // Arrange
        var command = new SubmitLoanCommand(CustomerId.generate(), LoanProductId.generate(), Money.vnd(10_000_000), "idemp-key-1");
        when(customerQueryPort.findById(command.customerId())).thenReturn(Optional.of(new CustomerSummaryDto()));
        when(loanRepository.save(any(LoanApplication.class))).thenAnswer(i -> i.getArgument(0));
        when(idempotencyService.executeIdempotent(eq("idemp-key-1"), any())).thenAnswer(i -> {
            Supplier<LoanApplicationDto> supplier = i.getArgument(1);
            return supplier.get();
        });

        // Act
        LoanApplicationDto result = submitLoanService.submit(command);

        // Assert
        assertThat(result.status()).isEqualTo(LoanStatus.SUBMITTED.name());
        verify(loanRepository).save(any());
        verify(eventPublisher).publish(any(LoanSubmittedEvent.class));
    }
}
```

### Application Event Publisher Configuration (Atomicity)

To guarantee that domain events (like `LoanSubmittedEvent`) are not lost if the JVM crashes after the database commits but before the event is dispatched across modules, Spring Modulith's JDBC Event Publication Registry must be enabled. This persists the event in the exact same database transaction as the domain entity update.

```yaml
# application.yml
spring:
  modulith:
    events:
      jdbc:
        schema-initialization:
          enabled: true
```

*Note: Ensure the `spring-modulith-events-jdbc` dependency is included in `pom.xml`.*

### Infrastructure Layer: REST Controller

```java
// loan/infrastructure/adapter/in/web/LoanController.java
@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
public class LoanController {

    private final SubmitLoanUseCase submitLoanUseCase;
    private final ReviewLoanUseCase reviewLoanUseCase;
    private final QueryLoanUseCase queryLoanUseCase;
    private final LoanMapper loanMapper;

    @GetMapping
    @PreAuthorize("hasPermission(null, 'LoanApplication', 'loan:read')")
    public ApiResponse<PaginatedResponse<LoanSummaryDto>> listLoans(
            @AuthenticationPrincipal AuthenticatedUser user,
            Pageable pageable) {
        // Row-level: customers see only their own loans
        if (user.hasRole("CUSTOMER")) {
            return ApiResponse.success(PaginatedResponse.from(
                queryLoanUseCase.findByCustomer(user.getCustomerId(), pageable)));
        }
        return ApiResponse.success(PaginatedResponse.from(
            queryLoanUseCase.findAll(pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(#id, 'LoanApplication', 'loan:read')")
    public ApiResponse<LoanApplicationDto> getLoan(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        LoanApplicationDto loanDto = queryLoanUseCase.findById(LoanApplicationId.of(id));
        // Row-level enforcement
        if (user.hasRole("CUSTOMER") && !loanDto.customerId().equals(user.getCustomerId().value())) {
            throw new AccessDeniedException("Cannot access another customer's loan");
        }
        return ApiResponse.success(loanDto);
    }

    @Operation(summary = "Submit a new loan application", 
               security = { @SecurityRequirement(name = "bearer-key") })
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Loan submitted"),
        @ApiResponse(responseCode = "400", description = "Missing Idempotency-Key or validation error")
    })
    @Parameters({
        @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true, 
                   description = "Unique key to prevent duplicate submissions", example = "uuid-1234")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasPermission(null, 'LoanApplication', 'loan:create')")
    public ApiResponse<LoanApplicationDto> submitLoan(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateLoanRequest request) {
        var command = new SubmitLoanCommand(
            CustomerId.of(request.customerId()),
            LoanProductId.of(request.productId()),
            Money.of(request.amount(), request.currency()),
            idempotencyKey);
        LoanApplicationDto result = submitLoanUseCase.submit(command);
        return ApiResponse.success(result);
    }
}
```

### Infrastructure Layer: JPA Adapter

```java
// loan/infrastructure/adapter/out/persistence/SpringDataLoanRepository.java
public interface SpringDataLoanRepository extends JpaRepository<LoanJpaEntity, UUID> {
    
    @EntityGraph(attributePaths = {"statusHistory"})
    Page<LoanJpaEntity> findByCustomerId(UUID customerId, Pageable pageable);
    
    @EntityGraph(attributePaths = {"statusHistory"})
    Page<LoanJpaEntity> findAll(Pageable pageable);
}

// loan/infrastructure/adapter/out/persistence/JpaLoanRepository.java
@Repository
@RequiredArgsConstructor
public class JpaLoanRepository implements LoanRepository {

    private final SpringDataLoanRepository jpaRepo;
    private final LoanPersistenceMapper mapper;

    @Override
    public LoanApplication save(LoanApplication application) {
        var entity = mapper.toJpaEntity(application);
        var saved = jpaRepo.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<LoanApplication> findById(LoanApplicationId id) {
        return jpaRepo.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public Page<LoanApplication> findByCustomer(CustomerId customerId, Pageable pageable) {
        return jpaRepo.findByCustomerId(customerId.value(), pageable)
            .map(mapper::toDomain);
    }
}
```

### Spring Modulith Verification Test

```java
// test/ModulithStructureTest.java
class ModulithStructureTest {
    static ApplicationModules modules =
        ApplicationModules.of(LendingPlatformApplication.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }

    @Test
    void documentsModules() {
        new Documenter(modules).writeDocumentation();
    }
}
```

### Integration Testing: Testcontainers Configuration

```java
// shared/infrastructure/persistence/PostgresTestContainerConfiguration.java
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainerConfiguration {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("meridian_test")
        .withUsername("test")
        .withPassword("test");

    static {
        postgresContainer.start();
    }
}

// In repository tests:
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestContainerConfiguration.class)
class JpaLoanRepositoryTest {
    // This will test against the real PostgreSQL container and verify Flyway migrations
}

// loan/infrastructure/adapter/out/persistence/LoanJpaEntity.java
@Entity
@Table(name = "loan_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanJpaEntity extends BaseJpaEntity {

    @Version
    private int version;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private BigDecimal requestedAmount;

    @Column(nullable = false)
    private String requestedCurrency;

    @Column
    private BigDecimal approvedAmount;

    @Column
    private String approvedCurrency;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private LoanStatus status;
}

// loan/infrastructure/adapter/out/persistence/LoanPersistenceMapper.java
@Mapper(componentModel = "spring")
public interface LoanPersistenceMapper {

    @Mapping(target = "version", source = "version")
    @Mapping(target = "id", expression = "java(domain.getId() != null ? domain.getId().value() : null)")
    @Mapping(target = "customerId", expression = "java(domain.getCustomerId() != null ? domain.getCustomerId().value() : null)")
    @Mapping(target = "productId", expression = "java(domain.getProductId() != null ? domain.getProductId().value() : null)")
    @Mapping(target = "requestedAmount", expression = "java(domain.getRequestedAmount() != null ? domain.getRequestedAmount().amount() : null)")
    @Mapping(target = "requestedCurrency", expression = "java(domain.getRequestedAmount() != null ? domain.getRequestedAmount().currency().getCurrencyCode() : null)")
    @Mapping(target = "approvedAmount", expression = "java(domain.getApprovedAmount() != null ? domain.getApprovedAmount().amount() : null)")
    @Mapping(target = "approvedCurrency", expression = "java(domain.getApprovedAmount() != null ? domain.getApprovedAmount().currency().getCurrencyCode() : null)")
    LoanJpaEntity toJpaEntity(LoanApplication domain);

    @Mapping(target = "version", source = "version")
    @Mapping(target = "id", expression = "java(entity.getId() != null ? LoanApplicationId.of(entity.getId()) : null)")
    @Mapping(target = "customerId", expression = "java(entity.getCustomerId() != null ? CustomerId.of(entity.getCustomerId()) : null)")
    @Mapping(target = "productId", expression = "java(entity.getProductId() != null ? LoanProductId.of(entity.getProductId()) : null)")
    @Mapping(target = "requestedAmount", expression = "java(entity.getRequestedAmount() != null ? Money.of(entity.getRequestedAmount(), entity.getRequestedCurrency()) : null)")
    @Mapping(target = "approvedAmount", expression = "java(entity.getApprovedAmount() != null ? Money.of(entity.getApprovedAmount(), entity.getApprovedCurrency()) : null)")
    LoanApplication toDomain(LoanJpaEntity entity);
}
```

---

## Code Examples — Infrastructure

### Jackson Configuration (Vietnamese Locale)

```java
// shared/infrastructure/config/JacksonConfig.java
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            
            // Format dates to standard Vietnamese format
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", new Locale("vi", "VN"));
            builder.serializers(new LocalDateTimeSerializer(dateFormatter));
            
            // Serialize Money correctly without decimal places for VND
            builder.serializerByType(Money.class, new JsonSerializer<Money>() {
                @Override
                public void serialize(Money money, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                    if ("VND".equalsIgnoreCase(money.currency())) {
                        gen.writeNumber(money.amount().setScale(0, RoundingMode.DOWN));
                    } else {
                        gen.writeNumber(money.amount());
                    }
                }
            });
        };
    }
}
```

### OpenAPI Configuration

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.x</version>
</dependency>
```

```yaml
# application.yml
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
  paths-to-match: /api/**
```

```java
// shared/infrastructure/config/OpenApiConfig.java
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info().title("Project Meridian API").version("v1"))
            .components(new Components()
                .addSecuritySchemes("bearer-key", 
                    new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
```

### Base JPA Entity (Soft Delete & Auditing)

```java
// shared/infrastructure/persistence/BaseJpaEntity.java
@MappedSuperclass
@SoftDelete // Hibernate 6.4+ feature for automatic soft delete
public abstract class BaseJpaEntity {
    @Id
    private UUID id;
    
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(nullable = false)
    private Instant updatedAt;
    
    // @SoftDelete automatically handles boolean 'deleted' column under the hood
}
```

### JPA Entity (Optimistic Locking)

```java
// loan/infrastructure/adapter/out/persistence/LoanJpaEntity.java
@Entity
@Table(name = "loan_applications")
public class LoanJpaEntity extends BaseJpaEntity {

    @Version  // JPA optimistic locking
    private int version;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private String status;

    // ... other fields

    // Mapper must carry version from domain → JPA and back
}
```

```java
// loan/infrastructure/adapter/out/persistence/LoanPersistenceMapper.java
// When mapping domain → JPA, preserve version:
entity.setVersion(domain.getVersion());
// When mapping JPA → domain, restore version:
domain.setVersion(entity.getVersion());
```

---

## Code Examples — Shared Kernel

### Web: Pagination Response Wrapper

```java
// shared/infrastructure/web/PaginatedResponse.java
public record PaginatedResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last
) {
    public static <T> PaginatedResponse<T> from(Page<T> page) {
        return new PaginatedResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isLast()
        );
    }
}
```

### Security: Resource Ownership Enforcement

```java
// shared/infrastructure/security/AuthenticatedUser.java
public class AuthenticatedUser implements UserDetails {
    private final UUID userId;
    private final CustomerId customerId;  // null for non-customer roles
    private final Set<String> roles;
    private final Set<String> permissions;

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    // Standard UserDetails methods...
}
```

```java
// shared/infrastructure/security/ResourceOwnershipEnforcer.java
@Component
@RequiredArgsConstructor
public class ResourceOwnershipEnforcer {

    /**
     * For CUSTOMER role: ensures the requested customerId matches the authenticated user.
     * For LOAN_OFFICER/MANAGER/ADMIN: allows access to any customer's data.
     */
    public void enforceCustomerOwnership(AuthenticatedUser user, CustomerId resourceCustomerId) {
        if (user.hasRole("CUSTOMER")) {
            if (!user.getCustomerId().equals(resourceCustomerId)) {
                throw new AccessDeniedException(
                    "Customer " + user.getCustomerId() + " cannot access resources belonging to " + resourceCustomerId);
            }
        }
        // Officers, managers, admins can access any customer
    }
}
```

## Code Examples — Shared Kernel

### Value Object: Money

```java
// shared/domain/model/Money.java
public record Money(BigDecimal amount, Currency currency) {

    public static final Currency VND = Currency.getInstance("VND");
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    public static final int VND_SCALE = 0;

    public Money {
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");
        // VND has 0 decimal places; normalize on construction
        int scale = currency.equals(VND) ? VND_SCALE : currency.getDefaultFractionDigits();
        amount = amount.setScale(Math.max(scale, 0), ROUNDING_MODE);
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public static Money vnd(long amount) {
        return new Money(BigDecimal.valueOf(amount).setScale(VND_SCALE, ROUNDING_MODE), VND);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        int scale = currency.equals(VND) ? VND_SCALE : currency.getDefaultFractionDigits();
        return new Money(this.amount.add(other.amount).setScale(scale, ROUNDING_MODE), this.currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        int scale = currency.equals(VND) ? VND_SCALE : currency.getDefaultFractionDigits();
        return new Money(this.amount.subtract(other.amount).setScale(scale, ROUNDING_MODE), this.currency);
    }

    public Money multiply(BigDecimal factor) {
        int scale = currency.equals(VND) ? VND_SCALE : currency.getDefaultFractionDigits();
        return new Money(this.amount.multiply(factor).setScale(scale, ROUNDING_MODE), this.currency);
    }

    public Money divide(BigDecimal divisor) {
        int scale = currency.equals(VND) ? VND_SCALE : currency.getDefaultFractionDigits();
        return new Money(this.amount.divide(divisor, Math.max(scale, 4), ROUNDING_MODE), this.currency);
    }

    public boolean isPositive() { return amount.compareTo(BigDecimal.ZERO) > 0; }
    public boolean isZero() { return amount.compareTo(BigDecimal.ZERO) == 0; }
    public boolean isNegative() { return amount.compareTo(BigDecimal.ZERO) < 0; }
    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }
    public boolean isLessThanOrEqual(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) <= 0;
    }

    private void assertSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new DomainException("Cannot operate on different currencies: "
                + this.currency + " vs " + other.currency);
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
```

### Value Object: NationalId

```java
// shared/domain/model/NationalId.java
public record NationalId(String value) {
    public NationalId {
        Objects.requireNonNull(value, "National ID must not be null");
        String cleaned = value.replaceAll("\\s", "");
        if (!cleaned.matches("^\\d{9}$") && !cleaned.matches("^\\d{12}$")) {
            throw new DomainException(
                "National ID must be 9 digits (CMND) or 12 digits (CCCD), got: " + cleaned.length());
        }
        value = cleaned;
    }

    public boolean isCccd() { return value.length() == 12; }
    public boolean isCmnd() { return value.length() == 9; }
}
```

### Value Object: EmailAddress

```java
// shared/domain/model/EmailAddress.java
public record EmailAddress(String value) {
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public EmailAddress {
        Objects.requireNonNull(value, "Email must not be null");
        if (!EMAIL_PATTERN.matcher(value).matches()) {
            throw new DomainException("Invalid email format: " + value);
        }
        value = value.toLowerCase();
    }
}
```

### Value Object: PhoneNumber

```java
// shared/domain/model/PhoneNumber.java
public record PhoneNumber(String value) {
    private static final Pattern VN_PHONE = Pattern.compile("^(\\+84|0)\\d{9}$");

    public PhoneNumber {
        Objects.requireNonNull(value, "Phone number must not be null");
        String cleaned = value.replaceAll("[\\s-]", "");
        if (!VN_PHONE.matcher(cleaned).matches()) {
            throw new DomainException("Invalid Vietnamese phone number: " + value);
        }
        value = cleaned;
    }

    public String toInternational() {
        return value.startsWith("0") ? "+84" + value.substring(1) : value;
    }
}
```

---

## Code Examples — Loan Module (Additional)

### Entity: LoanProduct

```java
// loan/domain/model/LoanProduct.java
public class LoanProduct {
    private LoanProductId id;
    private String name;
    private Money minAmount;
    private Money maxAmount;
    private InterestRate annualInterestRate;
    private int minTermMonths;
    private int maxTermMonths;
    private boolean active;

    public void validateLoanRequest(Money requestedAmount, int termMonths) {
        if (!active) {
            throw new DomainException("Loan product is inactive: " + name);
        }
        if (requestedAmount.isGreaterThan(maxAmount)
                || maxAmount.isGreaterThan(requestedAmount) == false
                    && minAmount.isGreaterThan(requestedAmount)) {
            throw new DomainException(String.format(
                "Requested amount %s outside product limits [%s, %s]",
                requestedAmount, minAmount, maxAmount));
        }
        if (termMonths < minTermMonths || termMonths > maxTermMonths) {
            throw new DomainException(String.format(
                "Term %d months outside product limits [%d, %d]",
                termMonths, minTermMonths, maxTermMonths));
        }
    }

    public RepaymentSchedule calculateRepaymentSchedule(LoanApplicationId loanId, Money principal, int termMonths, LocalDate disbursementDate) {
        return RepaymentSchedule.generateFlatRate(loanId, principal, annualInterestRate, termMonths, disbursementDate);
    }
}
```

### Value Object: RepaymentSchedule (SBV Circular 39 Compliant)

```java
// loan/domain/model/RepaymentSchedule.java
public record RepaymentSchedule(
    LoanApplicationId loanId,
    Money totalPrincipal,
    Money totalInterest,
    List<RepaymentInstallment> installments
) {
    public static RepaymentSchedule generateFlatRate(
            LoanApplicationId loanId, Money principal, 
            InterestRate annualRate, int termMonths, LocalDate disbursementDate) {
        
        // Circular 39 compliance: clearly distinguish principal and interest per period
        BigDecimal monthlyPrincipal = principal.amount()
            .divide(BigDecimal.valueOf(termMonths), 0, RoundingMode.HALF_UP);
            
        BigDecimal monthlyRate = annualRate.annualRate()
            .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
            
        BigDecimal monthlyInterest = principal.amount().multiply(monthlyRate)
            .setScale(0, RoundingMode.HALF_UP);

        Money installmentPrincipal = Money.of(monthlyPrincipal, "VND");
        Money installmentInterest = Money.of(monthlyInterest, "VND");

        List<RepaymentInstallment> installments = new ArrayList<>();
        Money remainingPrincipal = principal;

        for (int i = 1; i <= termMonths; i++) {
            LocalDate dueDate = disbursementDate.plusMonths(i);
            
            // Adjust last installment for rounding differences
            if (i == termMonths) {
                installmentPrincipal = remainingPrincipal;
            }
            
            installments.add(new RepaymentInstallment(
                i, dueDate, installmentPrincipal, installmentInterest, 
                installmentPrincipal.add(installmentInterest), false
            ));
            
            remainingPrincipal = remainingPrincipal.subtract(installmentPrincipal);
        }

        Money totalInterest = Money.of(
            monthlyInterest.multiply(BigDecimal.valueOf(termMonths)), "VND");

        return new RepaymentSchedule(loanId, principal, totalInterest, installments);
    }
}

public record RepaymentInstallment(
    int periodNumber,
    LocalDate dueDate,
    Money principalAmount,
    Money interestAmount,
    Money totalAmount,
    boolean isPaid
) {}
```

### Value Object: StatusTransition

```java
// loan/domain/model/StatusTransition.java
public record StatusTransition(
    LoanStatus fromStatus,
    LoanStatus toStatus,
    Instant transitionedAt,
    UserId performedBy  // nullable for system-triggered transitions
) {
    public static StatusTransition of(LoanStatus from, LoanStatus to, Instant at) {
        return new StatusTransition(from, to, at, null);
    }

    public static StatusTransition of(LoanStatus from, LoanStatus to, Instant at, UserId by) {
        return new StatusTransition(
            Objects.requireNonNull(from),
            Objects.requireNonNull(to),
            Objects.requireNonNull(at),
            by
        );
    }
}
```

### Domain Events (Missing Transitions)

```java
// loan/domain/event/LoanCancelledEvent.java
public record LoanCancelledEvent(
    LoanApplicationId loanId,
    CustomerId customerId,
    LoanStatus previousStatus,
    String reason,
    Instant cancelledAt
) implements DomainEvent {}

// loan/domain/event/LoanReviewStartedEvent.java
public record LoanReviewStartedEvent(
    LoanApplicationId loanId,
    UserId reviewerId,
    Instant startedAt
) implements DomainEvent {}

// loan/domain/event/LoanSentForApprovalEvent.java
public record LoanSentForApprovalEvent(
    LoanApplicationId loanId,
    CustomerId customerId,
    Money requestedAmount,
    Instant sentAt
) implements DomainEvent {}

// loan/domain/event/LoanCompletedEvent.java
public record LoanCompletedEvent(
    LoanApplicationId loanId,
    CustomerId customerId,
    Money disbursedAmount,
    Instant completedAt
) implements DomainEvent {}
```

---

## Code Examples — Approval Module

### Aggregate Root: ApprovalRequest

```java
// approval/domain/model/ApprovalRequest.java
public class ApprovalRequest {
    private ApprovalRequestId id;
    private LoanApplicationId loanId;
    private CustomerId customerId;
    private Money loanAmount;
    private ApprovalStatus status; // PENDING → IN_PROGRESS → APPROVED / REJECTED
    private List<ApprovalStep> steps;
    private int currentStepIndex;
    private Instant createdAt;
    private int version;

    public static ApprovalRequest create(
            LoanApplicationId loanId, CustomerId customerId,
            Money loanAmount, List<ApprovalRule> rules) {
        var request = new ApprovalRequest();
        request.id = ApprovalRequestId.generate();
        request.loanId = Objects.requireNonNull(loanId);
        request.customerId = Objects.requireNonNull(customerId);
        request.loanAmount = Objects.requireNonNull(loanAmount);
        request.status = ApprovalStatus.PENDING;
        request.steps = rules.stream()
            .filter(rule -> rule.appliesTo(loanAmount))
            .sorted(Comparator.comparingInt(ApprovalRule::level))
            .map(rule -> ApprovalStep.create(rule))
            .toList();
        if (request.steps.isEmpty()) {
            throw new DomainException("No approval rules match loan amount: " + loanAmount);
        }
        request.currentStepIndex = 0;
        request.createdAt = Instant.now();
        return request;
    }

    public void submitDecision(UserId approver, boolean approved, String comment) {
        if (status == ApprovalStatus.APPROVED || status == ApprovalStatus.REJECTED) {
            throw new InvalidStateTransitionException(status.name(), "already terminal");
        }
        ApprovalStep current = steps.get(currentStepIndex);
        current.recordDecision(approver, approved, comment);
        this.status = ApprovalStatus.IN_PROGRESS;

        if (!approved) {
            this.status = ApprovalStatus.REJECTED;
            return;
        }
        if (currentStepIndex < steps.size() - 1) {
            currentStepIndex++;
        } else {
            this.status = ApprovalStatus.APPROVED;
        }
    }

    public boolean isFullyApproved() { return status == ApprovalStatus.APPROVED; }
    public boolean isRejected() { return status == ApprovalStatus.REJECTED; }
    public ApprovalStep getCurrentStep() { return steps.get(currentStepIndex); }
}
```

### Application Event Listener

```java
// approval/application/event/LoanEventListener.java
@Component
@RequiredArgsConstructor
public class LoanEventListener {

    private final ApprovalUseCase approvalUseCase;

    // Use ApplicationModuleListener instead of standard EventListener
    // This ensures execution in a separate transaction AFTER the publisher commits
    @ApplicationModuleListener
    public void onLoanSubmitted(LoanSubmittedEvent event) {
        approvalUseCase.initializeApprovalFlow(
            event.loanId(),
            event.customerId(),
            event.requestedAmount()
        );
    }
}
```

---

## Code Examples — Customer Module

### Aggregate Root: Customer

```java
// customer/domain/model/Customer.java
public class Customer {
    private CustomerId id;
    private UserId userId;
    private PersonalInfo personalInfo;
    private EmploymentInfo employmentInfo;
    private KycStatus kycStatus;
    private Instant createdAt;

    public boolean isEligibleForLoan() {
        return kycStatus == KycStatus.VERIFIED
            && employmentInfo != null
            && employmentInfo.isCurrentlyEmployed()
            && employmentInfo.monthlySalary().isPositive();
    }

    public Money maxLoanAmount() {
        if (!isEligibleForLoan()) return Money.zero(Money.VND);
        // Salary advance: max 50% of monthly salary (business rule)
        return employmentInfo.monthlySalary().multiply(new BigDecimal("0.5"));
    }

    public void verify(UserId verifiedBy) {
        if (personalInfo == null || personalInfo.nationalId() == null) {
            throw new DomainException("Cannot verify customer without national ID");
        }
        this.kycStatus = KycStatus.VERIFIED;
    }
}

// customer/domain/model/PersonalInfo.java
public record PersonalInfo(
    String fullName,
    NationalId nationalId,
    PhoneNumber phoneNumber,
    EmailAddress email,
    LocalDate dateOfBirth
) {
    public PersonalInfo {
        Objects.requireNonNull(fullName, "Full name required");
        Objects.requireNonNull(nationalId, "National ID required");
        if (fullName.isBlank()) throw new DomainException("Full name cannot be blank");
    }
}

// customer/domain/model/EmploymentInfo.java
public record EmploymentInfo(
    String employerName,
    String position,
    Money monthlySalary,
    LocalDate startDate,
    LocalDate endDate  // null = currently employed
) {
    public boolean isCurrentlyEmployed() {
        return endDate == null || endDate.isAfter(LocalDate.now());
    }
}
```

---

## Code Examples — Identity Module

### Flyway Migration: Refresh Tokens

```sql
-- V003__create_refresh_tokens_table.sql
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(64) NOT NULL,        -- SHA-256 hash, NOT plaintext
    family_id       UUID NOT NULL,               -- Token family for reuse detection
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMP,
    replaced_by_id  UUID REFERENCES refresh_tokens(id),

    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id, revoked);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);
```

### Application Service: Authentication

```java
// identity/application/service/AuthenticationService.java
@Service
@Transactional
public class AuthenticationService implements AuthenticationUseCase {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepo;
    private final JwtTokenProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AuthResult authenticate(String email, String password) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new AuthenticationException("Invalid credentials"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid credentials");
        }

        String accessToken = jwtProvider.generateAccessToken(
            user.getId(), user.getEmail(), user.getRoleNames(), user.getPermissionNames());

        String rawRefreshToken = UUID.randomUUID().toString();
        UUID familyId = UUID.randomUUID();

        RefreshToken refreshEntity = RefreshToken.create(
            user.getId(), hashToken(rawRefreshToken), familyId,
            Instant.now().plus(Duration.ofDays(7)));
        refreshTokenRepo.save(refreshEntity);

        return new AuthResult(accessToken, rawRefreshToken, 900); // 15min in seconds
    }

    @Override
    public AuthResult refresh(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        RefreshToken existing = refreshTokenRepo.findByTokenHash(hash)
            .orElseThrow(() -> new AuthenticationException("Invalid refresh token"));

        // REUSE DETECTION: If token is already revoked, someone stole it
        if (existing.isRevoked()) {
            // Revoke ENTIRE family — force re-login on all devices in this family
            refreshTokenRepo.revokeByFamilyId(existing.getFamilyId());
            throw new AuthenticationException("Refresh token reuse detected — all sessions revoked");
        }

        if (existing.isExpired()) {
            throw new AuthenticationException("Refresh token expired");
        }

        // Rotate: revoke old, issue new in same family
        existing.revoke();
        refreshTokenRepo.save(existing);

        User user = userRepository.findById(existing.getUserId())
            .orElseThrow(() -> new AuthenticationException("User not found"));

        String newRawRefreshToken = UUID.randomUUID().toString();
        RefreshToken newRefresh = RefreshToken.create(
            user.getId(), hashToken(newRawRefreshToken), existing.getFamilyId(),
            Instant.now().plus(Duration.ofDays(7)));
        existing.setReplacedById(newRefresh.getId());
        refreshTokenRepo.save(newRefresh);

        String newAccessToken = jwtProvider.generateAccessToken(
            user.getId(), user.getEmail(), user.getRoleNames(), user.getPermissionNames());

        return new AuthResult(newAccessToken, newRawRefreshToken, 900);
    }

    private String hashToken(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

---

## Code Examples — Input Validation & Error Handling

### DTO Validation Constraints

```java
// loan/application/dto/CreateLoanRequest.java
public record CreateLoanRequest(
    @NotNull(message = "Customer ID is required")
    UUID customerId,

    @NotNull(message = "Product ID is required")
    UUID productId,

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "100000", message = "Minimum loan amount is 100,000 VND")
    @DecimalMax(value = "50000000", message = "Maximum loan amount is 50,000,000 VND")
    @Digits(integer = 12, fraction = 0, message = "VND amounts must be whole numbers")
    BigDecimal amount,

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "VND", message = "Only VND currency is supported")
    String currency
) {}
```

```java
// identity/application/dto/LoginRequest.java
public record LoginRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
    String password
) {}
```

```java
// shared/infrastructure/validation/ValidNationalId.java
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NationalIdValidator.class)
@Documented
public @interface ValidNationalId {
    String message() default "Must be a valid Vietnamese CMND (9 digits) or CCCD (12 digits)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

```java
// shared/infrastructure/validation/NationalIdValidator.java
public class NationalIdValidator implements ConstraintValidator<ValidNationalId, String> {

    private static final Pattern CMND = Pattern.compile("^\\d{9}$");
    private static final Pattern CCCD = Pattern.compile("^\\d{12}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) return true; // Let @NotNull handle nullability
        String cleaned = value.replaceAll("\\s", "");
        
        if (!CMND.matcher(cleaned).matches() && !CCCD.matcher(cleaned).matches()) {
            return false;
        }
        
        if (cleaned.length() == 12) {
            String provinceCode = cleaned.substring(0, 3);
            int code = Integer.parseInt(provinceCode);
            if (code < 1 || code > 96) {
                return false;
            }
            int centuryGenderCode = Character.getNumericValue(cleaned.charAt(3));
            if (centuryGenderCode < 0 || centuryGenderCode > 9) {
                return false;
            }
        }
        return true;
    }
}
```

```java
// identity/application/dto/RegisterRequest.java
public record RegisterRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255)
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
             message = "Password must contain uppercase, lowercase, and digit")
    String password,

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Name must be 2-100 characters")
    String fullName,

    @NotBlank(message = "National ID is required")
    @ValidNationalId
    String nationalId,

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^(0|\\+84)(3[2-9]|5[6|8|9]|7[0|6-9]|8[0-9]|9[0-4|6-9])\\d{7}$",
             message = "Invalid Vietnamese phone number")
    String phoneNumber
) {}
```

### Global Exception Handler

```java
// shared/infrastructure/web/ApiErrorResponse.java
public record ApiErrorResponse(
    Instant timestamp,
    int status,
    String errorCode,
    String message,
    String path,
    String traceId,
    Map<String, String> details
) {}

// shared/infrastructure/web/GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
            errors.put(err.getField(), err.getDefaultMessage()));
            
        var error = new ApiErrorResponse(
            Instant.now(),
            HttpStatus.BAD_REQUEST.value(),
            "VALIDATION_FAILED",
            "Input validation failed",
            request.getRequestURI(),
            MDC.get("traceId"),
            errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
            errors.put(v.getPropertyPath().toString(), v.getMessage()));
            
        var error = new ApiErrorResponse(
            Instant.now(),
            HttpStatus.BAD_REQUEST.value(),
            "VALIDATION_FAILED",
            "Constraint violation",
            request.getRequestURI(),
            MDC.get("traceId"),
            errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomain(DomainException ex, HttpServletRequest request) {
        var error = new ApiErrorResponse(
            Instant.now(),
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            "DOMAIN_ERROR",
            ex.getMessage(),
            request.getRequestURI(),
            MDC.get("traceId"),
            null
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        var error = new ApiErrorResponse(
            Instant.now(),
            HttpStatus.NOT_FOUND.value(),
            "NOT_FOUND",
            ex.getMessage(),
            request.getRequestURI(),
            MDC.get("traceId"),
            null
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        var error = new ApiErrorResponse(
            Instant.now(),
            HttpStatus.FORBIDDEN.value(),
            "ACCESS_DENIED",
            "Insufficient permissions",
            request.getRequestURI(),
            MDC.get("traceId"),
            null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
}
```