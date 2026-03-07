

<h1 align="center">🌾 BorneoHarvest</h1>

<p align="center">
  <strong>Empowering rural farmers in Southeast Asia with an offline-first, AI-driven agricultural assistant and real-time community disaster alert system.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Java-007396?style=for-the-badge&logo=java&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/Database-Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black" alt="Firebase" />
  <img src="https://img.shields.io/badge/AI-Google_Gemini-8E75B2?style=for-the-badge&logo=googleplay&logoColor=white" alt="Gemini AI" />
  <img src="https://img.shields.io/badge/ML-TensorFlow_Lite-FF6F00?style=for-the-badge&logo=tensorflow&logoColor=white" alt="TensorFlow Lite" />
</p>

---

## 📖 Overview
BorneoHarvest bridges the digital divide for rural agricultural communities. By combining on-device Machine Learning, Generative AI, and edge-caching cloud databases, it provides accessible, hands-free farming guidance and life-saving community disaster alerts—**even in areas with unstable or zero internet connectivity.**

---

## ✨ Core Features

### 📸 1. AI Plant Doctor (Diagnosa Penyakit)
* **On-Device ML:** Instantly scans and classifies crop diseases (e.g., *Penyakit Bintik Perang*) locally on the device using a custom TensorFlow Lite (TFLite) model—no internet required.
* **Generative AI Integration:** Connects to Google's **Gemini 3.1 Flash API** to generate step-by-step, organic treatment plans in Bahasa Melayu.
* **Offline Fallback:** If the network drops, the app gracefully falls back to a hardcoded local database to ensure the farmer still gets immediate, actionable advice.

### 🗣️ 2. Pembantu Tani (Voice-Activated Assistant)
* **Hands-Free Operation:** Farmers in the field can ask questions using their voice via Android's `SpeechRecognizer`.
* **AI-Powered Answers:** Gemini processes the query and returns highly practical, organic farming advice tailored to the Southeast Asian climate.
* **Voice Feedback:** Utilizes Android's Text-to-Speech (TTS) engine (with built-in fail-safes for Malay and Indonesian locales) to read the AI's response out loud, ensuring accessibility for users with lower literacy rates or those performing manual labor.

### 🚨 3. Community Disaster Hub & Early Warnings
* **Live Weather Analysis:** Integrates the Open-Meteo API to fetch real-time precipitation data, automatically triggering a Red Alert UI and Voice Warning if flood risks exceed 80%.
* **Crowdsourced Reporting:** Farmers can upload visual proof of local disasters (landslides, floods, fallen trees). The app automatically stamps the exact GPS location (e.g., `📍 Mentakab, Pahang`) to the report.
* **Extreme Memory Optimization:** Utilizes aggressive Bitmap scaling and Base64 `NO_WRAP` encoding to compress high-resolution photos into tiny string payloads, preventing `OutOfMemory` crashes on low-end devices.
* **Real-Time Sync:** Uses Firestore Snapshot Listeners to mimic a high-tier Push Notification server. A new report instantly triggers Heads-Up Notifications, vibration, and Voice Alerts across all active devices in the community—**costing $0.00 in backend server fees.**

---

## 🏗️ "Offline-First" Architecture

Rural areas often suffer from intermittent connectivity. BorneoHarvest is engineered with a strict **Offline-First** philosophy to prevent data loss and UI freezing:

1. **Firestore Local Cache:** Disaster reports are written to the local device cache first. If the user is offline, the UI updates instantly, and Firebase silently queues the payload, syncing the moment a 4G connection is restored.
2. **SharedPreferences Weather Backup:** Live API weather data and city locations are continuously saved locally. If the app boots offline, it pulls the most recent data to ensure the dashboard remains functional.
3. **No-Crash UI Logic:** Extensive `try-catch` blocks, active fallback triggers, and dedicated Volley Retry Policies protect the app from crashing when asynchronous cloud tasks time out.

---

## 🚀 Installation & Setup

To run this project locally, you will need to provide your own API keys and Firebase configuration.

### Prerequisites
* Android Studio (Latest Version)
* Minimum SDK: API 26 (Android 8.0)
* Target SDK: API 34

### Step-by-Step Setup
1. **Clone the repository:**
   ```bash
   git clone [https://github.com/yourusername/BorneoHarvest.git](https://github.com/yourusername/BorneoHarvest.git)


2. **Open the project** in Android Studio.
3. **Configure Gemini API:**
* Open the `local.properties` file in your root directory.
* Add your Gemini API key:
```properties
GEMINI_API_KEY="your_api_key_here"

```




4. **Configure Firebase:**
* Create a new project in the [Firebase Console](https://console.firebase.google.com/).
* Set up a **Cloud Firestore** database.
* Download the `google-services.json` file.
* Place the file inside the `app/` directory of this project. *(Note: This file is intentionally git-ignored for security).*


5. **Build and Run:**
* Sync the project with Gradle files and deploy to a physical Android device or Emulator.



---

## 🔮 Future Roadmap

* [ ] **IoT Integration:** Connect the app to cheap, field-deployed Arduino soil moisture sensors.
* [ ] **Marketplace Module:** Allow rural farmers to sell healthy crops directly to buyers, eliminating predatory middlemen.
* [ ] **Background Push Server:** Migrate the real-time sync listener to a Firebase Node.js Cloud Function (FCM) to wake up fully closed devices.

---

🛡️ **SeedShield ASEAN: Command Center & Real-Time Backend**
This repository also contains the Government & NGO Command Center, a high-impact web dashboard designed for real-time disaster monitoring. It acts as the "Single Source of Truth," receiving live data from the SeedShield mobile application used by farmers in the field.

🏗️**Technical Architecture**
The project leverages a cross-platform, real-time data pipeline. By using Firebase Firestore as the centralized backend, the system achieves sub-second synchronization between Android devices and the Web Dashboard.

🔌**The "Data Bridge" Integration**
The dashboard is hard-coded to synchronize with the DisasterReports collection. The integration relies on a specific "Data Contract" between the mobile app (Android/Java) and the Command Center (Web/JS).

**Data Schema (Firestore Keys)**
To ensure live synchronization, the following exact keys are used:
* **Key:* title **Description:* The disaster type + emoji (e.g., "🚨 Banjir Kilat 🌊") **Data Type:* String
* **Key:* description **Description:* Community-verified details or AI analysis notes **Data Type:* String
* **Key:* location **Description:* GPS-derived city or district name **Data Type:* String
* **Key:* time **Description:* Formatted date/time string of the occurrence **Data Type:* String
* **Key:* imageBase64 **Description:* The actual field photo encoded for instant web display **Data Type:* String (Base64)
* **Key:* timestamp **Description:* Exact millisecond time for chronological sorting **Data Type:* Number/Long

🚀**Key Features for Pitching**
* Live Handshake: Uses Firestore onSnapshot() listeners. When a farmer submits a report on the mobile app, it appears on the dashboard instantly without a page refresh.
* Multi-Platform Robustness: Demonstrates that the backend architecture is scalable and ready for both field users (Mobile) and decision-makers (Web).
* Visual Evidence Feed: The dashboard decodes imageBase64 strings to show real-time photos of crop diseases or disasters, providing immediate visual verification for NGOs.
* Emergency Logic: The dashboard automatically calculates "Total Reports" and identifies "Emergency Alerts" based on the incoming data stream.

🛠️**Setup Instructions**
* Configure Firebase: Ensure the firebaseConfig object in the dashboard matches the Project ID: borneoharvest-adbe1.
* Database Rules: Firestore security rules must allow public read/write for the duration of the hackathon:

**JavaScript*
allow read, write: if true;

**Deployment:** Open index.html in a browser to start the live monitoring feed.
<p align="center">
<i>Developed with ❤️ for the Borneo Hackathon 2026.</i>
</p>

```
