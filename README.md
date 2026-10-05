# 🏛️ Community Hero (CivicDex Mobile)

> Citizen issue reporting, AI-powered municipal damage triage, and civic accountability Android application built with Kotlin, Jetpack, and Gemini Vision.

[![Platform: Android](https://img.shields.io/badge/Platform-Android%2014+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Language: Kotlin](https://img.shields.io/badge/Language-Kotlin%202.0-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![AI: Gemini 1.5 Flash](https://img.shields.io/badge/AI-Gemini%201.5%20Flash-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://ai.google.dev/)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)](LICENSE)

---

## ⚡ Overview

**Community Hero** empowers citizens to report hyper-local civic infrastructure issues—such as road hazards, broken streetlights, water pipeline leaks, and waste mismanagement. Leveraging on-device camera input and multimodal **Gemini 1.5 Flash** vision analysis, the app automatically categorizes incidents, estimates severity, and tags GPS coordinates before submitting to local municipal departments.

---

## 🏛️ System Architecture

```text
 ┌──────────────────────┐      ┌────────────────────────┐      ┌──────────────────────┐
 │ Citizen Camera / GPS │ ──►  │ Gemini 1.5 Flash Vision │ ──►  │ Firebase Firestore   │
 │ Jetpack Compose UI   │      │ Automatic Triage & Tag │      │ Municipal Dashboard  │
 └──────────────────────┘      └────────────────────────┘      └──────────────────────┘
```

For detailed specifications, see [docs/architecture.md](docs/architecture.md) and [CivicDex_Advanced_Architecture.md](CivicDex_Advanced_Architecture.md).

---

## 🚀 Setup & Local Development

### Prerequisites
- [Android Studio](https://developer.android.com/studio) (Koala or newer)
- Android SDK 34+
- Gemini API Key from [Google AI Studio](https://aistudio.google.com/apikey)

### Running the App
1. Open the project folder in Android Studio.
2. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```
3. Set your API key in `.env`:
   ```env
   GEMINI_API_KEY=YOUR_GEMINI_API_KEY_HERE
   ```
4. Build and run on an Android emulator or physical device.

---

## 📄 License

Distributed under the [Apache-2.0 License](LICENSE).
