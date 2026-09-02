# v2rayNG Auto

An account-first Android VPN client with a clean dashboard, automatic server health checks, and first-class support for subscription metadata.

[![Release](https://img.shields.io/github/v/release/Girikhi/v2rayNG?display_name=tag&sort=semver)](https://github.com/Girikhi/v2rayNG/releases/latest)
[![Build](https://github.com/Girikhi/v2rayNG/actions/workflows/build-custom-debug.yml/badge.svg?branch=codex%2Fv2.2.5-auto)](https://github.com/Girikhi/v2rayNG/actions/workflows/build-custom-debug.yml)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/nougat)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

> **v2rayNG Auto V1.0** is an independent customized fork of [2dust/v2rayNG](https://github.com/2dust/v2rayNG), based on the fast v2rayNG 2.2.5 codebase. It is not an official 2dust release.

## Download

Download the signed **universal APK** from the [latest GitHub release](https://github.com/Girikhi/v2rayNG/releases/latest).

The universal package supports:

- `arm64-v8a`
- `armeabi-v7a`
- `x86`
- `x86_64`

Android 7.0 (API 24) or newer is required. When Android asks for permission, allow installation from the browser or file manager used to open the APK.

## Highlights

- Simple dashboard focused on accounts, connection state, and usable servers.
- Persian interface by default with a one-tap English/Persian switch.
- Light and dark themes with correct LTR and RTL layout behavior.
- Add an account directly from the clipboard or a QR code—no manual naming step.
- Account names are taken from subscription metadata or the subscription link.
- Account drawer with select, share, and delete controls.
- Workspace, Telegram channel, account status, and expiry progress on the main screen.
- Anonymous server labels such as **Server 1**, **Server 2**, and so on.
- Real-delay ping results using the familiar v2rayNG latency colors.
- Failed configurations are hidden from the active server list without being deleted.
- The first working server is selected automatically and is ready to connect.

## Automatic recovery

Every time the app opens, it checks the current account's configurations and prepares the first working server.

If all servers fail, the app automatically refreshes the subscription once and tests again when all of these conditions are true:

1. The account status is `active`.
2. The account has not expired.
3. Android reports an active internet-capable network.

This lets compatible Super Admin panels deliver fresh configurations without requiring the user to refresh manually.

## Quick start

1. Open **v2rayNG Auto**.
2. Tap **Add**.
3. Choose **Clipboard** or **QR code**.
4. Wait for the automatic server check.
5. Tap the large start button to connect.

Use **Ping** to test the account again or **Refresh** to download a fresh subscription immediately.

### راهنمای کوتاه فارسی

1. برنامهٔ **v2rayNG Auto** را باز کنید.
2. روی **افزودن** بزنید.
3. **کلیپ‌بورد** یا **کد کیوآر** را انتخاب کنید.
4. منتظر بمانید تا سرورها به‌صورت خودکار سنجیده شوند.
5. برای اتصال، دکمهٔ بزرگ شروع را لمس کنید.

## Subscription metadata

v2rayNG Auto understands the metadata returned by `my-bpb-custom-panel-next` Super Admin subscriptions.

| Response header | Purpose |
| --- | --- |
| `X-Panel-Expires-At` | Account expiry as an epoch value, ISO timestamp, or date |
| `subscription-userinfo: expire=...` | Standard expiry fallback |
| `X-Panel-Workspace` | URL-encoded UTF-8 workspace name |
| `X-Panel-User` | URL-encoded UTF-8 user name |
| `X-Panel-Status` | `active`, `scheduled`, `disabled`, or `expired` |
| `X-Panel-Starts-On` | Account start date or timestamp |
| `X-Panel-Telegram-Url` | Optional HTTP, HTTPS, or Telegram link |
| `X-Panel-Metadata-Version` | Metadata format version; currently `1` |
| `profile-update-interval` | Recommended refresh interval in hours |

The app enforces a minimum automatic refresh interval of 15 minutes. Existing metadata remains available if a later response omits an optional header.

## Building

The canonical APK is built by [GitHub Actions](https://github.com/Girikhi/v2rayNG/actions/workflows/build-custom-debug.yml). The workflow:

1. Builds the native tunnel libraries.
2. Downloads the matching Xray core library.
3. Builds the optimized F-Droid universal APK.
4. Signs the APK using repository secrets.
5. Checks the app name, version, non-debuggable state, and universal package.
6. Uploads only the universal APK artifact.

Forks that run this workflow must configure these Actions secrets:

- `V2RAYNG_AUTO_KEYSTORE_BASE64`
- `V2RAYNG_AUTO_KEYSTORE_PASSWORD`

The Android Studio project is located in [`V2rayNG/`](V2rayNG/).

## Credits and license

v2rayNG Auto is built on the work of:

- [v2rayNG](https://github.com/2dust/v2rayNG)
- [Xray-core](https://github.com/XTLS/Xray-core)
- [v2fly/v2ray-core](https://github.com/v2fly/v2ray-core)

This repository is distributed under the [GNU General Public License v3.0](LICENSE). Source modifications remain available here in accordance with that license.
