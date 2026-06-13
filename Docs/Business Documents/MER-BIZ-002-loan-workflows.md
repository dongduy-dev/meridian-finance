# MER-BIZ-002 — Meridian Loan Product Models and Workflows

## 1. Purpose

This document defines the product models and business workflows for the Meridian Lending Platform MVP.

Meridian supports a multi-product digital lending workflow centered on Salary Advance, with streamlined support for Unsecured Consumer Loan and Collateral Loan products.

The goal is to provide a realistic loan origination, approval, disbursement, and repayment-tracking workflow suitable for a fictional portfolio fintech system.

---

## 2. Product Model Overview

Meridian uses one generic lending core with product-specific policy behavior.

### 2.1 Common Lending Model

All loan products share the same core loan application lifecycle:

1. Customer profile completion
2. Product selection
3. Product eligibility validation
4. Loan application submission
5. Product-specific verification
6. Document checklist validation
7. Loan Officer review
8. Approval decision
9. Customer acceptance
10. Contract/document preparation
11. Manual disbursement confirmation
12. Loan activation
13. Repayment tracking
14. Settlement or closure

### 2.2 Product-Specific Verification Models

| Product                 | Verification Model                                | Main Risk Focus                                                       |
| ----------------------- | ------------------------------------------------- | --------------------------------------------------------------------- |
| Salary Advance          | Partner company and employee verification model         | Employment status, Salary amount, Available Salary Advance limit      |
| Unsecured Consumer Loan | Document-based income and employment verification model | Repayment capacity and document consistency                           |
| Collateral Loan         | Collateral information and document review model        | Collateral ownership, collateral support documents, manual assessment |

---

## 3. Loan Product Catalog

The loan product catalog stores configurable product information used by the system to validate and display lending products.

Each loan product should include:

* Product code
* Product name
* Product type
* Active/inactive status
* Minimum loan amount
* Maximum loan amount
* Allowed loan terms
* Interest rate
* Repayment method
* Required document types
* Product description
* Product-specific policy reference

Example product types:

```text
SALARY_ADVANCE
UNSECURED_CONSUMER_LOAN
COLLATERAL_LOAN
```

---

## 4. Common Loan Application Workflow

### 4.1 Customer Profile Completion

Before submitting a loan application, the customer must complete a basic profile.

Profile information may include:

* Full name
* Date of birth
* Identity reference
* Phone number
* Email address
* Residential address
* Employment information
* Bank account information for disbursement
* Basic consent confirmations

MVP note: Meridian does not integrate with real eKYC, credit bureau, or bank account verification. These checks are represented by manual review status or mock verification status.

---

### 4.2 Product Selection

The customer selects an active loan product from the customer portal.

The system displays:

* Product name
* Product description
* Minimum and maximum amount
* Available terms
* Interest rate
* Repayment method
* Required documents
* Eligibility notes

---

### 4.3 Product Eligibility Pre-Check

The system performs basic pre-checks before allowing submission.

Common checks include:

* Customer profile is complete
* Product is active
* Requested amount is within product limits
* Requested term is allowed
* Customer has no active blocking application
* Required product-specific information has been provided

If pre-check fails, the customer is shown the reason and may update the application.

---

### 4.4 Application Submission

After passing basic validation, the customer submits the loan application.

If the application was saved as a draft, the system updates the existing LoanApplication from DRAFT to SUBMITTED. If draft saving is not used, the system creates a new LoanApplication directly with SUBMITTED status.

The system records:

* Unique application number
* Customer reference
* Product reference
* Product type
* Requested amount
* Requested term
* Application status
* Submission timestamp
* Product-specific details
* Audit trail entry

Initial status:

```text
SUBMITTED
```

---

### 4.5 Product-Specific Verification

After submission, Meridian executes the relevant product-specific verification model.

* Salary Advance: verify customer against Partner Employee records.
* Unsecured Consumer Loan: verify income and employment document checklist.
* Collateral Loan: verify collateral information and supporting documents.

Verification may produce one of the following results:

```text
VERIFIED
FAILED
PENDING_MANUAL_REVIEW
REQUIRES_MORE_INFORMATION
```

---

### 4.6 Document Checklist Validation

The system checks whether the required documents for the selected product have been uploaded.

Document status values:

```text
NOT_REQUIRED
PENDING_UPLOAD
UPLOADED
UNDER_REVIEW
ACCEPTED
REJECTED
EXPIRED
```

If documents are missing or rejected, the application may be returned to the customer for revision.

---

### 4.7 Loan Officer Review

A Loan Officer reviews the application after required information is available.

The Loan Officer may:

* Review customer profile
* Review product-specific verification result
* Review uploaded documents
* Check requested amount and term
* Add internal comments
* Recommend approval
* Recommend rejection
* Return the application for revision

Possible outcomes:

```text
RECOMMEND_APPROVAL
RECOMMEND_REJECTION
RETURN_FOR_REVISION
```

---

### 4.8 Approval Decision

An Approver reviews the application and the Loan Officer recommendation.

The Approver may:

* Approve the application
* Reject the application
* Return the application to the Loan Officer
* Request more information

Approval should be separate from Loan Officer review to support maker-checker control.

Possible outcomes:

```text
APPROVED
REJECTED
RETURNED_TO_REVIEW
```

---

### 4.9 Offer and Customer Acceptance

After approval, the system prepares approved loan terms.

Approved terms include:

* Approved amount
* Approved term
* Interest rate
* Repayment method
* Estimated installment amount
* provisional repayment schedule
* Fees, if any
* Conditions, if any

The customer must accept the approved terms before the application can proceed to contract preparation and disbursement.

Possible customer actions:

```text
ACCEPT_OFFER
DECLINE_OFFER
EXPIRE_OFFER
```

---

### 4.10 Contract and Document Preparation

After customer acceptance, the system prepares or records required contract and disbursement documents.

MVP document handling may include:

* Generated loan agreement record
* Uploaded signed agreement
* Uploaded supporting documents
* Internal approval memo
* Disbursement instruction record

MVP note: Meridian does not need full e-signature integration. Contract signing can be represented by uploaded signed documents or manual staff confirmation.

---

### 4.11 Manual Disbursement Confirmation

After documents are complete, the Accounting Officer reviews the disbursement information.

The Accounting Officer confirms:

* Approved amount
* Customer bank account information
* Required documents are complete
* Approval is valid
* Customer has accepted the offer

The Accounting Officer then marks disbursement as completed.

MVP note: Meridian does not execute real bank transfers. Disbursement is manually marked as completed for portfolio scope.

---

### 4.12 Loan Activation

After manual disbursement is marked as completed, the system creates a LoanAccount with ACTIVE status.

The system records:

* application reference
* disbursement date
* principal amount
* interest rate
* loan term
* final repayment schedule
* outstanding balance
* loan account status

Initial loan account status:

```text
ACTIVE
```

---

### 4.13 Repayment and Settlement Tracking

The system tracks repayment status manually.

Repayment statuses may include:

```text
NOT_DUE
DUE
PARTIALLY_PAID
PAID
OVERDUE
SETTLED
```

MVP repayment tracking may include:

* Scheduled due date
* Principal due
* Interest due
* Amount paid
* Payment date
* Payment method note
* Manual payment confirmation
* Outstanding balance

---

## 5. Salary Advance Product Workflow

### 5.1 Product Model

Salary Advance is the flagship Meridian product.

It is based on the Partner company and employee verification model, with Available Salary Advance limit calculation handled as a product rule.

The main idea:

```text
Partner Company + Partner Employee data
→ employee verification
→ Available Salary Advance limit calculation
→ loan request
→ review and approval
→ manual disbursement confirmation
→ repayment tracking
```

The following sections break the Salary Advance workflow into detailed sub-workflows for readability. The end-to-end workflow summary in section 5.6 provides the full sequence and should match the Salary Advance workflow summary in MER-BIZ-001.

### 5.2 Back-Office Setup

1. Back-Office Admin creates a Partner Company.
2. Back-Office Admin configures Partner Company status as active or inactive.
3. Back-Office Admin imports Partner Employee data monthly.
4. The system records each Partner Employee import batch.
5. Imported Partner Employee records are validated and stored.

Partner Employee data includes:

* Partner company reference
* Employee code
* Employee full name
* Identity reference
* Salary amount
* Employment status
* Salary advance limit
* Effective month
* Import batch reference
* Active/inactive status

### 5.3 Employee Verification

1. Customer completes profile information.
2. Customer selects Salary Advance product.
3. Customer selects Partner Company.
4. Customer submits employee verification information.
5. System searches Partner Employee records.
6. System matches the customer against Partner Employee records.
7. System checks identity match, employee code match, and active employment status.
8. If matched and active, the customer becomes employment-verified for that partner.
9. If no match is found, the verification fails or is sent for manual review.

Possible verification outcomes:

```text
MATCHED_ACTIVE
MATCHED_INACTIVE
NOT_FOUND
MULTIPLE_MATCHES
PENDING_MANUAL_REVIEW
```

### 5.4 Salary Advance Limit Calculation

After successful employee verification, the system calculates the Available Salary Advance limit.

The Available Salary Advance limit may consider:

* Product maximum amount
* Partner company policy limit
* Employee-level Salary advance limit
* Salary-based percentage cap
* Existing active Salary Advance loans
* Pending Salary Advance applications
* Employee status
* Data freshness of the latest employee import

The final Available Salary Advance limit is the lowest applicable allowed amount after deductions.

Example logic:

```text
availableSalaryAdvanceLimit = min(
  productMaximumAmount,
  partnerCompanyLimit,
  employeeConfiguredLimit,
  salaryBasedLimit
) - outstandingSalaryAdvanceExposure
```

### 5.5 Salary Advance Application

This section describes the application request portion after partner setup, employee verification, and limit calculation have already been completed. Sections 5.2 through 5.5 together cover the full detailed Salary Advance sequence.

1. Customer enters requested amount and term.
2. System validates requested amount against Available Salary Advance limit.
3. System validates term against product rules.
4. Customer submits Salary Advance request.
5. System validates the document checklist.
6. Loan Officer reviews employee verification, Available Salary Advance limit, documents, and application details.
7. Loan Officer recommends approval, rejection, or revision.
8. Approver approves, rejects, or returns the application.
9. Customer accepts approved terms.
10. Contract and disbursement documents are prepared or uploaded.
11. Accounting Officer marks disbursement as completed.
12. The final repayment schedule is generated.
13. Loan becomes active.
14. Repayment and settlement status are tracked.

### 5.6 End-to-End Salary Advance Workflow Summary

1. Back-Office Admin creates a Partner Company.
2. Back-Office Admin configures Partner Company status as active or inactive.
3. Back-Office Admin imports Partner Employee data monthly.
4. System records Partner Employee import batch information.
5. Imported Partner Employee records are validated and stored.
6. Customer completes profile information.
7. Customer selects Salary Advance.
8. Customer selects Partner Company.
9. Customer submits employee verification information.
10. System matches the customer against Partner Employee records.
11. System calculates Available Salary Advance limit.
12. Customer enters requested amount and term.
13. System validates the request against product rules and Available Salary Advance limit.
14. Customer submits the Salary Advance request.
15. System validates the document checklist.
16. Loan Officer reviews employee verification, Available Salary Advance limit, documents, and application details.
17. Loan Officer recommends approval, rejection, or revision.
18. Approver approves, rejects, or returns the application.
19. Customer accepts approved terms.
20. Contract and disbursement documents are prepared or uploaded.
21. Accounting Officer marks disbursement as completed.
22. The final repayment schedule is generated.
23. Loan becomes active.
24. Repayment and settlement status are tracked.

### 5.7 Salary Advance MVP Boundaries

Meridian MVP does not include:

* Real payroll integration
* Real employer API integration
* Automatic payroll deduction
* Real bank transfer
* Employer-facing production portal
* Real-time HR system sync

Monthly employee import is sufficient for the MVP.

---

## 6. Unsecured Consumer Loan Product Workflow

### 6.1 Product Model

Unsecured Consumer Loan is a streamlined product based on the Document-based income and employment verification model.

The main idea:

```text
Customer profile + income and employment documents
→ document review
→ repayment capacity review
→ approval
→ manual disbursement confirmation
→ repayment tracking
```

### 6.2 End-to-End Unsecured Consumer Loan Workflow Summary

1. Customer completes profile information.
2. Customer selects Unsecured Consumer Loan.
3. Customer enters requested amount and term.
4. Customer uploads required income and employment documents.
5. System validates product rules, requested amount, requested term, and required fields.
6. System validates the document checklist.
7. Loan Officer reviews customer profile, documents, and basic repayment capacity.
8. Loan Officer recommends approval, rejection, or revision.
9. Approver approves, rejects, or returns the application.
10. Customer accepts approved terms.
11. Contract and disbursement documents are prepared or uploaded.
12. Accounting Officer marks disbursement as completed.
13. The final repayment schedule is generated.
14. Loan becomes active.
15. Repayment and settlement status are tracked.

### 6.3 Typical Required Documents

Required documents may include:

* Identity document
* Proof of income
* Payslip or salary statement
* Bank statement
* Labor contract or employment confirmation
* Loan purpose declaration, if required by product policy

### 6.4 MVP Boundaries

Meridian MVP does not include:

* Real credit bureau integration
* Real income verification API
* Real bank statement parsing
* Automated credit scoring model
* Fully automated approval

Manual review is acceptable for MVP.

---

## 7. Collateral Loan Product Workflow

### 7.1 Product Model

Collateral Loan is a lightweight secured-loan workflow based on the Collateral information and document review model, with manual assessment handled in the review workflow.

The main idea:

```text
Customer profile + collateral information + collateral documents
→ collateral review
→ approval
→ manual disbursement confirmation
→ repayment tracking
```

### 7.2 End-to-End Collateral Loan Workflow Summary

1. Customer completes profile information.
2. Customer selects Collateral Loan.
3. Customer enters requested amount and term.
4. Customer submits collateral information.
5. Customer uploads collateral ownership or supporting documents.
6. System validates product rules, requested amount, requested term, and required collateral information.
7. System validates the document checklist.
8. Loan Officer performs manual collateral review and records assessment notes.
9. Loan Officer recommends approval, rejection, or revision.
10. Approver approves, rejects, or returns the application.
11. Customer accepts approved terms.
12. Contract and disbursement documents are prepared or uploaded.
13. Accounting Officer marks disbursement as completed.
14. The final repayment schedule is generated.
15. Loan becomes active.
16. Repayment and settlement status are tracked.

### 7.3 Collateral Information

Collateral information may include:

* Collateral type
* Collateral description
* Estimated value
* Ownership status
* Ownership document reference
* Collateral condition note
* Manual review note

Possible collateral types:

```text
MOTORBIKE
CAR
ELECTRONICS
PROPERTY_DOCUMENT
OTHER
```

### 7.4 MVP Boundaries

Meridian MVP does not include:

* Full collateral valuation engine
* Legal enforcement workflow
* Notarization workflow
* Collateral auction/liquidation
* External asset registry integration
* Insurance integration

Collateral Loan remains a lightweight manual-review product in MVP.

---

## 8. Recommended Loan Application Statuses

Meridian should use a clear status model for LoanApplication.

Suggested statuses:

```text
DRAFT
SUBMITTED
VERIFICATION_PENDING
VERIFICATION_FAILED
DOCUMENTS_PENDING
UNDER_REVIEW
RETURNED_FOR_REVISION
APPROVAL_PENDING
APPROVED
REJECTED
CUSTOMER_ACCEPTANCE_PENDING
CUSTOMER_DECLINED
CONTRACT_PENDING
DISBURSEMENT_PENDING
DISBURSED
CANCELLED
EXPIRED
```

After disbursement, the system creates a LoanAccount with ACTIVE status.

Suggested LoanAccount statuses:

```text
ACTIVE
OVERDUE
SETTLED
CLOSED
```

---

## 9. Roles and Responsibilities

### Customer

* Completes profile
* Selects product
* Submits application
* Uploads documents
* Accepts or declines approved offer
* Tracks loan and repayment status

### Loan Officer

* Reviews customer profile
* Reviews verification result
* Reviews documents
* Requests revision if needed
* Recommends approval or rejection

### Approver

* Reviews Loan Officer recommendation
* Makes approval decision
* Approves, rejects, or returns application for further review

### Accounting Officer

* Reviews approved and accepted applications
* Confirms document readiness
* Performs manual disbursement confirmation
* Records disbursement details

### Back-Office Admin

* Manages product catalog
* Manages partner companies
* Imports partner employee data
* Maintains system configuration

### System

* Validates product rules
* Maintains workflow status
* Calculates Available Salary Advance limit
* Tracks document checklist status
* Generates the provisional repayment schedule and final repayment schedule
* Records audit trail events

---

## 10. Design Principles

Meridian loan workflows should follow these principles:

1. One common lending core for all products.
2. Product-specific rules are handled through product policies.
3. Salary Advance is the flagship product and should be deeper than the other products.
4. Unsecured Consumer Loan and Collateral Loan should remain streamlined.
5. Manual review is acceptable for MVP.
6. Real financial integrations are out of scope.
7. Every important status change should be auditable.
8. Approval and disbursement should be separate responsibilities.
9. Customer-facing steps should be clear and simple.
10. The system should feel realistic without becoming a production banking core.
