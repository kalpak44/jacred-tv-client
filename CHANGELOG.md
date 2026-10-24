# Changelog

Released versions, newest first. The release step in
`.github/workflows/ai-maintenance-agent.yml` prepends an entry here when it cuts a
tag, so keep this a flat `## [X.Y.Z]` list — it inserts above the first such
heading, and a different structure either breaks the insert or gets corrupted by
it.

## [1.2.1]

- No change to the app itself; released to confirm the build and signing pipeline
  still produces an installable update after its GitHub Actions were bumped.

## [1.2.0]

- Translate the interface into Russian, chosen by the television's own locale.
- Add an interface language setting, for when the app should not follow the television.
- Stop the focus ring being clipped off the recently-watched card when it is selected.

## [1.1.2]

- Move the update banner above the search box, so pressing up from the search box moves
  up the screen rather than down onto it.

## [1.1.1]

- Look for a new release once on launch, so the home-screen banner appears on the way in
  rather than up to six hours later.
- Replace the Settings button with a round gear, so the heaviest thing in the header is no
  longer the one control nobody is looking for.

## [1.1.0]

- Rework the interface against one spacing, type and focus scale, so focus means the same
  thing on every control and the filter chips no longer take half the releases screen.
- Stop Settings opening the keyboard by itself, and let the D-pad move between its fields
  instead of trapping the remote on the first one.
- Start with an empty search box rather than restoring the last query.
- Stop offering a TMDB key in the plot line when one is already configured.
- Rename the Gradle project and theme from HelloTv to JacRedTv.

## [1.0.3]

- Show the release's changelog entry in the update dialog, so an update says what it
  changes before it is installed.
- Show the running version on the home screen beside the TorrServer chip.

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
