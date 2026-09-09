<div align="center">

# 🌈 AmbiGoveeTV

### Philips Ambilight → Govee, directly on Android TV / Google TV

**Turn compatible Govee LAN lights into an extension of your Philips Ambilight TV — without a camera, HDMI sync box, Home Assistant, Raspberry Pi, or always-on PC.**

[![Android TV](https://img.shields.io/badge/Android%20TV-supported-3DDC84?logo=android&logoColor=white)](#compatibility)
[![Google TV](https://img.shields.io/badge/Google%20TV-supported-4285F4?logo=google&logoColor=white)](#compatibility)
[![Latest release](https://img.shields.io/github/v/release/supermathgeek/AmbiGoveeTV?label=latest)](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest)
[![License](https://img.shields.io/github/license/supermathgeek/AmbiGoveeTV)](LICENSE)

**English** · [Français](README_FR.md)

<br>

### Download

[**🪟 Windows installer**](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGovee-Installer-Windows.exe)
&nbsp;&nbsp;
[**🍎 macOS — Apple Silicon**](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGovee-Installer-macOS-Apple-Silicon.zip)
&nbsp;&nbsp;
[**🍎 macOS — Intel**](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGovee-Installer-macOS-Intel.zip)
&nbsp;&nbsp;
[**🐧 Linux**](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGovee-Installer-Linux-x86_64.tar.gz)

[**📦 Download APK directly**](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGoveeTV.apk)

</div>

---

## Can Govee lights sync with a Philips Ambilight TV?

**Yes — on compatible devices.** AmbiGoveeTV reads the colors already calculated by a compatible Philips Ambilight Android TV / Google TV through the local Philips JointSpace interface, then sends those colors to compatible Govee lights using Govee LAN control.

That means the Philips TV remains the source of the Ambilight effect. AmbiGoveeTV simply extends it into the room.

```text
Philips Ambilight TV
        │
        │ local Ambilight colors
        ▼
   AmbiGoveeTV
        │
        │ local network
        ▼
 Compatible Govee lights
```

### Why this is different

- **No camera pointed at the screen**
- **No HDMI sync box**
- **No PC that must stay powered on**
- **No Home Assistant required**
- **No Raspberry Pi required**
- **No AmbiGovee account**
- Main Philips → Govee synchronization stays on your **local network**
- Designed specifically for **Android TV / Google TV remote control**

---

# 🚀 Easy installation

AmbiGoveeTV includes a guided desktop installer with a real visual interface.

The installer automatically downloads Android Platform Tools and the latest official AmbiGoveeTV APK, connects to your TV, handles Android TV user profiles, installs the app, and launches it.

## Windows

1. Download [**AmbiGovee-Installer-Windows.exe**](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGovee-Installer-Windows.exe).
2. Open it.
3. Follow the on-screen instructions.
4. Enter your Philips TV's local IP address when requested.

> The installer is not code-signed yet, so Windows SmartScreen may show a warning. The source code is available in [`installer/ambigovee_installer.py`](installer/ambigovee_installer.py).

## macOS

Choose the build matching your Mac:

- **Apple Silicon (M1/M2/M3/M4/...)**: [download](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGovee-Installer-macOS-Apple-Silicon.zip)
- **Intel Mac**: [download](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGovee-Installer-macOS-Intel.zip)

Extract the ZIP and open **AmbiGovee Installer**.

> The macOS app is not notarized yet. If macOS blocks the first launch, right-click the app and choose **Open**.

## Linux

Download [**AmbiGovee-Installer-Linux-x86_64.tar.gz**](https://github.com/supermathgeek/AmbiGoveeTV/releases/latest/download/AmbiGovee-Installer-Linux-x86_64.tar.gz), extract it, then run:

```bash
./AmbiGovee-Installer
```

The installer itself is packaged as a standalone binary: users do **not** need Python installed.

---

# 📺 Before installing

On your Philips TV:

1. Open **Settings → About**.
2. Press **Build / Android TV build number** 7 times to enable Developer Options.
3. Open **Developer Options**.
4. Enable **USB debugging / ADB debugging**.
5. Make sure the computer and TV are on the same local network.
6. Find the TV's local IP address in the network settings.

Once AmbiGoveeTV is installed, ADB debugging can be disabled. It is only needed for installation/update from a computer.

---

# 📱 Philips pairing: QR code + phone

The first Philips pairing uses your phone as a temporary second screen.

This avoids closing the Philips PIN window on the TV.

1. AmbiGoveeTV displays a QR code on the TV.
2. Scan it with your phone while both devices are on the same Wi-Fi/LAN.
3. Tap **Start pairing**.
4. Philips displays a PIN on the TV.
5. **Keep the Philips PIN window open.**
6. Enter the PIN on your phone.
7. AmbiGovee confirms: **TV connected!**
8. Continue on the TV and add your Govee lights.

The pairing page is served directly by the TV over your local network. Philips credentials are stored on the TV and are not returned to the phone.

---

# 💡 Govee setup

For every compatible Govee light:

1. Open **Govee Home**.
2. Enable **LAN Control** for the device.
3. In AmbiGoveeTV, choose **Add / Search for a light**.
4. Select its room position:
   - Whole room / ceiling
   - Left
   - Top
   - Right
   - Bottom

You can add multiple lights and pause an individual light without deleting it.

---

# 🎬 Sync modes

| Mode | Best for | Behavior |
|---|---|---|
| **Direct** | Games / responsive content | Fast reaction |
| **Cinema** | Movies / series | Smoother transitions |
| **Soft** | Ambient viewing | More relaxed changes |

---

# 🛡️ Light-state safety

AmbiGoveeTV is designed to respect the real state of your lights.

- If a Govee light is off, AmbiGoveeTV does **not** turn it on just to synchronize.
- If you turn a light off during a movie, AmbiGoveeTV does **not** turn it back on.
- Before synchronization, AmbiGoveeTV can remember the normal color/brightness state.
- When synchronization stops, it restores that state only when doing so does not force an intentionally-off light back on.

---

# ✅ Compatibility

## Philips TVs

AmbiGoveeTV requires a compatible:

- Philips **Ambilight** TV
- Android TV or Google TV environment
- Philips **JointSpace** local API with Ambilight color access

Compatibility can vary by TV generation and firmware.

## Govee lights

The Govee device must expose compatible **Govee LAN Control** capabilities.

AmbiGoveeTV is capability-oriented rather than limited to one hard-coded Govee model.

If your device works — or does not — please open an [Issue](https://github.com/supermathgeek/AmbiGoveeTV/issues) with the TV model, firmware, Govee model and AmbiGoveeTV version. Do not publish private pairing keys or network secrets.

---

# ❓ FAQ

### Can I use Govee with Philips Ambilight without a camera?

Yes, when the Philips TV and Govee device are compatible. AmbiGoveeTV uses the Ambilight color data produced by the TV itself, so it does not need to watch the screen with a camera.

### Do I need a PC running while I watch TV?

No. A Windows, macOS or Linux computer is used only to install the Android TV app. After installation, AmbiGoveeTV runs directly on the TV.

### Do I need Home Assistant?

No. Home Assistant is not required.

### Do I need a Raspberry Pi?

No.

### Does it work with Netflix, HDMI devices and built-in TV apps?

AmbiGoveeTV follows the Ambilight color information exposed by the TV. Whether a particular source produces usable Ambilight data depends on the Philips TV, source and firmware.

### Does AmbiGoveeTV use the cloud for synchronization?

The main Philips-to-Govee synchronization is designed to work locally on the LAN. GitHub is used for software releases and update checks.

### Will AmbiGoveeTV turn on a light I intentionally switched off?

No. The sync logic is designed not to force an off Govee light back on.

### Is this an official Philips or Govee app?

No. AmbiGoveeTV is an independent open-source community project and is not affiliated with, sponsored by, or endorsed by Philips or Govee.

---

# 🔄 Updates

AmbiGoveeTV checks GitHub Releases for newer versions.

A normal sideloaded Android TV app cannot guarantee a completely silent installation on every TV. Android may request installation permission or confirmation.

For reliable in-place updates:

- keep the package name `fr.ambigovee.tv`;
- sign every release with the same Android signing certificate;
- publish a higher `versionCode`;
- keep the stable release asset name `AmbiGoveeTV.apk`.

The desktop installer always downloads the latest stable APK, so it can also be used to repair or update an installation.

---

# 🔐 Privacy

AmbiGoveeTV is built around local-network communication.

Do not post the following in public Issues:

- Philips pairing IDs or keys
- passwords
- private network credentials
- private tokens

See [PRIVACY.md](PRIVACY.md).

---

# 🐛 Bugs, device reports and contributions

Found a compatible TV or Govee device? Found a bug?

👉 [Open an Issue](https://github.com/supermathgeek/AmbiGoveeTV/issues/new)

Useful information:

- Philips TV model
- Android TV / Google TV version
- Philips firmware
- Govee model
- AmbiGoveeTV version
- expected behavior
- actual behavior
- sanitized logs if available

Pull Requests are welcome. The project is maintained by **supermathgeek**.

---

---

# 📄 License

Copyright © 2026 **supermathgeek**

See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md).

<div align="center">

### 🌈 AmbiGoveeTV

**Your Ambilight. Your whole room.**

</div>
