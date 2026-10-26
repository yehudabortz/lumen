# Maintaining and publishing Lumen

## Release checks

1. Start from a clean clone. Use JDK 21, Android platform 34 and build-tools 35; run `./build.sh` and
   `sh tests/run-scenic-tests.sh`. Follow [Android checks](../tests/README.md) for cache and live-photo changes.
2. Run `gitleaks git . --redact --log-opts=--all` and
   `python3 tools/security/audit-repository.py --history`. Inspect all GitHub branches/tags and any PR refs,
   Actions logs, release attachments and wiki content before changing visibility. These tools do not inspect
   private information inside screenshots; manually review every new image.
3. Check D-pad focus, Home/Back return, sources/credits, city search, NASA's first load and offline fallback.
   Inspect actual Android screenshots. Keep personal-device captures and diagnostics outside the repository.
4. Update CHANGELOG, README, design documentation and notices. Separate implemented behavior from planned features.
5. For an official APK, build locally with the existing private signing key. Verify it with
   `apksigner verify --verbose --print-certs out/Lumen.apk`, compare the signer against the previous official
   APK, and publish its SHA-256. Never upload `lumen.keystore`, `local.properties` or raw signing credentials.
   CI creates an ephemeral build key and does not publish its APK as an official release.
6. Create an experimental/pre-release tag and attach only the reviewed signed APK and checksum. A first
   source release does not imply Google Play compliance, broad device compatibility or a support guarantee.

## Repository controls

Rules for `main` are managed in GitHub, under **Settings › Rules › Rulesets**, not in this repository; those
settings are the only source of truth. Review them there, including which checks are required and who may
bypass them. Keep the repository private until the owner explicitly authorizes publication.

Use read-only default Actions tokens, disallow workflow approval of pull requests, pin actions to full commit
SHAs, and retain only the merge methods the `main` rules allow. Enable dependency alerts and automated
security fixes where available. Hosted secret scanning/push protection and private vulnerability reporting
should be enabled when the repository plan/visibility supports them; do not silently buy a paid feature.

## Sensitive-data incident

If a credential is exposed, revoke/rotate it first; deleting a file does not invalidate the credential. Keep
scan reports redacted and outside the source tree. Audit every reachable ref and any relevant GitHub copies.
If history needs rewriting, preserve a private recovery copy, rewrite only affected refs, verify the sanitized
result and push with a lease against the reviewed remote tip. Coordinate with other clone owners. GitHub PR
refs/cached views may require GitHub Support; local rewriting cannot erase other people's clones.

Retired local Git objects and reflogs can retain data after a previous rewrite. Once a scrub is verified and
recovery is no longer needed, expire those reflogs and prune unreachable objects. Do not keep private recovery
bundles, personal screenshots or secret scan reports in the repository.
