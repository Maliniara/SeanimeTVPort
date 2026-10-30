# Project Vision

## Pitch
Seanime TVapp is an Android TV application that lets an anime fan manage their AniList library and watch episodes on the big screen, with an interface designed for a TV remote.

## Problem Statement
Anime streaming and library tooling is built for desktop browsers and phones. On a TV, using a browser or a mobile app means fighting the interface from the couch. The project follows the Seanime experience (library, series pages, provider plugins) but runs natively on Android TV, with no server required.

## Target Users
Solo user (personal project) — an anime enthusiast who tracks their list on AniList and wants a polished, remote-friendly 10-foot experience.

## Key Features
- **AniList library and discovery** — watching/planning lists, browse, search, airing schedule, multiple accounts
- **Series detail** — hero banner, episode grid, recommendations, characters, trailer, AniList entry editing
- **Plugin-based streaming** — JS/TS provider plugins run on-device, with automatic provider and server fallback and a plugin store
- **Video playback** — Media3 player with quality, subtitle and audio selection, intro/outro skip and resume
- **TV-optimized navigation** — full D-pad support with visible focus states
- **TV home integration** — Continue Watching channel on the Android TV home screen

## Success Criteria
- The whole library is browsable and playable with the remote only
- Episodes play without any mobile or desktop interaction
- Navigation feels native to Android TV (focus states, D-pad traversal, no touch dependency)
- Watch progress stays in sync with AniList
- Runs smoothly on low-end TV hardware (Chromecast with Google TV HD)

## Differentiators
- Purpose-built native Android TV app (Kotlin, Compose, `androidx.tv`), not a web-to-TV port
- Standalone: AniList plus on-device plugins, with no self-hosted server to run
- Tuned for low-RAM boxes (bounded image caches, reduced animation on low-memory devices)

---
*Created: 2026-06-25 · Updated: 2026-10-29 — rewritten to match the implemented app (previously described a Seanime-server client built on React Native/Expo).*
