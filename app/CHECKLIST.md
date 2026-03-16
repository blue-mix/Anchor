// CHECKLIST.md

# Anchor Development Checklist

## Version 1.0.0 Release Checklist

### Core Features
- [x] HTTP server with Ktor Netty
- [x] File streaming endpoint (`/files/`)
- [x] Stream endpoint with range support (`/stream/`)
- [x] Thumbnail endpoint (`/thumbnail/`)
- [x] Directory browsing API (`/api/browse/`)
- [x] Foreground service implementation
- [x] Multicast lock for SSDP
- [x] Wake lock for transfers

### UPnP/DLNA
- [x] SSDP announcer (NOTIFY alive/byebye)
- [x] SSDP M-SEARCH responder
- [x] Device description XML (`/dlna/device.xml`)
- [x] ContentDirectory service description
- [x] ConnectionManager service description
- [x] SOAP Browse action handler
- [x] DIDL-Lite response generation
- [x] Proper XML escaping

### Discovery (Client)
- [x] SSDP multicast listener
- [x] M-SEARCH broadcast
- [x] Device description parser
- [x] Discovered devices StateFlow
- [x] Stale device cleanup

### UI Screens
- [x] Onboarding with permissions
- [x] Dashboard with server controls
- [x] Discovery screen with filters
- [x] Remote browser (list/grid views)
- [x] Player screen with ExoPlayer
- [ ] Settings screen

### Player Features
- [x] Basic ExoPlayer integration
- [x] Play/pause controls
- [x] Seek bar
- [x] Fullscreen mode
- [x] Playback speed control
- [ ] Test with remote servers
- [ ] Picture-in-Picture
- [ ] Gesture controls (volume/brightness)

### Data Persistence
- [ ] Save shared directories (SharedPreferences/DataStore)
- [ ] Save server port setting
- [ ] Save theme preference
- [ ] Save auto-start preference
- [ ] Restore state on app restart

### Error Handling
- [ ] Network error handling in browser
- [ ] Server start failure handling
- [ ] Permission denial handling
- [ ] File not found handling
- [ ] User-friendly error messages

### Testing
- [ ] Test server with VLC (desktop)
- [ ] Test server with VLC (mobile)
- [ ] Test server with Smart TV
- [ ] Test server with Windows Explorer
- [ ] Test discovery of other devices
- [ ] Test player with remote server
- [ ] Test large file streaming (>2GB)
- [ ] Test seek functionality
- [ ] Test subtitle passthrough

### Documentation
- [x] README.md
- [x] CHECKLIST.md
- [ ] API documentation
- [ ] Architecture diagram
- [ ] Screenshots
- [ ] Demo video

### Polish
- [ ] App icon
- [ ] Splash screen
- [ ] Loading states
- [ ] Empty states
- [ ] Pull-to-refresh
- [ ] Animations

---

## Version 1.1.0 Planned Features

### Cast to TV
- [ ] DLNA renderer discovery
- [ ] AVTransport service implementation
- [ ] SetAVTransportURI action
- [ ] Play/Pause/Stop actions
- [ ] Seek action
- [ ] GetPositionInfo action
- [ ] Now Playing UI (remote control)

### Web UI
- [ ] HTML template for browser access
- [ ] Responsive design
- [ ] File browser in HTML
- [ ] Video player in HTML5
- [ ] Mobile-friendly layout

### Playlist Support
- [ ] Generate .m3u playlists
- [ ] Folder as playlist
- [ ] Custom playlist creation
- [ ] Playlist export

### Library Features
- [ ] Media indexing service
- [ ] SQLite database for media
- [ ] Search functionality
- [ ] Recently played
- [ ] Favorites

---

## Version 2.0.0 Refactor

### Architecture
- [ ] Clean Architecture layers
- [ ] Repository pattern
- [ ] Use cases
- [ ] Dependency injection (Hilt)
- [ ] Result wrapper for errors

### Code Quality
- [ ] Unit tests (JUnit)
- [ ] Integration tests
- [ ] UI tests (Compose)
- [ ] Code coverage >70%
- [ ] Static analysis (Detekt)
- [ ] Lint checks

### CI/CD
- [ ] GitHub Actions workflow
- [ ] Automated builds
- [ ] Automated tests
- [ ] Release automation
- [ ] Signed APK generation

### Distribution
- [ ] Play Store listing
- [ ] F-Droid submission
- [ ] GitHub releases
- [ ] Changelog automation

---

## Bug Tracker

| ID | Description | Severity | Status |
|----|-------------|----------|--------|
| B001 | Shared folders reset on restart | High | 🔴 Open |
| B002 | Player not tested with remote | Medium | 🟡 Testing |
| B003 | Some special characters in filenames | Low | 🟡 Investigating |

---

## Notes

### Testing Devices
- Phone: [Your device model]
- TV: [Your TV model]
- Desktop: Windows/Mac/Linux

### Test Media Files
- Video: MKV (HEVC), MP4 (H.264)
- Audio: MP3, FLAC
- Size: Test with >2GB files

### Network Setup
- Wi-Fi: [Your network details]
- Typical latency: [X] ms
- Bandwidth: [X] Mbps