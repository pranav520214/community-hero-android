# Community Hero (CivicDex Android) Architecture

## 1. Architectural Overview

```text
 ┌────────────────────────────────────────┐
 │        Citizen Reporting Mobile UI     │
 │        (Jetpack Compose, Kotlin)       │
 └───────────────────┬────────────────────┘
                     │ Issue Capture (Photo + GPS)
                     ▼
 ┌────────────────────────────────────────┐
 │        Gemini 1.5 Flash Client         │
 │  • Multimodal Civic Classification     │
 │  • Severity & Pothole/Waste Analysis   │
 └───────────────────┬────────────────────┘
                     │ Structured Audit Record
                     ▼
 ┌────────────────────────────────────────┐
 │      Firebase Cloud Firestore / Storage│
 │  • Rule-Enforced Incident Tracking     │
 │  • Real-Time Municipal Feed Sync       │
 └────────────────────────────────────────┘
```

## 2. Security & Credentials
- All API keys must be loaded via `.env` or BuildConfig variables; never committed to source control.
- Firestore and Firebase Storage security rules enforce user authentication and ownership integrity.
