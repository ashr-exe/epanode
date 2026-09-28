# Feature coverage and limits

## Implemented

| Area | Behavior |
| --- | --- |
| Local library | MediaStore scan, explicit SAF folder access, songs/albums/artists/folders, incremental handling of unchanged MediaStore entries, manual rescan, missing device-row removal after a successful complete scan |
| Metadata | Embedded/indexed metadata first; deterministic filename cleanup second; explicit `needs review` fallback; embedded covers, optional MusicBrainz/Cover Art Archive lookup with user selection; local metadata overrides survive rescans |
| Search | Unicode/accent-aware local title/artist/album/lyrics search with bounded edit-distance matching and debouncing; search runs off the UI thread |
| Playback | Play/pause, seek, next/previous, shuffle, repeat one/all, engine-managed transitions, media-session/notification controls, audio focus and unplug handling |
| Queue | Play next, append, remove, move earlier, select queued item, persisted queue, position checkpoint every 30 seconds while playing and on explicit state changes |
| Collections | Likes, play counts, recently played, recent additions, most played, daily deterministic local mixes, local song radio |
| Playlists | Create, rename, delete, add/remove songs, reorder, shuffle/play, M3U export using local content URIs |
| Best parts | Multiple named clips per song; millisecond time entry; range slider; mark current position; preview; save, edit, remove; loop one clip or every saved clip across a playlist/library |
| Lyrics | Plain text and timestamped LRC, repeated timestamps and offsets, highlighted current line, tap to seek, text editing, explicit file import, matching sidecars when scanning a folder |
| Sound | 0.5–2× playback speed, device equalizer presets, sleep timer |
| Discovery | YouTube Music search with YouTube search fallback; queries can contain titles, artists or remembered lyrics; results depend on the provider’s search index |
| Import | Direct HTTPS audio URLs; YouTube video/music links and paginated public playlists; public Spotify track/album/playlist embed metadata with conservative audio matching; Android share target automatically queues supported links |
| Download jobs | Durable WorkManager jobs, Wi-Fi default, low-storage constraint, serialized transfer, visible progress/errors, cancellation, transient network retries, duplicate-source detection, cleanup of unpublished partials |
| Backup | Versioned JSON export/restore of local metadata, playlist membership, parts, likes, and history; transactional restore; bounded input; unrecognized track identities do not introduce executable or network paths |
| Privacy | No account or analytics SDK; no cloud backup; local history and recommendation ranking; explicit online lookup/search/import operations |

## Important limits

- **Not all features of Spotify and YouTube Music are implemented.** Crossfade, ReplayGain/loudness normalization, Android Auto browsing, widgets, casting/Connect, multi-device handoff, collaborative sessions, podcast/audiobook catalogs, video playback, voice search, and equalizer hardware verification are outside this build. Cloud catalogs, social feeds, subscriptions, and cloud personalization conflict with a wholly local library; several of the other omissions are simply unfinished work, not requirements that were impossible.
- Recommendations use tags, likes, playback counts, and a deterministic daily ordering. There is no audio embedding model, genre classifier, or Spotify/YouTube recommendation feed.
- **There is no acoustic fingerprint recognition.** A file with an opaque filename and no trustworthy tags cannot be reliably identified from its name. Epanode displays it deterministically and allows correction; it does not pretend a guessed match is certain. MusicBrainz lookup requires recognizable search text.
- Library identity is based on Android content URIs. Moving files across storage providers, deleting/reimporting them, or restoring to a different phone can change identity. This version is not a cross-device music migration tool. Separately indexed copies can remain separate tracks.
- SAF folders need explicit Android permission. App-private storage owned by other apps and protected offline Spotify/YouTube caches are inaccessible. New SAF content requires another folder scan; there is no always-running filesystem watcher.
- Spotify public embed pages are not a stable playlist export API. Private/unavailable items fail clearly. Exposed track-count mismatches are rejected, but a provider may not expose a reliable total. Large or restricted Spotify playlists therefore need further provider work. A confident text/duration match reduces errors but cannot prove recording identity.
- YouTube/NewPipe extraction depends on upstream website behavior and may break or be blocked by region, rate limits, or authentication. No guarantee covers all links. Live-stream, DRM, login-required, and members-only downloads are not supported.
- Song-link URLs that include a YouTube `list` parameter are treated as playlist imports. Downloads are limited to 512 MB per file and 5,000 items per fetched YouTube playlist. Cancellation does not remove completed music from storage.
- Sharing an audio content URI from another app can grant only temporary permission. Adding its parent folder explicitly is the durable option.
- Imported remote covers are cached, not embedded into the downloaded audio file. Clearing the cache can require fetching them again. The app falls back to generated artwork when a cover cannot be loaded.
- LRC is supported; there is no bundled commercial lyrics catalog. “Search by lyric” online means passing that query to the music provider, not guaranteed lyric fingerprint lookup.
- Backup/M3U exports reference the existing local files; audio is not copied into backups. Crossfade and sample-perfect loop seams have not been measured on real hardware.
- The APK is an evaluation build. Actual device playback, Bluetooth, notification behavior, interruptions, battery, RAM, and long-running download jobs still require the device validation listed in TESTING.md.
