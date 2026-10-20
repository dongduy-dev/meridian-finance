# MER-DEP-001 — Identity Bootstrap and Public Demo Boundary

## 1. Deployment Policy

Meridian is a public software-engineering portfolio/demo, not a real financial service. Customer Web and Customer registration are public. Customer uploads remain enabled. Internal Web may be internet reachable; Meridian authentication and RBAC authorize its operations without an additional Staff access gate.

Visitors must use fictional profile, identity, banking, employment, payroll, financial, and document information. Submitted demo information must still be treated as private. Registration still requires access to an email inbox for verification and recovery. Customer Web links `/login` and `/register` to the public `/demo-privacy` notice. The operator must provide a deletion/contact direction on the portfolio or deployment page linking to Meridian. Demo data may be manually reset or deleted under the authorized procedures in [MER-DEP-003](MER-DEP-003-portfolio-operations-and-recovery.md). Staff credentials and setup links are shared privately only with trusted reviewers and must never appear in source, README, portfolio copy, or public deployment documentation.

## 2. Historical Credential Retirement

Flyway V73 targets exactly the five User IDs originally inserted by V13. It preserves those Users, their role assignments, Customer mapping, and business/audit references. It sets each User to `DISABLED`, replaces the password hash with BCrypt of a separately generated 256-bit random secret, increments `authorization_version`, and clears temporary login-protection state. PostgreSQL generates and discards the raw placeholder secret inside the migration.

V73 revokes all unrevoked refresh-token sessions, including consumed predecessors. It revokes outstanding password-reset/setup and email-verification tokens while preserving consumed and previously revoked recovery history. Revocation timestamps cannot precede token issuance. Protected access tokens issued with the previous authorization version fail the existing persisted freshness check. Reactivation alone cannot restore the historical password or old token authority.

Historical migrations remain unchanged. Flyway applies V73 on both clean installation and upgrade; subsequent restarts do not repeat it. Do not reactivate a historical User as a bootstrap shortcut.

## 3. Establish the First Back-Office Admin

Bootstrap is a startup input adapter enabled only by operator configuration. It has no HTTP endpoint and accepts no password. It creates an ordinary email-verified `STAFF` User with only the predefined `BACK_OFFICE_ADMIN` role through the existing Internal User provisioning transaction. Creation, role assignment, digest-only password-setup state, and SYSTEM-attributed business audit commit together. Notification sends the one-time setup link after commit through the existing Staff setup email contract.

Before running bootstrap, configure the normal database, encryption/JWT keys, SMTP transport, and Internal Web base URL. [MER-DEP-002 §3.1](MER-DEP-002-cloudflare-edge-and-aws-runtime.md#31-smtp-provider-configuration) owns production mail configuration. Deployed startup/recreation requires explicit operator authorization and the release, backup, and readiness gates in [MER-DEP-003](MER-DEP-003-portfolio-operations-and-recovery.md); the local commands below do not replace them. Use an operator-controlled inbox. For local development, Compose sends the setup email to Mailpit.

1. In the private runtime environment or local ignored `meridian-platform/.env`, set `MERIDIAN_ADMIN_BOOTSTRAP_ENABLED=true`, `MERIDIAN_ADMIN_BOOTSTRAP_EMAIL` to the intended operator inbox, and `MERIDIAN_ADMIN_BOOTSTRAP_DISPLAY_NAME` to the intended display name. Email and display name must satisfy the existing Internal User creation validation. No password or raw token is configured.
2. Start the backend. For local Compose, run `docker compose up --build -d` from `meridian-platform`. Flyway runs before bootstrap.
3. Read the Staff password-setup email privately. Open its Internal Web `/set-password` link and choose a password. Later Staff recovery uses `/reset-password`. Setup tokens use the configured password-reset lifetime, 30 minutes by default, and are single-use.
4. Remove the bootstrap email/display-name configuration and set `MERIDIAN_ADMIN_BOOTSTRAP_ENABLED=false`. Recreate the local backend with `docker compose up -d --force-recreate meridian-platform`, or restart the deployed backend with the disabled configuration.
5. Log into Internal Web with the new account and open `/admin/users`.

Bootstrap serializes competing starts with a PostgreSQL transaction advisory lock. It refuses to create a second account if any operator-created Back-Office Admin exists, including an inactive one, or if a historical Back-Office Admin is active. A duplicate email fails without changing that account. It never resets an existing password or resends setup on restart. Leaving bootstrap enabled after successful creation causes the next startup to fail with `ADMINISTRATOR_ALREADY_ESTABLISHED`; disable it and use ordinary administration/recovery.

Missing or invalid bootstrap configuration fails startup before account creation. A transaction failure rolls back the account, role, setup token, and audit. If email delivery fails after commit, startup fails with a safe recovery instruction; the account remains created. Disable bootstrap, correct SMTP configuration, restart normally, and use Internal Web **Forgot password** for the configured account. The same recovery path replaces an expired or lost initial setup token. Do not retry bootstrap to reset the account. An inactive established administrator requires deliberate operator recovery or another authorized administrator; bootstrap does not bypass its status.

## 4. Subsequent Staff and Customer Accounts

The first Back-Office Admin uses `/admin/users` to create normal Loan Officer, Approver, Accounting Officer, and Back-Office Admin accounts with the corresponding predefined role. Each Staff user receives the existing password-setup email and chooses their own password. Administration can resend setup and manage status/role assignments through the maintained Identity contracts. There is no special reviewer account type or shared deployment password.

Customers continue through `register → verification pending → email verification → login`. Registration rate limiting, verification, login protection, refresh rotation, ownership checks, and Customer/Staff portal separation retain their existing behavior. Local developers use the same bootstrap and Mailpit setup/recovery paths; no development profile restores the V13 password.

## 5. Validation and Deployment Ownership

PostgreSQL tests exercise clean V1–V73 migration, V72 upgrade, reference preservation, known-password rejection after reactivation, stale access-token rejection, refresh/setup revocation, repeated migration, and concurrent first-admin bootstrap. Bootstrap adapter/service tests verify opt-in configuration and safe delivery-failure instructions. Customer auth tests exercise the public notice links while retaining ordinary registration, validation, and password visibility behavior.

This document owns deployment Identity, bootstrap, and the public demo boundary. [MER-DEP-002](MER-DEP-002-cloudflare-edge-and-aws-runtime.md) owns Cloudflare/AWS edge and runtime boundaries, private ClamAV placement, and SMTP-provider configuration. [MER-DEP-003](MER-DEP-003-portfolio-operations-and-recovery.md) owns readiness, release identity, backup/recovery, and reset operations. These portfolio controls do not establish a real financial production service or its broader storage lifecycle, retention, legal-hold, and key-governance requirements.
