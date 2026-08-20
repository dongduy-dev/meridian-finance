# Meridian Capital

### Investment • Financial Services • Technology

**EST. June 9, 2026**

---

# Meridian Finance

### Digital Lending & Financial Solutions

Meridian Finance is the fictional financial-technology brand under **Meridian Capital**. It represents an independent portfolio project focused on transparent, well-governed digital lending.

---

# Project Meridian

## Product

### Meridian Lending Platform

Meridian Lending Platform is the backend product developed as Project Meridian. It supports three lending products through one coherent lifecycle:

- Salary Advance;
- Unsecured Consumer Loan;
- Collateral Loan.

The platform covers Customer readiness and origination, product-specific evidence and verification, document review and correction, independent recommendation and approval, approved offers, operational contracts, manual disbursement and activation, repayment servicing, settlement, and closure.

Salary Advance additionally integrates Partner Company employment eligibility and product-specific exposure management. Unsecured Consumer Loan and Collateral Loan use their own verification evidence while sharing the common lending lifecycle.

## Mission

To make short-term lending workflows clear, auditable, and operationally reliable while keeping product rules, customer protection, and financial evidence explicit.

## Product Principles

- One modular lending platform with product-specific behavior inside the Loan domain.
- Customer-owned journeys with explicit Staff authority and maker-checker controls.
- Immutable evidence for accepted offers, contracts, schedules, payments, and terminal outcomes.
- Purpose-limited handling of identity, bank-account, employment, document, and assessment data.
- Manual review remains authoritative; any future OCR assistance is advisory.

## Technical Identity

Meridian is a Java and Spring Boot modular monolith backed by PostgreSQL and Flyway. It uses JWT-based authentication, role and permission authorization, explicit bounded-context ownership, and an append-only business audit trail.

Exact versions, build instructions, executable capabilities, and delivery status are maintained in the repository build, README, product specifications, and follow-up register rather than in this brand overview.

## Organization Structure

```text
Meridian Capital
│
└── Meridian Finance
    │
    └── Meridian Lending Platform
        (Project Meridian)
```

---

> "Helping people navigate their financial journey."
>
> — Meridian Finance
