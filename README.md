# St Sava Printer App

An Android companion app for the St Sava Church Register and the paired X5h-953E mini thermal printer.

## Current stage

This first build is a **Bluetooth connection test** for the Rhino T8 (Android 10). It lists already-paired Bluetooth devices and connects to the selected printer using Bluetooth Classic Serial Port Profile (SPP).

**It does not print receipts yet.** The printer's exact Tiny protocol handshake and print-job sequence still need to be verified against the physical printer before enabling printing. The existing Church Register is not modified.

## Build

GitHub Actions builds a debug APK on pushes to `main` and on manual runs:

1. Open the repository's **Actions** tab.
2. Select **Build Android APK**.
3. Open the successful run and download the `st-sava-printer-debug-apk` artifact.
4. Install the APK on the Rhino T8.

## Device

- Minimum Android: 10 (API 29)
- Printer name expected: `X5h-953E`
- Transport: Bluetooth Classic SPP
- SPP service UUID: `00001101-0000-1000-8000-00805F9B34FB`

## Safety

The app only opens a Bluetooth connection in this stage. It does not send undocumented commands, feed paper, or change printer settings.
