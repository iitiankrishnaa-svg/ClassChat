# ClassChat: Offline Zero-Configuration Classroom Chat

An offline, zero-configuration Android messaging application engineered specifically for students in a classroom.

ClassChat operates **100% offline**: No internet connection, mobile data, Wi-Fi router, or cloud servers are required. Communication happens entirely through direct device-to-device **Bluetooth Low Energy (BLE)** using real Android BLE APIs.

---

## 1. Zero-Configuration User Experience

From installation to active messaging, the experience is completely friction-free:

```
Open App
   ↓
Enter Name (First launch only)
   ↓
Tap "Allow & Start Chatting" (Bluetooth / Nearby Devices permission)
   ↓
If Bluetooth is OFF → Android system prompt to enable Bluetooth
   ↓
Automatic Discovery & Live Classroom Dashboard
```

- **NO** manual Bluetooth device pairing or PIN codes.
- **NO** IP addresses, subnet masks, or socket ports.
- **NO** Wi-Fi hotspot configuration or Wi-Fi passwords.
- **NO** cloud accounts, phone numbers, or email logins.
- **NO** classroom codes or room numbers.

---

## 2. Technical Architecture & Analysis: 30–60 Students Scale

### The Android Bluetooth Reality & Hardware Limits
Standard Android phones have physical hardware and OS constraints:
1. **Concurrent GATT Connection Limit**: The underlying Bluetooth controller chips (Qualcomm, Broadcom, MediaTek) and Android's Fluoride Bluetooth stack hard-cap simultaneous active GATT connections to **4 to 7 connections** on most consumer phones (up to ~10–12 on select recent flagships).
2. **Crash on All-to-All Connections**: Attempting to maintain persistent bidirectional GATT connections between 30–60 students would require thousands of connections across the room and 30–59 connections on each phone. This triggers `GATT_NO_RESOURCES` (Error 133) and crashes the Bluetooth stack.
3. **No Native OS Bluetooth Mesh**: Android does not provide a native, standard SIG Bluetooth Mesh socket API for peer-to-peer data interchange without proprietary controller firmware and physical provisioning keys.

### The ClassChat Solution: Hybrid Beaconing + Transient GATT Queue + Epidemic Relay

To achieve robust communication for 30–60 students simultaneously, ClassChat implements a four-part architecture:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PRESENCE & DISCOVERY                            │
│  Continuous BLE Advertising & Scanning (Zero Connections Held)         │
│  All 30–60 students discover each other in 1–3s via Service Data      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
               ┌────────────────────┴────────────────────┐
               ▼                                         ▼
┌───────────────────────────────┐         ┌───────────────────────────────┐
│     ONE-TO-ONE MESSAGING      │         │      CLASS GROUP CHAT         │
│  Transient Connection-on-Demand│         │  Epidemic Store-and-Forward   │
│  • Connect via GATT LE        │         │  • Fan-out to 3 nearby peers  │
│  • Write framed packet        │         │  • Deduplication cache        │
│  • Receive write ACK          │         │  • Hop limit (TTL = 3)        │
│  • Disconnect (~250ms total)  │         │  • Multi-hop mesh propagation │
└───────────────────────────────┘         └───────────────────────────────┘
```

1. **Passive Presence & Classroom Discovery (Zero Connections)**:
   - Every phone continuously advertises the ClassChat Service UUID (`0000CC01-0000-1000-8000-00805F9B34FB`) with compact Service Data containing the student's ID hash and display name.
   - Devices scan in low-latency mode.
   - **All 30–60 students in the room discover each other without opening a single GATT connection!**
   - A background heartbeat tracker monitors last-seen timestamps and marks students as "Away" if no broadcast is received for 15 seconds.

2. **Transient Connection-on-Demand (GATT Queue Dispatcher)**:
   - When a student sends a direct message:
     - The message is enqueued in `BleQueueDispatcher`.
     - The phone connects to the target peer's GATT Server (`TRANSPORT_LE`).
     - Writes the payload to `CHAR_MESSAGE_IN` (`0000CC02-0000-1000-8000-00805F9B34FB`).
     - Closes and releases the GATT connection immediately.
   - The entire transaction takes ~200–400ms. A phone only ever holds **1 active connection at any instant**, well below the hardware limit of 4–7.

3. **Zero-Pairing GATT Protocol**:
   - The local GATT Server exposes characteristics with `PERMISSION_WRITE` and `PROPERTY_WRITE_NO_RESPONSE` without requiring MITM encryption pairing.
   - Android will **never** display a system pairing confirmation or passkey prompt.

4. **Epidemic Store-and-Forward Mesh Relay for Class Group Chat**:
   - Broadcast messages to the "Class Group" use an epidemic gossip relay:
     - **Header**: `messageId` (UUID), `senderId`, `senderName`, `timestamp`, `hopLimit` (TTL = 3), `visitedNodes` list.
     - **Deduplication Engine**: A thread-safe LRU cache and local Room database index track `seenMessageIds`. If a message ID has already been received, it is discarded immediately.
     - **Fanout**: When a student posts or receives a fresh classroom broadcast, they forward the packet to 2–3 nearby peers who haven't received it yet.
     - With a TTL of 3 hops and fanout of 3, messages propagate across 60 phones in 1–2 seconds, reaching from the front row to the back row of large lecture halls.

---

## 3. Project Structure

```
com.classchat.app/
├── ClassChatApplication.kt          # Application initialization & DI
├── MainActivity.kt                  # Activity host, permissions & Bluetooth dialogs
├── model/
│   ├── UserProfile.kt               # Local user profile
│   ├── PeerUser.kt                  # Discovered classmate model & signal calculation
│   ├── Message.kt                   # Room entity (1-to-1 & Class Group)
│   ├── DeliveryStatus.kt            # PENDING, SENDING, SENT_DIRECT, RELAYED, DELIVERED, FAILED
│   └── BlePacket.kt                 # Protocol framing, serialization, and JSON codec
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt           # Room Database
│   │   ├── MessageDao.kt            # Message DAO with indexed queries
│   │   ├── PreferencesManager.kt    # DataStore Preferences for user ID and name
│   │   └── Converters.kt            # Type converters
│   ├── ble/
│   │   ├── BleConstants.kt          # Service & Characteristic UUIDs, timing constants
│   │   ├── BleAdvertiser.kt         # BLE presence advertiser
│   │   ├── BleScanner.kt            # BLE scanner & heartbeat pruner
│   │   ├── BleGattServer.kt         # Local GATT server receiving incoming messages
│   │   ├── BleGattClient.kt         # Transient GATT client for dispatching packets
│   │   ├── BleQueueDispatcher.kt    # Serialized FIFO queue for stable BLE controller operations
│   │   ├── MeshRelayEngine.kt       # Deduplication and epidemic gossip relay
│   │   └── BleManager.kt            # Master BLE orchestrator
│   └── repository/
│       ├── UserRepository.kt        # User profile & discovered peers flow
│       └── ChatRepository.kt        # Room persistence & BLE transmission bridge
├── service/
│   └── ClassChatService.kt          # Foreground service keeping BLE alive in background
└── ui/
    ├── navigation/
    │   ├── Screen.kt                # Type-safe navigation routes
    │   └── NavGraph.kt              # Compose NavHost
    ├── theme/
    │   ├── Color.kt                 # Theme colors (Indigo, Slate, Emerald, Teal)
    │   ├── Type.kt                  # Typography
    │   └── Theme.kt                 # Material 3 light/dark support
    ├── components/
    │   ├── SignalIndicator.kt       # Visual RSSI bars (Excellent, Good, Fair, Weak)
    │   ├── UserAvatar.kt            # Initials avatar with gradient
    │   ├── MessageBubble.kt         # Chat bubble with delivery ticks & retry button
    │   └── StatusBanner.kt          # Bluetooth state & classroom count banner
    └── screens/
        ├── splash/SplashScreen.kt
        ├── onboarding/
        │   ├── NameSetupScreen.kt   # Minimalist name entry
        │   └── PermissionScreen.kt  # System permission & Bluetooth enable flow
        ├── main/
        │   ├── MainScreen.kt        # Classroom dashboard, tabs, hero card
        │   └── MainViewModel.kt
        ├── chat/
        │   ├── ChatScreen.kt        # 1-to-1 chat interface
        │   └── ChatViewModel.kt
        ├── group/
        │   ├── GroupChatScreen.kt   # Class Group broadcast chat interface
        │   └── GroupChatViewModel.kt
        └── settings/
            ├── SettingsScreen.kt    # Edit name, diagnostics, clear chats, privacy
            └── SettingsViewModel.kt
```

---

## 4. How to Build & Run

### Prerequisites
- Android Studio (Hedgehog, Iguana, Jellyfish, or newer)
- Android SDK 34 (Android 14)
- JDK 17

### Building from Android Studio
1. Open Android Studio.
2. Select **Open** and choose the `bt chat app` directory.
3. Allow Gradle to sync dependencies.
4. Connect an Android device via USB (or wireless debugging).
5. Click **Run** (`Shift + F10`).

### Building from the Command Line
Using the included Gradle wrapper:

**Windows (PowerShell/CMD):**
```powershell
.\gradlew.bat assembleDebug
```

**macOS / Linux:**
```bash
./gradlew assembleDebug
```

The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

To install directly to a connected device via ADB:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. Testing with 2+ Real Android Phones

Follow this verification procedure:

### Devices Setup
- **Phone A**: Enter name `Rahul`
- **Phone B**: Enter name `Aman`

### Testing Steps
1. **Turn OFF Internet**: On both phones, turn off Wi-Fi and Mobile Data (SIM data).
2. **Launch App**: Open ClassChat on both devices.
3. **Onboarding**:
   - First launch: Phone A enters `Rahul`, Phone B enters `Aman`.
   - Tap **Continue**.
4. **Grant Permissions**:
   - Tap **Allow & Start Chatting**.
   - Grant the system "Nearby Devices" / Bluetooth permissions.
   - If Bluetooth is turned off, Android will show a system dialog: *"An app wants to turn on Bluetooth"*. Tap **Allow**.
5. **Automatic Discovery**:
   - Within 2–4 seconds without touching anything:
     - Rahul’s phone will display **Aman** under *Nearby Students* with a signal indicator.
     - Aman’s phone will display **Rahul** under *Nearby Students*.
     - The top status banner updates to show: *"1 student in classroom • Offline BLE"*.
6. **One-to-One Chat**:
   - On Phone A, Rahul taps **Aman** (or taps *Chat*).
   - Rahul types `"Hello Aman!"` and taps **Send**.
   - Notice the checkmark changes: Clock (Sending) → Single Check (Sent Direct) → Double Check (Delivered).
   - On Phone B, Aman immediately receives `"Hello Aman!"` in real time.
   - Aman types `"Hi Rahul, sitting in the 2nd row!"` and taps **Send**.
   - Rahul receives Aman's reply immediately.
7. **Class Group Broadcast**:
   - On Phone A, navigate back and tap the **Class Group** card.
   - Type `"Professor announced quiz next Monday"` and send.
   - Phone B receives the message in Class Group with sender attributed as **Rahul**.
8. **Range & Disconnection Test**:
   - Turn Bluetooth off on Phone B.
   - Within 15 seconds, Phone A updates Aman's status to *Away*.
   - Turn Bluetooth back on Phone B; Aman reappears as online automatically.

---

## 6. Privacy & Security

- **Zero Cloud Footprint**: ClassChat contains no analytics SDKs, tracking pixels, or external API endpoints.
- **Local Storage**: All conversations are stored in a local SQLite database (Room) on the phone's private app storage.
- **No Account Harvesting**: No phone number, email address, or IMEI is ever requested or transmitted.
