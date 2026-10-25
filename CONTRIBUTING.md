[English](CONTRIBUTING.md) | [中文](CONTRIBUTING.zh-CN.md)

# Contributing

Bug reports, compatibility feedback, feature suggestions and code contributions are welcome.

## Reporting a problem

Include the device model, Android or HyperOS version, app version, reproduction steps, actual behavior and expected behavior. Remove private expenses, credentials and personal details from screenshots or logs.

## Code changes

1. Fork the repository and create a focused branch.
2. Keep the implementation small and avoid unnecessary runtime dependencies.
3. Do not introduce advertising, tracking or undocumented network calls.
4. With JDK 17 and Android SDK 35 available, run `./gradlew assembleDebug lintDebug` (Windows: `gradlew.bat assembleDebug lintDebug`). Report the exact checks actually completed.
5. Explain the motivation, behavior change and validation in the pull request.
6. Update English documentation and its `.zh-CN.md` companion together. The current app UI and speech configuration are Chinese; documentation language selection does not change the app's UI.

## Design principles

- Core bookkeeping works without an account or network connection.
- Request only the permissions needed by the feature.
- Let users control subjective category choices.
- Keep common travel-entry actions quick.
- Document parsing limits, system speech behavior, Android backup and external services accurately.

Source compilation alone is not an Android package build, lint run, installation test or device compatibility test. Preserve existing release assets when making documentation changes.
