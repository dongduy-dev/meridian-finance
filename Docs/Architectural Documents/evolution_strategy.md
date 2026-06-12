# Evolution Strategy

Meridian is intentionally built as a Modular Monolith.

This approach provides:

- Faster development
- Simpler deployment
- Easier debugging
- Lower operational complexity

Modules are isolated through ports and interfaces, allowing selected capabilities to be extracted into independent services if future requirements justify the additional complexity.

Potential extraction candidates include:

| Module | Reason |
| --- | --- |
| Identity & Access | Low coupling, clear API boundary |
| Notification | Event-driven and largely independent |
| Document & OCR | Separate scaling requirements |
| Customer Management | Potential integration point with external systems |

Loan Origination and Approval Workflow remain part of the monolith for as long as possible because they contain the core business logic and require strong transactional consistency.