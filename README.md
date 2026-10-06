# Healthyfy

Personal calorie-tracking Android app (HealthifyMe-style), built with Kotlin + Jetpack Compose + Room.

## Features
- Daily dashboard: calorie ring, macros, meals grouped by Breakfast/Lunch/Dinner/Snack, day navigation
- Add food by **text** ("2 roti and 1 cup dal"), **photo** (camera/gallery), or manually
- Calorie estimation chain (all free):
  1. **Gemini free tier** (your own key from aistudio.google.com/apikey) – text + photo, structured JSON output
  2. **Offline table** (`assets/foods.json`, Indian + common foods) – works with no network/key
  3. **Open Food Facts** – packaged foods, no key
- Calorie goal in Settings; all data stays on-device (Room)

## Build
Open in Android Studio and Run, or push to GitHub: the *Build APK* workflow uploads `app-debug.apk` as an artifact.
Locally: `gradle assembleDebug` (needs Android SDK 35, JDK 17).

## Roadmap
- Fully on-device AI (MediaPipe LLM Inference with Gemma / ML Kit GenAI) as another estimator
- Barcode scan (ML Kit) → Open Food Facts, water/weight tracking, weekly charts
