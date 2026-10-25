[English](CHANGELOG.md) | [中文](CHANGELOG.zh-CN.md)

# Changelog

Version entries below describe the source history and historical release notes. As checked on 2026-10-25, the latest published release is v2.1.1; the v3.0.0 release is a draft. See the [release/source boundary](docs/releases/README.md).

## Unreleased audit changes

- Add English default documentation and Chinese companion pages.
- Correct Android backup and document-provider privacy descriptions.
- Make editable numeric values and CSV fields independent of device locale.
- Reject non-finite amounts and rates, and recover from an invalid cached rate.
- Clarify release status, early-tag source mismatches and APK verification limits.

## 3.0.0 — 2026-08-27

- Add custom category creation, renaming and deletion, with matching-record updates.
- Cache the category list locally and use it for manual classification.
- Add JPY-to-CNY refresh and manual rate entry.
- Show converted totals and add rate/CNY columns to CSV.
- Convert recognized CNY-marked input into JPY before saving.

## 2.1.1 — 2026-08-21

- Add category/day grouping and daily counts, totals and details.
- Retain category summaries, manual classification and CSV export.
- Recompute the selected view from local records.

## 2.1.0 — 2026-08-21

- Replace keyword classification with manual category selection.
- Save new entries as uncategorized and allow later reclassification.
- Reset earlier automatic classifications once during migration.
- Export the final manual classification.

## 2.0.0 — 2026-08-21

- Add category summaries, grouped records, counts and subtotals.
- Export UTF-8 BOM CSV using Android's document picker without broad storage permission.

## 1.1.0 — 2026-08-21

- Add in-app system speech recognition, microphone permission and recognition-state/error feedback.
- Check speech-service availability and apply system-bar/display-cutout insets.

## 1.0.1 — 2026-08-21

- Adjust startup system-bar handling for HyperOS compatibility.
- Add a safe fallback screen and system speech-service visibility declarations.

## 1.0.0 — 2026-08-21

- Initial Record/Manage interface with text and system speech input.
- Parse expenses locally, confirm before saving, persist records and show totals.
