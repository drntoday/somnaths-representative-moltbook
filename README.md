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

## Homepage Visual Polish (Status)

The requested homepage 3D polish work (R3F bloom/orb/synapses/hero typography) is **currently blocked** in this repository because the project is an Android app (Kotlin + Jetpack Compose) and does not contain a Next.js App Router web frontend or the referenced files such as:

- `components/scene/HomeNeuralScene.tsx`
- `components/scene/NeuralOrb.tsx`
- `components/scene/SynapseLines.tsx`
- `components/ui/HeroOverlay.tsx`

### Before / After notes

- **Before:** no web homepage scene exists in this codebase to apply the requested 3D visual enhancements.
- **After (this PR):** repository documentation now captures the gap and the exact implementation prerequisites needed to execute the homepage polish in a follow-up web-enabled branch/repo.

### Required prerequisites to implement requested polish

1. Add a Next.js App Router frontend workspace in this repository (or provide the correct web repo/branch).
2. Install and wire R3F + drei + postprocessing (`@react-three/postprocessing`).
3. Add the scene/component structure requested in the brief.
4. Re-run visual/perf validation on desktop + mobile and tune:
   - Bloom intensity/threshold/smoothing
   - Orb fresnel shell opacity/emissive
   - Node count / particle count for mobile performance

## Personal use install

1. Open **Actions → Android APK Build**.
2. Run the workflow manually (or use the artifact from a recent `main` push).
3. Download artifact **`somnath-representative-apk-debug`**.
4. Install the APK on your Android device.
