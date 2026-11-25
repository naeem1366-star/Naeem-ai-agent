# Naeem AI Desktop Bridge

Windows companion for the Naeem AI Android app.

## Current alpha capabilities
- Local HTTPS server hosted on the Windows PC (no cloud server).
- Six-digit pairing code (15-minute validity, five-attempt limit) and per-session random token.
- Certificate fingerprint pinning support for the Android client.
- UDP discovery on the same Wi-Fi/LAN.
- JPEG screen streaming to the paired phone.
- Optional mouse, click, keyboard, text-entry and allow-listed app-launch commands.
- Remote control is OFF by default and must be enabled on the PC.

## Pairing
1. Start the Windows app.
2. Connect the phone and PC to the same router/network.
3. In the Android app open «کنترل کامپیوتر» and tap «جست‌وجوی کامپیوتر».
4. Enter the six-digit code shown on Windows.
5. Confirm the displayed certificate fingerprint matches the Windows window.
6. Enable remote control on Windows before sending mouse/keyboard actions.

The bridge listens on TCP 8765 (HTTPS/WSS) and UDP 8766 (discovery). Keep Windows Firewall restricted to Private networks. Do not expose these ports to the public internet.

## Build
Use .NET 8 SDK on Windows:
```powershell
dotnet publish .\NaeemAIDesktopBridge.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true
```

The GitHub Actions workflow packages a Windows installer. This is an early LAN-only bridge; it does not yet run an AI model locally or modify project files in design applications.
