# Contributing to Lumen

Lumen is experimental Android TV software. Small, focused pull requests with a clear description are welcome.
Contributions to Lumen's own code are accepted under the MIT license. Keep third-party artwork and data notices intact.

1. Fork the repository or create a working branch. Open a pull request against `main`.
2. Use JDK 21, Android platform 34 and build-tools 35. Run `./build.sh` without Gradle.
3. Run `sh tests/run-scenic-tests.sh`, `python3 tools/security/audit-repository.py --history` and
   `gitleaks git . --redact --log-opts=--all`. See [tests](tests/README.md) for Android cache checks.
4. For UI changes, check actual Android rendering and D-pad navigation. A clean Google TV emulator with public
   sample content is appropriate for shareable captures; see [the capture workflow](tools/screenshots/README.md).
   Describe the Android version and what you checked. Include limitations when hardware testing is unavailable.
5. Keep [the design spec](docs/design/DESIGN.md) and relevant documentation in sync with implemented behavior.

Do not add personal TV screenshots, private device addresses, signing keys, API credentials, app data or raw
logs. Keep diagnostic files outside the repository and redact reports before sharing. Review staged files
before every commit. Git commit author information is public if the repository becomes public; choose your
GitHub no-reply email if you do not want your personal email included.

CI builds and scans pull requests using read-only permissions. Changes reach `main` through pull requests,
under branch rules the repository owner manages in GitHub's settings. See
[maintainer instructions](docs/MAINTAINING.md).
