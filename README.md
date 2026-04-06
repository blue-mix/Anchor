# Anchor

A lightweight, cross-platform local media server for Android. Turn your phone into a streaming hub - share videos, music, and photos to Smart TVs, VLC, and any device on your local network without internet or cloud storage.

![Android](https://img.shields.io/badge/Android-26%2B-green?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple?logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

---

## Features

### Server Mode
- **HTTP Media Server** - Host your media files over local network
- **Lossless Streaming** - Byte-for-byte transfer, no transcoding
- **Byte-Range Support** - Seek/scrub through videos seamlessly
- **UPnP/DLNA Discovery** - Automatically appear on Smart TVs and media players
- **QR Code Sharing** - Quick connection for other devices
- **Background Service** - Keeps running with persistent notification

### Client Mode
- **Device Discovery** - Find other Anchor servers and DLNA devices
- **Remote Browsing** - Browse files on discovered servers
- **In-App Playback** - Play media with built-in ExoPlayer
- **Cast to TV** - Push media to Smart TVs *(planned)*

### Media Support
| Type | Formats |
|------|---------|
| Video | MP4, MKV, AVI, MOV, WebM, FLV, 3GP |
| Audio | MP3, FLAC, AAC, OGG, WAV, M4A, OPUS |
| Image | JPG, PNG, GIF, WebP, HEIC |

---

## Screenshots

<!-- Add screenshots here -->
| Dashboard | Discovery | Browser | Player |
|-----------|-----------|---------|--------|
| ![Dashboard](screenshots/dashboard.png) | ![Discovery](screenshots/discovery.png) | ![Browser](screenshots/browser.png) | ![Player](screenshots/player.png) |

---

## Architecture
```
┌─────────────────────────────────────────────────────────────┐
│                         ANCHOR                              │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │   Server    │  │  Discovery  │  │       Media         │  │
│  ├─────────────┤  ├─────────────┤  ├─────────────────────┤  │
│  │ Ktor HTTP   │  │ SSDP Listen │  │ ExoPlayer           │  │
│  │ DLNA/UPnP   │  │ SSDP Announce│ │ Thumbnails          │  │
│  │ Foreground  │  │ Device Parse │  │ Media Session      │  │
│  │ Service     │  │             │  │                     │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────┐│
│  │                    UI (Jetpack Compose)                 ││
│  ├─────────────────────────────────────────────────────────┤│
│  │ Onboarding │ Dashboard │ Discovery │ Browser │ Player   ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

### Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVVM + StateFlow |
| Dependency Injection | Koin |
| HTTP Server | Ktor (Netty) |
| Media Playback | AndroidX Media3 (ExoPlayer) |
| Discovery | Native UDP/Multicast (SSDP) |
| Image Loading | Coil 3.x |
| QR Generation | ZXing |

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- Android SDK 26+ (Android 8.0)
- Kotlin 2.0+

### Build & Run
```bash
# Clone the repository
git clone https://github.com/yourusername/anchor.git

# Open in Android Studio
cd anchor

# Build debug APK
./gradlew assembleDebug

# Install on device
./gradlew installDebug
```

### Permissions Required

| Permission | Purpose |
|------------|---------|
| `INTERNET` | HTTP server and network communication |
| `ACCESS_WIFI_STATE` | Get local IP address |
| `CHANGE_WIFI_MULTICAST_STATE` | UPnP/SSDP discovery |
| `READ_MEDIA_*` | Access media files (Android 13+) |
| `READ_EXTERNAL_STORAGE` | Access media files (Android 12-) |
| `FOREGROUND_SERVICE` | Keep server running |
| `POST_NOTIFICATIONS` | Show server status |

---

## Usage

### Starting the Server

1. Open Anchor and grant permissions
2. Tap **"Add Folder"** to select directories to share
3. Tap **"Start Server"**
4. Share the displayed URL or QR code

### Connecting from Other Devices

**VLC (Desktop/Mobile):**
1. Open VLC → View → Playlist → Local Network → Universal Plug'n'Play
2. Select "Anchor: [Device Name]"
3. Browse and play files

**Smart TV:**
1. Open your TV's media player app
2. Look for network/DLNA sources
3. Select "Anchor: [Device Name]"

**Web Browser:**
```
http://[PHONE_IP]:8080/
```

### API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | Server info |
| `/api/info` | GET | Server info (JSON) |
| `/api/directories` | GET | List shared folders |
| `/api/browse/{alias}/{path}` | GET | Browse directory |
| `/files/{alias}/{path}` | GET | Download/stream file |
| `/stream/{alias}/{path}` | GET | Stream with range support |
| `/thumbnail/{alias}/{path}` | GET | Get media thumbnail |
| `/dlna/device.xml` | GET | UPnP device description |

---

## Roadmap

### Version 1.0 (Current)

- [x] HTTP media server with Ktor
- [x] Lossless file streaming
- [x] Byte-range requests (seeking)
- [x] UPnP/DLNA server announcements
- [x] SSDP device discovery
- [x] ContentDirectory service (SOAP)
- [x] Foreground service with notification
- [x] Multicast lock management
- [x] Dashboard with server controls
- [x] QR code generation
- [x] Remote file browser
- [x] Discovery screen
- [x] In-app video player (ExoPlayer)
- [x] Onboarding flow with permissions
- [ ] Persist shared folders across restarts
- [ ] Settings screen

### Version 1.1 (Planned)

- [ ] Cast to TV (DLNA renderer control)
- [ ] Playlist generation (.m3u)
- [ ] Web UI for browser access
- [ ] Multiple server profiles
- [ ] Folder auto-scan
- [ ] Media library indexing

### Version 1.2 (Planned)

- [ ] Desktop companion app (Kotlin Multiplatform)
- [ ] Chromecast support
- [ ] AirPlay support (iOS interop)
- [ ] Subtitle management
- [ ] Watch history sync

### Version 2.0 (Future)

- [ ] Unit & integration tests
- [ ] CI/CD pipeline
- [ ] Play Store release

---

## Known Issues

| Issue | Status | Workaround |
|-------|--------|------------|
| Shared folders reset on restart | Open | Re-add folders after restart |
| Some TVs don't show thumbnails | Investigating | Thumbnails work in VLC |
| Player not tested with remote servers | Testing | Use VLC as alternative |

---

## Contributing

Contributions are welcome! Please read our contributing guidelines before submitting PRs.

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful commit messages
- Add comments for complex logic
- Update README for new features

---

## Project Structure
```
app/src/main/java/com/example/anchor/
├── AnchorApp.kt                  # Application class
├── MainActivity.kt               # Entry point
├── core/                         # Core utilities and extensions
│   ├── extension/
│   ├── result/
│   └── util/
├── data/                         # Data layer (DTOs, Mappers, Repositories)
│   ├── dto/
│   ├── mapper/
│   ├── model/
│   ├── repository/
│   └── source/
├── di/                           # Dependency Injection setup (Koin)
├── domain/                       # Domain layer (Models, Repositories, Use Cases)
│   ├── model/
│   ├── repository/
│   └── usecase/
├── server/                       # Ktor HTTP Server and DLNA
│   ├── AnchorHttpServer.kt
│   ├── DeviceDescriptionParser.kt
│   ├── UpnpDiscoveryManager.kt
│   ├── dlna/
│   ├── handler/
│   ├── routing/
│   └── service/
└── ui/                           # Jetpack Compose UI (Screens, ViewModels)
    ├── browser/
    ├── components/
    ├── dashboard/
    ├── onboarding/
    ├── player/
    └── theme/
```

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
```
MIT License

Copyright (c) 2024 Anchor

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## Acknowledgments

- [Ktor](https://ktor.io/) - Async HTTP server framework
- [ExoPlayer](https://exoplayer.dev/) - Media playback library
- [Coil](https://coil-kt.github.io/coil/) - Image loading
- [ZXing](https://github.com/zxing/zxing) - QR code generation
- [Material Design 3](https://m3.material.io/) - Design system

---

## Contact

- **Issues**: [GitHub Issues](https://github.com/yourusername/anchor/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yourusername/anchor/discussions)

---

<p align="center">
  Made using Kotlin & Jetpack Compose
</p>