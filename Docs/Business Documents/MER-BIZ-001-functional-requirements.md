# MER-BIZ-001 - Functional Requirements Document

## 1. Document Information

| Field | Value |
| ----- | ----- |
| Project | Meridian |
| Product | Meridian Lending Platform |
| Fictional Company / Brand | Meridian Finance |
| Document Type | Functional Requirements Document |
| Version | 0.2 |
| Status | Draft |
| Author | Dong Duy |
| Reference Workflow | MER-BIZ-002-loan-workflows.md |
| Scope | MVP multi-product digital lending platform centered on Salary Advance, with streamlined support for Unsecured Consumer Loan and Collateral Loan workflows |

---

## 2. Purpose and Portfolio Positioning

This document defines the functional requirements for the Meridian Lending Platform MVP.

During my internship, I gained exposure to fintech lending workflows. Meridian is an independent fictional portfolio project inspired by general digital lending concepts, using fictional data and generalized workflows.

Meridian is designed as a multi-product digital lending platform with one common lending lifecycle. The MVP is centered on Salary Advance and includes streamlined support for Unsecured Consumer Loan and Collateral Loan workflows.

MER-BIZ-002-loan-workflows.md is the source of truth for loan product models, lifecycle logic, role responsibilities, and workflow behavior. This FRD summarizes the functional requirements that the product must support.

This document focuses on what the system must do from a business and functional perspective. Detailed database design, API design, deployment design, and implementation-level technical design are documented separately.

---

## 3. Product and Application Scope

### 3.1 Product Scope

Meridian Lending Platform is an MVP multi-product digital lending platform centered on Salary Advance, with streamlined support for Unsecured Consumer Loan and Collateral Loan workflows.

The MVP supports:

| Product Code | Product Name | MVP Depth | Verification Model |
| ------------ | ------------ | --------- | ------------------ |
| `SALARY_ADVANCE` | Salary Advance | Primary | Partner company and employee verification model |
| `UNSECURED_CONSUMER_LOAN` | Unsecured Consumer Loan | Streamlined | Document-based income and employment verification model |
| `COLLATERAL_LOAN` | Collateral Loan | Streamlined | Collateral information and document review model |

### 3.2 Application Scope

The MVP consists of:

| Component | Scope |
| --------- | ----- |
| Backend | One Spring Boot backend responsible for business logic, security, persistence, and workflow execution. |
| Back-Office Web Portal | Internal web application used by Back-Office Admins, Loan Officers, Approvers, and Accounting Officers. |
| Customer Web Portal | Customer-facing web application used for registration, profile completion, product selection, loan application submission, document upload, offer acceptance, and status tracking. |
| Mobile App | Future enhancement only. A mobile app is not part of the MVP. |

The MVP uses one backend and one database. Customer-facing and back-office applications communicate with the same backend.

### 3.3 Architecture Scope

The product architecture remains high-level in this FRD:

* Java and Spring Boot backend.
* PostgreSQL database.
* Modular monolith.
* DDD-style bounded contexts.
* Hexagonal / Ports and Adapters where useful.
* One backend, one database, multiple frontends.

Backend top-level modules:

```text
com.meridian.platform/
├── shared/
├── identity/
├── customer/
├── partner/
├── loan/
├── approval/
├── document/
├── audit/
└── notification/   # optional later
```

The `loan/` module is the generic lending core. Loan products must not be modeled as separate top-level backend modules. Product-specific rules belong inside `loan/domain/product/...` and are selected through a Strategy/Policy pattern. `LoanApplication` is the common workflow entity, and `LoanProductPolicy` handles product-specific validation and behavior.

### 3.4 In Scope

The MVP includes:

* customer registration and authentication;
* customer profile completion;
* loan product catalog;
* product selection and eligibility validation;
* common loan application workflow;
* Salary Advance partner company management;
* monthly Partner Employee data import;
* Salary Advance employee verification and limit calculation;
* Unsecured Consumer Loan application and document review;
* Collateral Loan application and manual collateral review;
* document checklist validation;
* loan officer review;
* approver decision;
* customer acceptance of approved terms;
* contract and document preparation tracking;
* manual disbursement confirmation;
* loan activation;
* repayment tracking;
* settlement or closure tracking;
* maker-checker controls for review and approval;
* audit trail for important business actions and status transitions.

### 3.5 Out of Scope

The MVP does not include:

* No real bank transfer;
* No real payroll integration;
* No real employer API integration;
* No real payment gateway;
* No real SMS OTP;
* No biometric login;
* No real credit bureau integration;
* No full collateral valuation/legal enforcement;
* No double-entry ledger;
* No production compliance workflow;
* No microservices;
* No full mobile app as MVP;
* No automated credit scoring model;
* No full e-signature integration;
* No external asset registry integration;
* No savings deposit, entrusted loan, or corporate loan products.

These items may be considered for later phases only if they support the portfolio roadmap.

---

## 4. User Roles

| Role | Description |
| ---- | ----------- |
| Customer | Individual user who completes a profile, selects a product, submits applications, uploads documents, accepts or declines approved terms, and tracks loan and repayment status. |
| Loan Officer | Internal user who reviews applications, verification results, documents, requested amount and term, and recommends approval, rejection, or revision. |
| Approver | Internal user who reviews Loan Officer recommendations and approves, rejects, or returns applications for further review. |
| Accounting Officer | Internal user who confirms document readiness and manually marks disbursement as completed. |
| Back-Office Admin | Internal administrator who manages product catalog configuration, partner companies, Partner Employee imports, users, and system configuration. |
| System | Automated platform behavior responsible for validation, workflow status changes, limit calculation, document checklist checks, schedule generation, and audit trail recording. |

---

## 5. Core Business Concepts

### 5.1 Loan Product

A loan product defines a supported lending product in Meridian. All products share the common lending lifecycle and use product-specific policies for eligibility, verification, document requirements, and validation.

Supported product codes are:

```text
SALARY_ADVANCE
UNSECURED_CONSUMER_LOAN
COLLATERAL_LOAN
```

### 5.2 Loan Product Catalog

The loan product catalog stores product configuration used for display, validation, and workflow behavior.

Each product should include:

* product code;
* product name;
* product type;
* active or inactive status;
* minimum loan amount;
* maximum loan amount;
* allowed loan terms;
* interest rate;
* repayment method;
* required document types;
* product description;
* product-specific policy reference.

### 5.3 Loan Application

A `LoanApplication` represents a customer request for a selected loan product. It is the common workflow record for Salary Advance, Unsecured Consumer Loan, and Collateral Loan.

A LoanApplication may exist as a draft before submission and becomes submitted after the customer completes validation and submits the application.

Common application information includes:

* customer reference;
* selected loan product;
* requested amount;
* requested term;
* product-specific details;
* verification result;
* document checklist status;
* review and approval history;
* customer acceptance status;
* contract and document readiness;
* disbursement status;
* audit history.

### 5.4 Loan Account

A `LoanAccount` represents the active loan record created after an approved and accepted loan application has been manually confirmed as disbursed.

A LoanAccount is used for post-disbursement tracking, including repayment schedule, outstanding balance, repayment status, settlement, and closure.

Loan account information includes:

* application reference;
* disbursement date;
* principal amount;
* interest rate;
* loan term;
* final repayment schedule;
* outstanding balance;
* loan account status.

### 5.5 Partner Company and Partner Employee

A Partner Company is an employer configured by Back-Office Admins for Salary Advance eligibility.

A Partner Employee record represents monthly employee information imported for a partner company. It is used for employee verification and Salary Advance limit calculation.

Partner Employee data includes:

* Partner company reference;
* Employee code;
* Employee full name;
* Identity reference;
* Salary amount;
* Employment status;
* Salary advance limit;
* Effective month;
* Import batch reference;
* Active/inactive status.

### 5.6 Collateral

Collateral represents asset information submitted by a customer for a Collateral Loan. The MVP supports basic collateral information and supporting document review only.

Collateral information may include:

* Collateral type;
* Collateral description;
* Estimated value;
* Ownership status;
* Ownership document reference;
* Collateral condition note;
* Manual review note.

---

## 6. Common Loan Application Workflow

All loan products follow the common lifecycle defined in MER-BIZ-002:

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

The system must keep the customer-facing workflow clear while allowing back-office users to review, approve, return, reject, and complete manual operational steps.

---

## 7. Product Workflows

### 7.1 Salary Advance

Salary Advance is the flagship MVP product. It uses the Partner company and employee verification model, with Available Salary Advance limit calculation handled as a product rule.

End-to-end workflow summary:

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

### 7.2 Unsecured Consumer Loan

Unsecured Consumer Loan is a streamlined product based on the Document-based income and employment verification model.

End-to-end workflow summary:

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

### 7.3 Collateral Loan

Collateral Loan is a streamlined secured-loan workflow based on the Collateral information and document review model, with manual assessment handled in the review workflow.

End-to-end workflow summary:

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

---

## 8. Status Requirements

### 8.1 LoanApplication Statuses

| Status | Description |
| ------ | ----------- |
| `DRAFT` | Application has been started but not submitted. |
| `SUBMITTED` | Customer has submitted the application. |
| `VERIFICATION_PENDING` | Product-specific verification is pending. |
| `VERIFICATION_FAILED` | Product-specific verification failed. |
| `DOCUMENTS_PENDING` | Required documents are missing, incomplete, rejected, or pending upload. |
| `UNDER_REVIEW` | Loan Officer is reviewing the application. |
| `RETURNED_FOR_REVISION` | Application has been returned for customer or staff revision. |
| `APPROVAL_PENDING` | Application is waiting for Approver decision. |
| `APPROVED` | Application has been approved. |
| `REJECTED` | Application has been rejected. |
| `CUSTOMER_ACCEPTANCE_PENDING` | Approved terms are waiting for customer acceptance. |
| `CUSTOMER_DECLINED` | Customer declined the approved terms. |
| `CONTRACT_PENDING` | Contract or required documents are being prepared or completed. |
| `DISBURSEMENT_PENDING` | Application is ready for manual disbursement confirmation. |
| `DISBURSED` | Disbursement has been manually confirmed. |
| `CANCELLED` | Application has been cancelled. |
| `EXPIRED` | Application or offer expired before completion. |

### 8.2 LoanAccount Statuses

| Status | Description |
| ------ | ----------- |
| `ACTIVE` | Loan account is active after disbursement. |
| `OVERDUE` | Loan has overdue repayment obligations. |
| `SETTLED` | Loan has been fully repaid or settled. |
| `CLOSED` | Loan account is closed after settlement or administrative closure. |

### 8.3 ProductVerificationResult Statuses

| Status | Description |
| ------ | ----------- |
| `VERIFIED` | Product-specific verification passed. |
| `FAILED` | Product-specific verification failed. |
| `PENDING_MANUAL_REVIEW` | Verification requires manual review before proceeding. |
| `REQUIRES_MORE_INFORMATION` | More information is required from the customer or staff before verification can continue. |

### 8.4 Document Review Statuses

| Status | Description |
| ------ | ----------- |
| `NOT_REQUIRED` | This document is not required for the selected product or workflow step. |
| `PENDING_UPLOAD` | The required document has not been uploaded yet. |
| `UPLOADED` | The document has been uploaded and is waiting for review or validation. |
| `UNDER_REVIEW` | The document is currently being reviewed. |
| `ACCEPTED` | The document has been accepted. |
| `REJECTED` | The document has been rejected and may require replacement or correction. |
| `EXPIRED` | The document is no longer valid according to product or business rules. |

### 8.5 Repayment Statuses

| Status | Description |
| ------ | ----------- |
| `NOT_DUE` | Repayment is scheduled but not yet due. |
| `DUE` | Repayment is currently due. |
| `PARTIALLY_PAID` | Only part of the due amount has been paid. |
| `PAID` | The scheduled repayment has been fully paid. |
| `OVERDUE` | Repayment is past due. |
| `SETTLED` | The repayment obligation has been fully settled. |

---

## 9. Functional Requirements

### 9.1 Customer Management

| ID | Requirement |
| -- | ----------- |
| FR-CUST-001 | The system shall allow customers to register an account. |
| FR-CUST-002 | The system shall allow customers to authenticate and access the Customer Web Portal. |
| FR-CUST-003 | The system shall allow customers to complete and maintain basic profile information. |
| FR-CUST-004 | The system shall require profile completion before loan application submission. |
| FR-CUST-005 | The system shall allow customers to maintain bank account information for disbursement tracking. |
| FR-CUST-006 | The system shall allow customers to view loan application history and current status. |
| FR-CUST-007 | The system shall allow customers to view active loan and repayment information. |

### 9.2 Loan Product Catalog

| ID | Requirement |
| -- | ----------- |
| FR-PROD-001 | The system shall maintain a catalog of loan products. |
| FR-PROD-002 | The system shall support `SALARY_ADVANCE`, `UNSECURED_CONSUMER_LOAN`, and `COLLATERAL_LOAN`. |
| FR-PROD-003 | The system shall allow Back-Office Admins to activate or deactivate loan products. |
| FR-PROD-004 | The system shall store product name, description, type, min amount, max amount, allowed terms, interest rate, repayment method, and required document types. |
| FR-PROD-005 | The system shall associate each product with a product-specific policy reference. |
| FR-PROD-006 | The system shall display active products and product eligibility notes in the Customer Web Portal. |
| FR-PROD-007 | The system shall apply product-specific validation rules before submission and during workflow transitions. |

### 9.3 Common Loan Application

| ID | Requirement |
| -- | ----------- |
| FR-LOAN-001 | The system shall allow customers to create loan applications for active loan products. |
| FR-LOAN-002 | The system shall use `LoanApplication` as the common workflow record for all loan products. |
| FR-LOAN-003 | The system shall capture requested amount and requested term for each loan application. |
| FR-LOAN-004 | The system shall validate customer profile completion, product status, requested amount, requested term, and required product-specific information. |
| FR-LOAN-005 | The system shall create an application number and audit trail entry when an application is submitted. |
| FR-LOAN-006 | The system shall support product-specific verification after submission. |
| FR-LOAN-007 | The system shall support document checklist validation based on product requirements. |
| FR-LOAN-008 | The system shall allow eligible applications to be returned for revision. |
| FR-LOAN-009 | The system shall allow eligible applications to be cancelled or expired according to business rules. |

### 9.4 Salary Advance

| ID | Requirement |
| -- | ----------- |
| FR-SA-001 | The system shall allow Back-Office Admins to create and maintain Partner Companies. |
| FR-SA-002 | The system shall allow Back-Office Admins to mark Partner Companies as active or inactive. |
| FR-SA-003 | The system shall allow Back-Office Admins to import Partner Employee data monthly. |
| FR-SA-004 | The system shall record Partner Employee import batch information. |
| FR-SA-005 | Partner Employee data shall include partner company reference, employee code, employee full name, identity reference, salary amount, employment status, salary advance limit, effective month, import batch reference, and active/inactive status. |
| FR-SA-006 | The system shall allow customers to select a Partner Company during Salary Advance verification. |
| FR-SA-007 | The system shall allow customers to submit employee verification information. |
| FR-SA-008 | The system shall match submitted employee verification information against Partner Employee records. |
| FR-SA-009 | The system shall prevent inactive or unmatched employees from proceeding without manual review or correction. |
| FR-SA-010 | The system shall calculate the Available Salary Advance limit for verified customers. |
| FR-SA-011 | The system shall validate requested Salary Advance amount against the Available Salary Advance limit. |
| FR-SA-012 | The system shall allow customers to submit Salary Advance requests after passing required validation. |
| FR-SA-013 | The system shall support Loan Officer review of employee verification, Available Salary Advance limit, documents, requested amount, and requested term. |
| FR-SA-014 | The system shall support Approver approval, rejection, or return for revision of Salary Advance applications. |
| FR-SA-015 | The system shall require customer acceptance of approved Salary Advance terms before contract or disbursement steps. |
| FR-SA-016 | The system shall support preparation or upload of Salary Advance contract and disbursement documents after customer acceptance. |
| FR-SA-017 | The system shall allow the Accounting Officer to mark Salary Advance disbursement as completed. |
| FR-SA-018 | The system shall create a LoanAccount with ACTIVE status after manual disbursement confirmation. |
| FR-SA-019 | The system shall track Salary Advance repayment and settlement status. |
| FR-SA-020 | The system shall validate and store imported Partner Employee records before they are used for employee verification. |

### 9.5 Unsecured Consumer Loan

| ID | Requirement |
| -- | ----------- |
| FR-UCL-001 | The system shall allow customers with complete profiles to select Unsecured Consumer Loan. |
| FR-UCL-002 | The system shall capture requested amount and requested term. |
| FR-UCL-003 | The system shall allow customers to upload income and employment documents required by product policy. |
| FR-UCL-004 | The system shall validate requested amount, term, profile completion, and required document checklist. |
| FR-UCL-005 | The system shall support Loan Officer review of customer profile, documents, and basic repayment capacity. |
| FR-UCL-006 | The system shall support Approver approval, rejection, or return for revision. |
| FR-UCL-007 | The system shall require customer acceptance of approved terms before contract or disbursement steps. |
| FR-UCL-008 | The system shall support manual disbursement confirmation by the Accounting Officer. |
| FR-UCL-009 | The system shall generate the final repayment schedule after disbursement is confirmed. |
| FR-UCL-010 | The system shall track the active loan account, repayment status, and settlement status. |
| FR-UCL-011 | The system shall support preparation or upload of contract and disbursement documents after customer acceptance. |

### 9.6 Collateral Loan

| ID | Requirement |
| -- | ----------- |
| FR-CL-001 | The system shall allow customers with complete profiles to select Collateral Loan. |
| FR-CL-002 | The system shall capture requested amount and requested term. |
| FR-CL-003 | The system shall capture collateral information required by product policy. |
| FR-CL-004 | The system shall allow customers to upload collateral ownership and supporting documents. |
| FR-CL-005 | The system shall validate requested amount, term, and required collateral information. |
| FR-CL-006 | The system shall support manual collateral review by a Loan Officer. |
| FR-CL-007 | The system shall allow the Loan Officer to record collateral assessment notes. |
| FR-CL-008 | The system shall support Approver approval, rejection, or return for revision. |
| FR-CL-009 | The system shall require customer acceptance of approved terms before contract or disbursement steps. |
| FR-CL-010 | The system shall support manual disbursement confirmation by the Accounting Officer. |
| FR-CL-011 | The system shall generate the final repayment schedule after disbursement is confirmed. |
| FR-CL-012 | The system shall track the active loan account, repayment status, and settlement status. |
| FR-CL-013 | The system shall support preparation or upload of contract and disbursement documents after customer acceptance. |

### 9.7 Document Management

| ID | Requirement |
| -- | ----------- |
| FR-DOC-001 | The system shall allow customers and authorized back-office users to upload required documents. |
| FR-DOC-002 | The system shall associate uploaded documents with customer, loan application, collateral, or contract records. |
| FR-DOC-003 | The system shall store document type, upload timestamp, uploader, and review status. |
| FR-DOC-004 | The system shall validate document checklist completion based on the selected loan product. |
| FR-DOC-005 | The system shall support manual document review and rejection reason capture. |
| FR-DOC-006 | The system shall allow applications with missing or rejected documents to be returned for revision. |
| FR-DOC-007 | The system shall track document review status using `NOT_REQUIRED`, `PENDING_UPLOAD`, `UPLOADED`, `UNDER_REVIEW`, `ACCEPTED`, `REJECTED`, and `EXPIRED`. |

### 9.8 Review and Approval

| ID | Requirement |
| -- | ----------- |
| FR-APR-001 | The system shall allow Loan Officers to review submitted applications. |
| FR-APR-002 | The system shall allow Loan Officers to recommend approval, recommend rejection, or return applications for revision. |
| FR-APR-003 | The system shall allow Approvers to approve, reject, or return applications. |
| FR-APR-004 | The system shall require rejection and return reasons where applicable. |
| FR-APR-005 | The system shall keep Loan Officer review and Approver decision responsibilities separate. |
| FR-APR-006 | The system shall support maker-checker control between review and approval. |
| FR-APR-007 | The system shall record all review and approval actions in the audit trail. |

### 9.9 Customer Acceptance and Contract Preparation

| ID | Requirement |
| -- | ----------- |
| FR-OFFER-001 | The system shall present approved terms to the customer after approval. |
| FR-OFFER-002 | The system shall allow the customer to accept or decline approved terms. |
| FR-OFFER-003 | The system shall prevent contract preparation and disbursement if the customer has not accepted approved terms. |
| FR-OFFER-004 | The system shall support contract and disbursement document preparation after customer acceptance. |
| FR-OFFER-005 | The system shall support uploaded signed documents or manual confirmation for MVP document handling. |
| FR-OFFER-006 | The system shall display approved amount, approved term, interest rate, repayment method, estimated installment amount, provisional repayment schedule, fees, and conditions where applicable. |

### 9.10 Disbursement

| ID | Requirement |
| -- | ----------- |
| FR-DIS-001 | The system shall move approved and accepted applications to disbursement preparation after required documents are complete. |
| FR-DIS-002 | The system shall store customer bank account information for manual disbursement confirmation tracking. |
| FR-DIS-003 | The system shall allow only authorized Accounting Officers to mark disbursement as completed. |
| FR-DIS-004 | The system shall keep approval and disbursement as separate responsibilities. |
| FR-DIS-005 | The system shall create a LoanAccount with ACTIVE status after manual disbursement confirmation. |
| FR-DIS-006 | The system shall record disbursement confirmation in the audit trail. |

### 9.11 Repayment Tracking

| ID | Requirement |
| -- | ----------- |
| FR-REP-001 | The system shall generate a final repayment schedule after disbursement confirmation. |
| FR-REP-002 | The system shall track scheduled due date, amount due, amount paid, outstanding balance, and repayment status. |
| FR-REP-003 | The system shall allow authorized users to manually record or confirm repayment updates. |
| FR-REP-004 | The system shall mark loans as overdue when repayment is not completed by the due date. |
| FR-REP-005 | The system shall mark loans as settled after full repayment or approved settlement. |
| FR-REP-006 | The system shall prevent new Salary Advance requests when the customer has blocking overdue Salary Advance exposure. |
| FR-REP-007 | The system shall track repayment status using `NOT_DUE`, `DUE`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`, and `SETTLED`. |

### 9.12 Back-Office Web Portal

| ID | Requirement |
| -- | ----------- |
| FR-BO-001 | The system shall provide a Back-Office Web Portal for internal operational users. |
| FR-BO-002 | The Back-Office Web Portal shall allow authorized users to view customer and loan application information. |
| FR-BO-003 | The Back-Office Web Portal shall allow Back-Office Admins to manage the product catalog. |
| FR-BO-004 | The Back-Office Web Portal shall allow Back-Office Admins to manage Partner Companies and Partner Employee imports. |
| FR-BO-005 | The Back-Office Web Portal shall allow Loan Officers to review applications and documents. |
| FR-BO-006 | The Back-Office Web Portal shall allow Approvers to make approval decisions. |
| FR-BO-007 | The Back-Office Web Portal shall allow Accounting Officers to perform manual disbursement confirmation and repayment updates. |
| FR-BO-008 | The Back-Office Web Portal shall allow authorized users to view audit events. |

### 9.13 Customer Web Portal

| ID | Requirement |
| -- | ----------- |
| FR-CWEB-001 | The system shall provide a Customer Web Portal for customer-facing workflows. |
| FR-CWEB-002 | The Customer Web Portal shall allow customers to register and log in. |
| FR-CWEB-003 | The Customer Web Portal shall allow customers to complete and update profile information. |
| FR-CWEB-004 | The Customer Web Portal shall allow customers to view active loan products. |
| FR-CWEB-005 | The Customer Web Portal shall allow customers to submit applications for supported products. |
| FR-CWEB-006 | The Customer Web Portal shall allow customers to upload required documents. |
| FR-CWEB-007 | The Customer Web Portal shall allow customers to accept or decline approved terms. |
| FR-CWEB-008 | The Customer Web Portal shall allow customers to view application, loan, repayment, settlement, and closure status. |

### 9.14 Audit Trail and Logging

| ID | Requirement |
| -- | ----------- |
| FR-AUD-001 | The system shall record audit trail entries for important business actions. |
| FR-AUD-002 | The system shall record every important LoanApplication status transition. |
| FR-AUD-003 | The system shall record every important LoanAccount status transition. |
| FR-AUD-004 | The system shall record actor, action, timestamp, affected entity, previous status, new status, and reason where applicable. |
| FR-AUD-005 | The system shall prevent audit records from being modified by normal users. |
| FR-AUD-006 | The system shall separately audit review, approval, customer acceptance, document readiness, manual disbursement confirmation, and repayment updates. |
| FR-AUD-007 | The system shall support maker-checker traceability for review and approval actions. |

---

## 10. Business Rules

| ID | Rule |
| -- | ---- |
| BR-001 | A customer can submit applications only for active loan products. |
| BR-002 | A customer profile must be complete before loan application submission. |
| BR-003 | A loan application must pass product-specific validation before submission. |
| BR-004 | Salary Advance requires successful employee verification or approved manual review before proceeding. |
| BR-005 | Salary Advance requested amount must be less than or equal to the Available Salary Advance limit. |
| BR-006 | Inactive Partner Companies or inactive Partner Employee records cannot be used for normal Salary Advance eligibility. |
| BR-007 | A customer cannot create a new Salary Advance request when blocking overdue Salary Advance exposure exists. |
| BR-008 | Unsecured Consumer Loan requires income and employment document review but does not require collateral information. |
| BR-009 | Collateral Loan requires collateral information and collateral ownership or supporting documents. |
| BR-010 | Loan Officer review and Approver decision must be performed as separate responsibilities. |
| BR-011 | Approval and disbursement must be performed as separate responsibilities. |
| BR-012 | Approved terms require customer acceptance before contract preparation and disbursement. |
| BR-013 | Disbursement can be marked completed only after approval, customer acceptance, and required document readiness. |
| BR-014 | A LoanAccount is created with ACTIVE status only after manual disbursement confirmation. |
| BR-015 | Rejection, return for revision, and manual override actions must include a reason. |
| BR-016 | Every important status transition must create an audit trail record. |

---

## 11. Data Entities

Initial conceptual entities identified from the functional requirements. These are not final database table definitions.

* User;
* Customer;
* BackOfficeUser;
* LoanProduct;
* LoanProductPolicy;
* LoanApplication;
* LoanAccount;
* PartnerCompany;
* PartnerEmployee;
* PartnerEmployeeImportBatch;
* EmployeeVerification;
* SalaryAdvanceLimit;
* ProductVerificationResult;
* Document;
* DocumentChecklist;
* Collateral;
* ReviewRecommendation;
* ApprovalDecision;
* CustomerAcceptance;
* ContractDocumentRecord;
* DisbursementRecord;
* RepaymentSchedule;
* RepaymentRecord;
* AuditEvent.

---

## 12. Non-Functional Requirements

| Category | Requirement |
| -------- | ----------- |
| Security | The system must enforce role-based access control for customer and back-office actions. |
| Auditability | Important business actions and status transitions must be traceable. |
| Data Integrity | Loan status transitions must be controlled by business rules. |
| Reliability | Failed operations should not create inconsistent loan application, document, disbursement, or repayment states. |
| Maintainability | Business modules should remain organized by bounded context within a modular monolith. |
| Privacy | Personal, employment, financial, and collateral data must not be exposed unnecessarily. |
| Extensibility | New loan products should be supported through product-specific policy components inside the generic lending core. |

---

## 13. Assumptions

* Meridian uses fictional partner companies, customers, employees, collateral, products, and loan data.
* MER-BIZ-002 is the detailed workflow source of truth for product models and workflow behavior.
* The MVP uses one backend system and one database.
* Back-Office Web Portal and Customer Web Portal call the same backend APIs.
* Mobile app functionality is deferred to a later phase.
* Banking, payroll, employer, credit bureau, payment gateway, SMS OTP, and biometric integrations are simulated or excluded in the MVP.
* Salary Advance is deeper than the other loan products in the MVP.
* Unsecured Consumer Loan and Collateral Loan are intentionally streamlined.
* Manual review, manual document confirmation, manual disbursement confirmation, and manual repayment updates are acceptable for MVP scope.
* Salary Advance employee data is imported monthly.

---

## 14. Open Questions

| ID | Question |
| -- | -------- |
| Q-001 | Which exact documents are required for each loan product in the MVP catalog? |
| Q-002 | Should Salary Advance limit be recalculated only after monthly import or also after each repayment update? |
| Q-003 | Should customer bank account information be editable after submission? |
| Q-004 | Should approval require one approver or configurable multi-level approval in the MVP? |
| Q-005 | Which collateral types should be selectable in the first MVP release? |
| Q-006 | Should Collateral Loan use a simple loan-to-value rule or rely fully on manual review in the MVP? |
| Q-007 | Should repayment updates be manually entered only, or should demo data simulate scheduled repayments automatically? |

---

## 15. MVP Priority

### Must Have

* one backend and one database;
* Customer Web Portal;
* Back-Office Web Portal;
* customer profile completion;
* loan product catalog;
* common loan application workflow;
* Salary Advance workflow;
* Partner Company management;
* monthly Partner Employee import;
* employee verification;
* Salary Advance limit calculation;
* Unsecured Consumer Loan workflow;
* Collateral Loan workflow;
* document checklist validation;
* Loan Officer review;
* Approver decision;
* customer acceptance;
* manual disbursement confirmation;
* loan activation;
* final repayment schedule generation;
* repayment tracking;
* audit trail.

### Should Have

* product activation and deactivation;
* customer application history;
* back-office application queue;
* collateral assessment notes;
* import batch tracking;
* repayment status update screens;
* simple dashboard views.

### Could Have

* notification service;
* OCR-assisted document extraction;
* simple analytics dashboards;
* lightweight mobile app after the Customer Web Portal is stable.

### Won't Have in MVP

* No real bank transfer;
* No real payroll integration;
* No real employer API integration;
* No real payment gateway;
* No real SMS OTP;
* No biometric login;
* No real credit bureau integration;
* No full collateral valuation/legal enforcement;
* No double-entry ledger;
* No production compliance workflow;
* No microservices;
* No full mobile app as MVP.
