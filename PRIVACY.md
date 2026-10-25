[English](PRIVACY.md) | [中文](PRIVACY.zh-CN.md)

# Privacy

Documentation reviewed: 2026-10-25. This describes the repository source; distributed APK behavior has not been independently verified against it.

## Ledger storage and Android backup

Items, amounts, categories, custom category lists, cached exchange rates and timestamps are saved in app-private SharedPreferences on the device. The app has no account, ledger server or app-managed cloud synchronization.

**Android system backup remains enabled.** The manifest sets `android:allowBackup="true"` and declares no backup exclusion rules. Depending on device settings and the operating system's backup or transfer service, app-private preferences may be backed up or transferred outside this device. Local-first storage therefore does not guarantee that data never leaves the phone. See [Android's Auto Backup documentation](https://developer.android.com/identity/data/autobackup).

Uninstalling the app or clearing its data removes the local copy. System backups may restore data later. Export CSV files if you need a backup you control; the app does not import CSV backups.

## Microphone and speech recognition

The app requests microphone permission when the user initiates voice input. It calls Android's selected system speech service, receives recognized text and does not implement background recording or save audio files itself. The speech provider may process audio online under its own privacy policy.

## CSV export

On request, the app writes a UTF-8 CSV to the destination selected through Android's document picker. The app does not upload it. A selected document provider can be a cloud-backed location; its storage behavior is controlled by that provider and the user's selection.

## Exchange-rate refresh

Only the refresh action initiates the app's exchange-rate request to `https://open.er-api.com/v6/latest/JPY`. The request contains no ledger items, amounts or categories. The rate provider can receive normal connection metadata, such as the public IP address. On failure, the app retains the saved rate or its default estimate. The system speech service can make its own network requests.

## Permissions and third-party services

- Microphone: user-initiated speech entry.
- Internet: user-initiated exchange-rate refresh; system speech behavior is separate.

The source contains no advertising, analytics, crash-reporting or tracking SDK. It requests no contacts, location, photo-library or broad storage permission. Android backup, system speech and the selected document provider have their own behavior and privacy settings.
