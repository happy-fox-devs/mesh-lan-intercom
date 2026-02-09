# 🦊 Mesh LAN Intercom

**Decentralized, Off-Grid Voice Communication System for Android**

![Version](https://img.shields.io/badge/Version-1.0.4-blue) ![License](https://img.shields.io/badge/License-MIT-green) ![Platform](https://img.shields.io/badge/Platform-Android%208%2B-3DDC84)

> **"Communicate without internet, without servers, with total privacy."**

## 📖 Overview

**Mesh LAN Intercom** is a high-performance Android application that creates a **local mesh network** for real-time voice communication. Using **Bluetooth**, **Wi-Fi Direct**, and **Ultra-Low Latency Audio (Oboe + Opus)**, it enables groups to communicate without cellular data, internet, or infrastructure.

Perfect for:
- 🏍️ **Motorcycle Convoys** - Hands-free communication while riding
- 🏕️ **Camping & Hiking** - Stay connected in remote areas
- 🚨 **Emergency Scenarios** - Communicate when networks are down
- 🎮 **Gaming Groups** - Local multiplayer coordination

## ✨ Key Features

### Core Functionality
- **📡 Decentralized Mesh Network:** Auto-discovery and multi-hop routing via Google Nearby Connections (P2P_CLUSTER)
- **⚡ Ultra-Low Latency:** C++ audio engine with Oboe + Opus codec (24kbps VBR) for instant transmission
- **🔒 Secret Channels:** Cryptographic isolation - only devices with same secret word can connect
- **🎧 Bluetooth Headset Support:** Automatic SCO/HFP routing for hands-free operation
- **🌙 Background Operation:** Foreground service keeps audio running with screen off
- **🦊 Custom Identity:** Persistent nicknames with real-time peer list visualization
- **🌍 Bilingual UI:** Full support for English and Spanish

### Advanced Features
- **� Auto-Updater:** Over-the-air updates via GitHub Releases (no Play Store required)
- **🎨 Modern Dark UI:** Cyber-aesthetic with Material 3 and Jetpack Compose
- **🔇 Mute/Deafen Controls:** Independent microphone and speaker muting
- **📱 Zero Configuration:** Just set a secret word, nickname, and start

## 🛠️ Tech Stack

**Languages & Frameworks:**
- Kotlin (UI/Business Logic)
- C++ (Audio Engine)
- Jetpack Compose (Modern UI)

**Audio Stack:**
- Google Oboe (AAudio/OpenSL ES fallback)
- Opus Codec (Real-time audio compression)
- JNI Bridge (Kotlin ↔ C++ communication)

**Networking:**
- Google Nearby Connections API
- Star/Cluster Topology with flood routing
- Packet deduplication & TTL management

## 📥 Installation

### Option 1: Download APK (Recommended)
1. Download the latest release: [mesh-lan-intercom.apk](https://github.com/happy-fox-devs/mesh-lan-intercom/releases/latest)
2. Enable "Install from Unknown Sources" on your Android device
3. Install the APK
4. Grant all requested permissions on first launch

### Option 2: Build from Source
**Prerequisites:**
- Android Studio Iguana or newer
- Android SDK 34
- NDK 25.1.8937393 or newer
- CMake 3.22.1+

**Steps:**
1. Clone the repository:
   ```bash
   git clone https://github.com/happy-fox-devs/mesh-lan-intercom.git
   cd mesh-lan-intercom
   ```
2. Open in Android Studio
3. Sync Gradle files
4. Build and run (`Shift + F10`)

## 📱 How to Use

### First Time Setup
1. **Open Settings** (☰ menu → Settings)
2. **Set Language** (optional): Choose English or Español
3. **Return to Home** and configure:
   - **Secret Word**: Your private channel identifier (e.g., "CONVOY_ALPHA")
   - **Nickname**: How you'll appear to others (e.g., "Rider_01")
4. **Save** your configuration (💾 icons)

### Starting Communication
1. Press the **Power Button** (🔴 → 🟢)
2. Wait for peers with the same secret word to appear in the list
3. **Speak freely** - audio is full-duplex (always-on)

### Controls
- **Mute (🎤/🎤❌)**: Mute your microphone
- **Deafen (🎧/🎧❌)**: Mute microphone + disable incoming audio
- **Settings**: Access language, version info, and updates

### Auto-Update
1. Go to **Settings**
2. Tap **🔄 Check for Updates**
3. Follow prompts to download and install new versions

## 🔐 Privacy & Security

- **No Internet Required**: All communication is local P2P
- **No Servers**: Zero backend infrastructure
- **Channel Isolation**: Cryptographic secret word hashing prevents eavesdropping
- **Encrypted Transport**: Nearby Connections uses TLS by default
- **No Data Collection**: Zero telemetry or analytics

## 🤝 Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

**Copyright © 2026 Adrian Mauricio Arandia Urrea**

## 🐛 Known Issues

- File locks on Windows during Gradle clean (restart daemon: `./gradlew --stop`)
- First connection may take 5-10 seconds for peer discovery
- Bluetooth range limited to ~10-30m depending on device

## 🗺️ Roadmap

- [ ] Push-to-talk mode
- [ ] Audio quality settings (bitrate control)
- [ ] Connection strength indicators
- [ ] Offline message queue
- [ ] Group chat text overlay

## 📞 Support

For issues, questions, or feature requests:
- **GitHub Issues**: [Create an issue](https://github.com/happy-fox-devs/mesh-lan-intercom/issues)
- **Email**: happyfox.dev@gmail.com

---

**Built with ❤️ by the Happy Fox Devs team**
