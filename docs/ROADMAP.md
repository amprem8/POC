# xVault — Product Roadmap

> **Project:** xVault (Internal Password Manager)  
> **Created:** June 18, 2026  
> **Last Updated:** July 8, 2026  
> **Status:** POC → Production  

---

## 🎯 Vision

A cross-platform enterprise credential manager exclusively for Comcast employees, supporting **Android, iOS, macOS, and Windows**. All platforms share a single reliable backend (**xVault**) for encrypted password storage, with biometric authentication, Comcast SSO (OpenID Connect / Azure AD), passkey support, secure credential sharing (Pneumatic Tube), and intelligent security recommendations.

---

## 📐 Architecture Overview

| Layer                | Technology                                                           |
|----------------------|----------------------------------------------------------------------|
| **UI (Android/iOS)** | Compose Multiplatform (KMP)                                          |
| **UI (Windows/Mac)** | Compose Desktop (JVM target)                                         |
| **Shared Logic**     | Kotlin Multiplatform (`shared` module)                               |
| **Backend**          | xVault — `https://prod.xvault.comcast.com` (single source of truth)  |
| **Storage**          | xVault nook engine (per-user encrypted secrets)                      |
| **Auth**             | Comcast SSO (Azure AD OIDC + PKCE) + Biometrics                      |
| **Infra**            | No AWS required — xVault handles all backend needs                   |

### xVault API Endpoints Used

| Function                  | xVault API                                                    |
|---------------------------|---------------------------------------------------------------|
| SSO / OIDC Auth           | `POST /xvault/v1/auth/{oidc-path}`                           |
| Token Lifecycle           | `/auth/token/create`, `/renew-self`, `/revoke-self`           |
| User Entity (Object ID)  | `PUT /xvault/v1/identity/entity/name/{email}`                 |
| Store Credentials         | `PUT /xvault/v1/nook/users/{objectId}/credentials/{id}`      |
| Read Credentials          | `GET /xvault/v1/nook/users/{objectId}/credentials/{id}`      |
| List Credentials (Sync)   | `GET /xvault/v1/nook/users/{objectId}/credentials?list=true` |
| Delete Credentials        | `DELETE /xvault/v1/nook/users/{objectId}/credentials/{id}`   |
| Send (Pneumatic Tube)     | `POST /xvault/v1/sys/wrapping/wrap` + `/unwrap`              |
| Trash / Soft Delete       | `PUT /xvault/v1/nook/users/{objectId}/trash/{id}`            |
| Policies / ACL            | `PUT /xvault/v1/sys/policy/{name}`                           |
| Namespace Isolation        | `PUT /xvault/v1/sys/namespaces/{ns}`                         |

### xVault Environments

| Environment | Host                                  | Portal                                    |
|-------------|---------------------------------------|-------------------------------------------|
| Production  | `https://prod.xvault.comcast.com`     | `https://portal.xvault.comcast.com`       |
| Staging     | `https://stage.xvault.comcast.com`    | `https://portal-stage.xvault.comcast.com` |

---

## 🔐 Core Features

### 1. Authentication & Enrollment
- First-time: Biometric fingerprint enrollment → Comcast SSO sign-in (Azure AD OIDC with PKCE) → Object ID assigned via xVault entity
- Subsequent logins: Biometric OR SSO (user's choice)
- SSO redirect URI: `http://localhost:8769/auth/callback`
- SSO token stored locally; email extracted from ID token claims
- Object ID is permanent per-user across all platforms

### 2. xVault Backend (Single Backend — No AWS Required)
- Sole encrypted credential storage via nook/cubbyhole engine
- User account management via identity entities & Object ID
- Sync across all 4 platform clients via nook path: `/users/{objectId}/credentials/`
- Zero-knowledge architecture (server stores encrypted blobs only)
- KEK/DEK encryption key management built-in

### 3. Passkey Support
- FIDO2/WebAuthn passkey creation & storage
- Cross-platform passkey sync via xVault nook: `/users/{objectId}/passkeys/`

### 4. Send (Pneumatic Tube)
- End-to-end encrypted credential sharing via xVault wrapping API
- Sender wraps credential → `POST /sys/wrapping/wrap` with TTL → gets one-time token
- Recipient unwraps → `POST /sys/wrapping/unwrap` with token → token burned after use
- Audit trail on xVault

### 5. Password Generator
- Customizable length, symbols, numbers, ambiguous characters
- Strength meter (entropy calculation)
- One-tap save to vault
- Pure client-side logic (no backend needed)

### 6. Security Recommendations
- Breach detection (Have I Been Pwned k-anonymity API — direct call, no AWS)
- Weak/reused/old password alerts
- Prioritized actionable list

### 7. Deleted Items (Trash)
- Soft-delete: move to xVault trash path `/users/{objectId}/trash/{id}` with 30-day retention
- Restore or permanent purge
- Synced via xVault

---

## 🗓️ Phased Rollout

| Phase       | Platform | Timeline | Priority    |
|-------------|----------|----------|-------------|
| **Phase 0** | Android  | Q3 2026  | 🔴 Critical |
| **Phase 1** | Windows  | Q4 2026  | 🟠 High     |
| **Phase 2** | macOS    | Q1 2027  | 🟡 Medium   |
| **Phase 3** | iOS      | Q2 2027  | 🟡 Medium   |

---

## Phase 0 — Android (Current)

**Status:** POC in progress  
**Existing:** Autofill service, Credential Provider, Biometric, SSO (dummy), Accessibility service

### Milestones
1. ✅ Autofill Service (`VaultAutofillService`) — websites & apps only (WiFi/system excluded)
2. ✅ Credential Provider Service (Android 14+)
3. ✅ Biometric unlock (fingerprint)
4. ✅ SSO sign-in flow (dummy — Comcast SSO button with toast)
5. ✅ Removed master password, recovery phrase, forgot password flows
6. 🔲 Real Comcast SSO integration (Azure AD OIDC + PKCE via Chrome Custom Tab)
7. 🔲 xVault backend integration (replace local-only encrypted prefs)
8. 🔲 Object ID assignment on registration via xVault identity entity
9. 🔲 Passkey storage & retrieval
10. 🔲 Send (Pneumatic Tube) via xVault wrapping API
11. 🔲 Password Generator
12. 🔲 Security Recommendations engine (HIBP integration)
13. 🔲 Deleted items / Trash

---

## Phase 1 — Windows (Compose Desktop JVM)

### Milestones
1. 🔲 Compose Desktop target (`jvmMain`) with Windows packaging (MSI/MSIX)
2. 🔲 Windows Hello biometric integration (JNI/JNA bridge)
3. 🔲 Browser extension communication (native messaging)
4. 🔲 xVault sync
5. 🔲 All shared features (Send, Generator, Recommendations, Trash)

---

## Phase 2 — macOS (Compose Desktop JVM)

### Milestones
1. 🔲 macOS target with `.app`/`.dmg` packaging
2. 🔲 Touch ID integration (LocalAuthentication framework via JNI)
3. 🔲 Safari extension / Browser native messaging
4. 🔲 xVault sync
5. 🔲 All shared features

---

## Phase 3 — iOS

### Milestones
1. 🔲 Compose Multiplatform iOS (already scaffolded)
2. 🔲 AutoFill Credential Provider extension (already has `AutoFillExtension` folder)
3. 🔲 Face ID / Touch ID via LocalAuthentication
4. 🔲 xVault sync
5. 🔲 All shared features

---

## 🏗️ Authentication Flow

```
┌──────────────────────────────────────────────┐
│         FIRST-TIME SETUP (Signup)             │
├──────────────────────────────────────────────┤
│ 1. Biometric fingerprint enrollment (required)│
│ 2. Comcast SSO sign-in (required)             │
│    └─ Azure AD OIDC + PKCE                   │
│    └─ Chrome Custom Tab / ASWebAuthSession   │
│    └─ Redirect: http://localhost:8769/auth/   │
│ 3. Email extracted from SSO ID token          │
│ 4. xVault entity created → Object ID assigned │
│ 5. SSO token stored locally (permanent)       │
│ 6. Onboarding → Main screen                  │
└──────────────────────────────────────────────┘

┌──────────────────────────────────────────────┐
│         SUBSEQUENT LOGIN                      │
├──────────────────────────────────────────────┤
│ 1. Choose: Biometric OR Comcast SSO          │
│ 2. xVault token validated/renewed            │
│ 3. Credentials synced from xVault            │
│ 4. Main screen                               │
└──────────────────────────────────────────────┘
```

---

## 🔧 Infrastructure Summary

| Component | Solution | AWS? |
|---|---|---|
| Backend / API | xVault (`prod.xvault.comcast.com`) | ❌ |
| Secret Storage | xVault nook engine | ❌ |
| User Identity | xVault identity entities | ❌ |
| Encryption (KEK/DEK) | xVault + CipherTrust | ❌ |
| SSO / Auth | Azure AD (Comcast OIDC) | ❌ |
| Secure Sharing | xVault wrapping API | ❌ |
| Breach Detection | HIBP API (direct HTTPS) | ❌ |
| Push Notifications | Firebase (Android) / APNs (iOS) | ❌ |
| Password Generation | Client-side only | ❌ |
| App Distribution | Play Store / App Store / internal | ❌ |

**AWS is not required for any feature in this roadmap.**

---

## PlantUML Diagrams

| Diagram                     | File                         |
|-----------------------------|------------------------------|
| Overall System Architecture | `architecture_overview.puml` |
| Authentication Flow         | `auth_flow.puml`             |
| Passkey Feature             | `passkey_flow.puml`          |
| Send (Pneumatic Tube)       | `send_flow.puml`             |
| Password Generator          | `generator_flow.puml`        |
| Security Recommendations    | `recommendations_flow.puml`  |
| Deleted Items               | `deleted_items_flow.puml`    |
| Phase 0 - Android           | `phase0_android.puml`        |
| Phase 1 - Windows           | `phase1_windows.puml`        |
| Phase 2 - macOS             | `phase2_macos.puml`          |
| Phase 3 - iOS               | `phase3_ios.puml`            |
