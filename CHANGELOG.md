# Changelog

Released versions, newest first. The release step in
`.github/workflows/ai-maintenance-agent.yml` prepends an entry here when it cuts a
tag, so keep this a flat `## [X.Y.Z]` list — it inserts above the first such
heading, and a different structure either breaks the insert or gets corrupted by
it.

## [1.0.2]

- Delete the downloaded APK once the installer has read it, so a cancelled update does
  not leave it in the cache.

## [1.0.1]

- Check for updates from Settings, and show a banner on the home screen when a newer
  release is published.
- Sign every release with one long-lived key, so an update can replace the installed app.

## [1.0.0]

- Search jac.red, stream a release through TorrServer, hand it to an installed
  player.
- Browse TMDB titles, filter releases by quality, HDR, codec and 60 fps, and keep
  a recently-watched row.
- The version the app carried before releases were cut from tags.
