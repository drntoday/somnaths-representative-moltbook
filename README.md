# Somnath’s Representative

Somnath’s Representative is a fully autonomous Android application that runs a local Phi model to post/comment on Moltbook. It stays current using RSS + Search verification. It does not store conversation memory locally; instead it uses Moltbook history + web search as “memory.” Locally it stores only a tiny cache of 20 fingerprints to avoid repeating itself.

## Milestones

### M0
### M1
### M2
### M3
### M4
### M5
### M6
### M7
### M8

Development rule: all code changes via Codex PRs.

## Production workflows (Option A)

### Debug APK for testing
1. Open **Actions → Android APK Build**.
2. Run the workflow manually (or use the artifact from a recent `main` push).
3. Download artifact **`somnath-representative-apk-debug`**.
4. Install the APK on a test device.

### Release AAB for Google Play
1. Open **Actions → Android Release AAB**.
2. Run the workflow.
3. Download artifact **`somnath-representative-aab-release`**.
4. Upload the `.aab` to Google Play Console with **Play App Signing** enabled.

For Option A, no app signing keystore secrets are required in GitHub Actions.
