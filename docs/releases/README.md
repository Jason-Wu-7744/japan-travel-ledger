[English](README.md) | [中文](README.zh-CN.md)

# Release and source notes

Checked on 2026-10-25 against GitHub release and tag metadata.

- `main` declares source version **3.0.0** and version code **7**.
- `/releases/latest` returns published **v2.1.1**.
- The release named `V3.0.0` has `draft=true`, no publication date and an empty `tag_name`. Its untagged attachment URL is not a public v3.0.0 release link.
- A `v3.0.0` Git tag exists separately. The draft's empty tag has not been silently replaced or published.
- Tags **v1.0.0, v1.0.1, v1.1.0, v2.0.0 and v2.1.0** all resolve to commit `45b77c4760e57ec5618918271b20fd977afcffb7`. Its `app/build.gradle` declares **2.1.0 / versionCode 5**. Checking out those earlier tags therefore does not reproduce distinct historical source versions.

| Version | Feature notes |
| --- | --- |
| [v1.0.0](v1.0.0.md) | Initial release |
| [v1.0.1](v1.0.1.md) | HyperOS compatibility |
| [v1.1.0](v1.1.0.md) | Speech input and display cutouts |
| [v2.0.0](v2.0.0.md) | Category summaries and CSV export |
| [v2.1.0](v2.1.0.md) | Manual classification |
| [v2.1.1](v2.1.1.md) | Daily management |
| [v3.0.0](v3.0.0.md) | Custom categories and exchange rates |

## APK verification limits

Release attachments are preserved with their existing filenames. No APK was rebuilt, installed, signature-checked or shown to match a particular source commit during this audit. Historical source/asset correspondence remains unverified; no guessed retagging was performed. A Java compile against Android API 35 is not a Gradle package build or device test. Review the actual release version before expecting features described on `main`.
