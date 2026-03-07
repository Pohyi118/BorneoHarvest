
```markdown
# 🌾 BorneoHarvest

> **Empowering rural farmers in Borneo with an offline-first, AI-driven agricultural assistant and real-time community disaster alert system.**



BorneoHarvest is an Android mobile application designed to bridge the digital divide for rural farmers. By combining local on-device Machine Learning, Generative AI, and offline-first cloud databases, it provides accessible, hands-free farming guidance and life-saving community disaster alerts—even in areas with highly unstable internet connectivity.

---

## ✨ Core Features

### 📸 1. AI Plant Doctor (Diagnosa Penyakit)
* **On-Device ML:** Uses a custom TensorFlow Lite (TFLite) model to instantly scan and classify crop diseases (e.g., *Penyakit Bintik Perang*) locally on the device without requiring internet.
* **Generative AI Integration:** Connects to Google's Gemini 3.1 Flash API to generate step-by-step, organic treatment plans in Bahasa Melayu.
* **Offline Fallback:** If the network drops, the app gracefully falls back to a hardcoded local database to ensure the farmer still gets immediate, actionable advice.

### 🗣️ 2. Pembantu Tani (Voice-Activated Assistant)
* **Hands-Free Operation:** Farmers working in the fields can ask questions using their voice via Android's Speech-to-Text (`SpeechRecognizer`).
* **AI-Powered Answers:** Gemini processes the query and returns highly practical, organic farming advice tailored to the Southeast Asian climate.
* **Voice Feedback:** The app utilizes Android's Text-to-Speech (TTS) engine (with built-in fail-safes for Malay and Indonesian locales) to read the AI's response out loud, ensuring accessibility for users with lower literacy rates or those busy with manual labor.

### 🚨 3. Community Disaster Hub & Early Warnings
* **Live Weather Analysis:** Integrates the Open-Meteo API to fetch real-time precipitation data and automatically trigger a "Red Alert" UI and Voice Warning if flood risks exceed 80%.
* **Crowdsourced Reporting & Location Tagging:** Farmers can upload photos of local disasters (landslides, floods, fallen trees). The app automatically tags the exact GPS location (e.g., "📍 Mentakab, Pahang") to the report.
* **Extreme Memory Optimization:** Utilizes aggressive Bitmap scaling and Base64 `NO_WRAP` encoding to compress high-resolution camera photos into tiny string payloads, completely preventing Android `OutOfMemory` crashes on lower-end devices.
* **Real-Time Sync (FCM Alternative):** Uses Firestore Snapshot Listeners to mimic a high-tier Push Notification server. When a report is uploaded, it instantly triggers high-priority Heads-Up Notifications, vibration, and Voice Alerts across all active devices in the community—costing $0.00 in backend cloud functions.

---

## 🏗️ Technical Architecture & "Offline-First" Design

Rural areas often suffer from intermittent connectivity. BorneoHarvest is built with a strict **Offline-First** philosophy:

1. **Firestore Local Cache:** All community disaster reports are written to the local device cache first. If the user is offline, the UI updates instantly, and Firebase silently queues the data to sync the moment a 4G connection is restored.
2. **SharedPreferences Weather Backup:** Live API weather data and city locations are continuously saved locally. If the app boots offline, it pulls the most recent data to ensure the dashboard never appears "broken."
3. **No-Crash UI Logic:** Extensive `try-catch` blocks, active fallback triggers, and dedicated Volley Retry Policies prevent the app from freezing or crashing when asynchronous cloud tasks time out.

---

## 🛠️ Tech Stack

* **Platform:** Android (Java)
* **Backend / Database:** Firebase Cloud Firestore (Real-time NoSQL)
* **Artificial Intelligence:** * Google Gemini API (`gemini-3-flash-preview`)
  * TensorFlow Lite (Image Classification)
* **APIs:** Open-Meteo REST API (via Android Volley)
* **Hardware Integrations:** CameraX / FileProvider, GPS (FusedLocationProvider), Text-to-Speech (TTS), SpeechRecognizer.

---

## 🚀 Installation & Setup

To run this project locally, you will need to provide your own API keys and Firebase configuration.

1. **Clone the repository:**
   ```bash
   git clone [https://github.com/yourusername/BorneoHarvest.git](https://github.com/yourusername/BorneoHarvest.git)

```

2. **Open the project in Android Studio.**
3. **Configure Gemini API:**
* Open the `local.properties` file in the root directory.
* Add your Gemini API key:
```properties
GEMINI_API_KEY="your_api_key_here"

```




4. **Configure Firebase:**
* Create a Firebase project and set up a Firestore Database.
* Download the `google-services.json` file from your Firebase console.
* Place the file in the `app/` directory. *(Note: This file is git-ignored for security).*


5. **Build and Run:**
* Sync the project with Gradle files and deploy to a physical Android device or Emulator (API 26+ recommended).



---

## 🔮 Future Roadmap

* **IoT Integration:** Connecting the app to cheap, field-deployed soil moisture sensors.
* **Marketplace Module:** Allowing rural farmers to sell their healthy crops directly to buyers without middlemen.
* **Background Push Server:** Migrating the real-time sync listener to a Firebase Node.js Cloud Function (FCM) to wake up fully closed devices.

---

*Developed for the Borneo Hackathon 2026.*

```


```
