# Development Roadmap

Status below reflects what exists in the code as of 2026-10-29. Items marked done were confirmed by the presence of the implementing code, not by device testing.

## Phase 1: MVP
**Goal**: A usable TV app that can browse and play content

- [x] **Project bootstrap** — Kotlin / Compose / `androidx.tv` project for Android TV `[Effort: S]`
- [x] **Data sources** — AniList GraphQL client with OAuth and multi-account; provider plugins through QuickJS `[Effort: M]` *(replaces the earlier "Seanime API client" plan)*
- [x] **Home screen** — Continue watching and list rows `[Effort: M]`
- [x] **Library browser** — Library, AniList browse `[Effort: M]`
- [x] **Series detail screen** — Episodes, metadata, play button `[Effort: M]` *(completed 2026-06-30; later rebuilt on Material 3 Expressive with an episode grid and range selector)*
- [x] **Video playback** — Media3 ExoPlayer (HLS and progressive) `[Effort: L]`
- [x] **D-pad navigation** — Focus management across screens `[Effort: M]`

## Phase 2: Core Features
**Goal**: Full-featured TV experience with sync and polish

- [x] **Watch progress sync** — Progress written back to AniList `[Effort: M]`
- [x] **Search** — AniList search with search history `[Effort: M]`
- [x] **Settings screen** — Account, plugins, subtitle and playback preferences `[Effort: S]`
- [ ] **Loading states & error handling** — Loading indicator and user-facing messages exist; skeleton screens and retry coverage not audited `[Effort: S]`
- [x] **Playback controls** — Seek, skip intro/outro, quality / subtitle / audio selection `[Effort: M]`
- [x] **Plugin store** — Browse and install provider plugins `[Effort: M]`
- [x] **Provider auto-fallback** — "Auto" provider mode plus per-episode server fallback `[Effort: M]`

## Phase 3: Polish & Quality of Life
**Goal**: Refined experience and nice-to-haves

- [x] **Anime detail metadata** — Genres, synopsis, rankings, studio `[Effort: S]`
- [x] **Continue watching row** — Home row plus Android TV home-screen channel `[Effort: S]`
- [x] **Airing schedule** — Weekly airing schedule screen `[Effort: M]`
- [ ] **Filters & sorting** — Filter library by status, genre, format; not verified in code `[Effort: M]`
- [x] **Trailer playback** — YouTube embed in a WebView overlay `[Effort: S]` *(completed 2026-06-30)*

## Open items
- [x] Set application id and root package to `com.malina.seanimetvport`
- [ ] Replace debug release signing before any publication
- [ ] Rethink `ANILIST_CLIENT_SECRET` handling if the app is ever distributed (the secret is extractable from the APK)

## Future Enhancements
- [ ] **Fire TV support** — same APK, needs compatibility check
- [ ] **Download management**
- [ ] **Playback speed** — deliberately skipped so far

---
**Effort Scale**: `S`: 2-3 days | `M`: 1 week | `L`: 2+ weeks

*Created: 2026-06-25 · Updated: 2026-10-29*
