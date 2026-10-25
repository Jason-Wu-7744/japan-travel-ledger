[English](ARCHITECTURE.md) | [中文](ARCHITECTURE.zh-CN.md)

# Architecture

A native Android Java app with no third-party runtime dependencies. The current implementation is concentrated in one Activity. Its UI labels and speech language are Chinese.

```text
Text or system speech recognition
              |
Local item and amount parsing
              |
User confirmation and save
              |
SharedPreferences + JSON
       |          |          |
Categories    JPY/CNY      CSV export
and day view  estimates
```

## Parsing and persistence

Local regular expressions extract an amount and item. No network or language-model API is used for parsing. Records contain an ID, item, category, JPY amount and creation timestamp. Custom categories, the exchange rate and its update time are stored in the same app-private preferences.

Numbers use explicit US decimal symbols so editable values and CSV numeric fields keep a dot decimal separator regardless of the device locale. Non-finite and non-positive amounts or rates are rejected; invalid cached rates fall back to the default estimate.

## Categories and grouping

New records are uncategorized. Users add, rename or delete categories and assign records manually. Renaming updates matching records; deleting a category returns its records to uncategorized. Category and calendar-day summaries are recomputed from local records.

## Exchange rates

A manual refresh uses `HttpURLConnection` to request `https://open.er-api.com/v6/latest/JPY`. No ledger content is included. The CNY rate and update time are cached locally; failed requests retain the saved rate or default estimate. Explicitly recognized CNY amounts are converted into JPY before saving. Totals are estimates using the currently selected rate, not historical conversion rates for each expense.

## Speech, export and display

Android `SpeechRecognizer` delegates speech-to-text to the selected system service; network handling depends on that service. The app does not save recordings itself. `ACTION_CREATE_DOCUMENT` exports a UTF-8 BOM CSV to the chosen document provider. Insets and display-cutout safe areas determine page padding.

## Backup boundary

`android:allowBackup="true"` remains in the manifest. System backup or device transfer can include the stored preferences, depending on device settings. A document-picker destination can also be cloud-backed. See [Privacy](../PRIVACY.md).

## Limits and verification

- JPY is the ledger currency; CNY is a conversion estimate.
- There is no app-managed synchronization or CSV import.
- The parser selects a numeric match and supports only some Chinese-number expressions; complex arithmetic, incidental numbers and compound amounts need user confirmation.
- System speech is unavailable on some devices.
- SharedPreferences suits a small personal ledger rather than large datasets.

The audit compiled `MainActivity.java` with JDK 17 against Android API 35 and checked targeted numeric regressions. This is not a full Gradle build, lint run or device test. Existing APKs were not rebuilt, signature-checked or matched to source.

Build-attempt note: the offline Gradle build and lint invocation stopped before task execution because Windows could not load `native-platform.dll`. Java/API compilation and the targeted JVM checks above completed separately. No APK build or lint success is claimed.
