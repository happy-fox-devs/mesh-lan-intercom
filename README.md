# 🦊 Mesh Lan Intercom (MLI)

**Decentralized, Off-Grid Voice Communication System for Android.**

![Project Status](https://img.shields.io/badge/Status-Stable-green) ![License](https://img.shields.io/badge/License-MIT-blue) ![Platform](https://img.shields.io/badge/Platform-Android-3DDC84)

> **"Communicate without internet, without servers, and with total privacy."**

## 📖 Overview

**Mesh Lan Intercom (MLI)** is a high-performance Android application that creates a **local mesh network** for voice communication. It uses a combination of **Bluetooth**, **Wi-Fi Direct**, and **Ultra-Low Latency Audio (Oboe + Opus)** to allow groups of people to talk in real-time without relying on cellular data, internet, or routers.

Ideal for:
- 🏍️ Motorcycle Convoys
- 🏕️ Camping & Hiking

## ✨ Key Features

*   **📡 Decentralized Mesh Network:** Uses Google Nearby Connections (P2P_CLUSTER) to auto-discover and route audio between devices.
*   **⚡ Ultra-Low Latency:** Built with C++ (Oboe) and the Opus Codec (24kbps VBR) for instant voice transmission.
*   **🔒 Secret Channels:** Network isolation using cryptographic hashing of a "Secret Word". Only peers with the same key can communicate.
*   **🎧 Bluetooth Headset Support:** Automatic SCO/HFP routing for hands-free operation.
*   **🦊 Custom Identity:** Persistent nicknames and peer list visualization.

## 🛠️ Architecture

*   **Language:** Kotlin (UI/Logic) + C++ (Audio Engine).
*   **Audio Stack:** Google Oboe (AAudio/OpenSL ES) -> Opus Encoder -> JNI Bridge.
*   **Transport:** Google Nearby Connections API (Star/Cluster Topology).
*   **Patterns:** MVVM-ish (Compose), RAII (C++), Observer Pattern (Mesh Manager).

## 🚀 Getting Started

### Prerequisites
*   Android Studio Iguana or newer.
*   Android device with Android 8.0 (Oreo) or higher.
*   NDK and CMake installed.

### Installation
1.  Clone the repository:
    ```bash
    git clone https://github.com/your-username/mesh-lan-intercom.git
    ```
2.  Open in Android Studio.
3.  Build and Run (`Shift + F10`).
4.  **Permissions:** Grant all requested permissions (Location, Bluetooth, Audio) on the first launch.

## 📱 How to Use

1.  **Set Identity:** Enter your **Secret Channel Word** (e.g., "TEAM_ALPHA") and your **Nickname**.
2.  **Save:** Click the Save icons (💾).
3.  **Start Engine:** Press the large **Red Power Button**. It will turn **Green**.
4.  **Connect:** Wait for peers to appear in the list.
5.  **Talk:** Just speak! The audio is always-on (Full Duplex).
