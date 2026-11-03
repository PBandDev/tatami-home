# How Tatami works

Notes for people who want to change Tatami or check what it does. To install and use it, read the
[README](../README.md).

The repo holds two apps that share one copy of Tatami:

| Folder | What | Installed as |
|---|---|---|
| `plan/` | Tatami itself: the plan, its looks and effects, the full-screen view, the effects list, the screensaver | (a library) |
| `app/` | The Tatami add-on: `plan/` plus a module card for cleanyourtv's Home. Built from the repo root | `io.github.pbanddev.tatami` |
| `home/app/` | Tatami Home: cleanyourtv's launcher with `plan/` behind the apps. Built with `./gradlew -p home` | `io.github.pbanddev.tatamihome` |

`tools/export-addon.sh <cleanyourtv checkout>` writes the add-on folder that cleanyourtv keeps in `addons/tatami/`:
`git archive` without `docs/`, `home/`, `tools/` and this README (`.gitattributes`), plus `docs/addon-README.md`
as its README.

## What it asks for

No `INTERNET` permission in either app: nothing here uses the network. The Tatami add-on asks for no
permission at all (its APK is about 75 KB). Tatami Home's APK is about 640 KB, and it asks for these:

| In the manifest | Why |
|---|---|
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | The Wi-Fi pill: connected or not, and the network's name. |
| `ACCESS_FINE_LOCATION` | Android 10 and newer only show the Wi-Fi name to apps with location access. Never asked for on screen: `home/install.sh` grants it. |
| `WRITE_SECURE_SETTINGS` | Only for the launcher's optional remote-key helpers. Nothing grants it unless you set those up (cleanyourtv's `launcher/build-and-install.sh`). |
| Two accessibility services (`HomeKeyService`, `InputKeyService`) and a notification listener (`GuardRevival`) | The same optional remote-key helpers. All off until you switch them on in TV Settings; Tatami does not. |
| `yandex.tv.permission.ACCESS_TV_SOURCE` | Inputs on Yandex TV OS; ignored on other TVs. |

All of these come from cleanyourtv's launcher, unchanged.

## The moving plan

Every look value (the layout's knobs, the paints, the walls, the tilt, the doors, the fixtures and the lens) is
in `Look.java`, one instance per look: `Look.NAGAYA` and `Look.YOJOHAN`. Nagaya's pace is in its `Look` too;
Yojōhan's motion lives in its own classes, so its pace never changes.

Time is the wall clock, so every view of the plan shows the same frame at the same moment. That is why the
hand-over from Home to the screensaver does not jump.

### Nagaya

- One tile of about 62 rooms, 2.5 by 1.6 screen heights, repeats across the plan; each row of tiles is shifted
  half a tile. The camera's centre wraps by whole tiles, so the numbers stay small and exact however long it runs.
- Rooms sit in rows about a sixth of the screen height tall. A room is 1 to 3 boxes: a plain box, an L or a room
  with 2 steps in its edges, never more; wider rooms get more steps. Every wall lies on a grid of steps, 1/108 of
  the screen height.
- About every 7 seconds, at uneven times, some walls glide to new places and then everything rests. Each moving
  wall starts up to 1.8 s after the others and takes its own 2.4 to 3.2 s, eased at both ends. How much moves
  changes from time to time, and about 1 time in 7 nothing moves. Every 10 minutes every wall is back home.
- No room gets thinner than 70 % of its size at rest. Every rule the walls keep compares 2 walls, so a move is
  taken only if each rule also holds with one of its walls moved and the other not yet: then it holds at every
  moment, whichever wall moves first.
- Paints come from a deck of 23 warm and olive paints, shuffled once. Over them lies a colour wash fixed to the
  screen, bluer to the left and darker at the bottom, worked out on the CPU at the corners of small pieces of each
  room. The walls stay flat black.
- About 1 pair of touching rooms in 4 shares a door, at most one, so most rooms have one. Every door opens
  clockwise, and only where the room it swings into stays deep enough. All doors follow one opening on a clock of
  their own, about every 13 seconds at uneven times, each door up to 2 s behind the shared one, so they open in a
  slow ripple.
- The camera holds its tilt for 40 s, turns between 16 and 24 degrees over 26 s, and zooms out from 1 to 0.78 and
  back as it turns, all eased. It drifts down the screen and a little to the left, with a slow wander.

### Yojōhan

- The plan is cut once per seed into about 40 rooms: straight cuts, plus "windmills", 4 rooms wrapped around a
  centre room as tatami mats are laid. A windmill at the top of the tree means no wall crosses the whole screen.
- Every cut and windmill edge is a ratio that slides on its own slow sine. Rooms on both sides of a wall share
  that one number, so the area always adds up. No room gets thinner than 5 % of the screen height or longer
  than 5 to 1.
- Paints come from a deck of 22 dark olive and khaki paints, dealt once. Rooms that ever touch never share a
  paint.
- A door is a pale bar in the wall with a swing that shuts and opens fully, in loose waves. Each keeps its place
  on its wall; other walls' joins slide past it.
- A teal toilet and tub, and stairs, sit in about 1 room in 5, at a fixed size, in rooms that are big enough at
  every moment. They never shrink or cross a wall.
- The camera is tilted 11 degrees (walls fall to the right), drifts and slowly pulls in and back. The plan is
  sized so its edges never show, not even in the strip past the screen that the blur reads.

### Both

- Both, the default, shows Nagaya and Yojōhan in turns of 5, 10, 30 or 60 minutes, Nagaya first. Each swap dips
  to dark for 1.2 s each way, and the look changes in the dark.
- Yojōhan is built at once. Nagaya is built once per app process on a background thread, the first time a view
  asks for it (about 0.2 s on the test TV), and Home, the screensaver and the effects list share it. A view that
  starts while it builds waits in the dark, at most 2 s, and fades Nagaya in.

## Drawing

- OpenGL ES 2, all on the GPU. The scene is drawn with 4x MSAA.
- Tilt-shift draws the bare scene into a texture a little bigger than the screen (so walls just off screen
  blur in too), averages it down to 1/4 (a 4x4 box, so thin walls do not flicker), blurs it once with a
  separable Gaussian and blends it over the scene everywhere but a sharp oval in the middle.
- In Yojōhan, the scene in the window takes the vignette, a colour ramp (bluer left, warmer right) and grain.
  The vignette, the ramp and the blur's oval come from a small map of the screen made once per size; the grain
  from a 256 x 256 noise tile that jumps to a new place 24 times a second, like film. Nagaya's colours come
  washed from the CPU, so its scene takes only the grain, and its blend only the blur's oval.
- Each swap's dip to dark is one brightness value, applied last in the 2 passes that reach the window.
- Six passes, about 6 to 8 ms of GPU a frame at 1080p on the test TV, for either look. The blur target is padded
  to whole 16-pixel GPU tiles: unpadded, it cost 3 ms more.
- Only the scene's triangles go from the CPU to the GPU each frame.
- On Home the plan is a `GLSurfaceView` under the launcher's views, which draw over it with a dark layer at
  their bottom. The idle fade fades those views as one.
- The plan draws one frame per display refresh, asked for by the `Choreographer`. Left to draw as fast as the
  surface takes frames, it could settle a frame ahead after Home sat still, and the launcher's frames then
  waited behind it on the GPU: 98 % janky over the dock on the test TV, while the plan itself still showed
  60 fps.

## Home must not crash

On Android 10, one crash of a Home that is not a system app makes Android forget it as the default, and the
next Home press shows the chooser. So a GL failure never ends the app:

- The renderer catches its own failures (a shader or a render target the driver refuses, memory running out)
  and clears to the floor colour.
- A failure in Android's EGL setup ends only the drawing thread. The window is let go of the frame it waited
  for, and Home's next start leaves the plan out until the app is updated.

- Nagaya is made through one guarded factory. Anything it throws, while it builds or while it draws, is logged
  once, and Yojōhan is drawn instead for as long as the app runs.

All three were forced on the test TV: Home stayed the default and its UI kept working.

## Tests

Everything but the renderer and the Android glue is plain Java with unit tests.

- Yojōhan: every float of its triangles and camera matrix, over 40 times, 3 screen sizes and every switch, is held
  to one hash, and its shader text to its first version; the rooms fill the plan at any time and never overlap,
  windmills show up and no wall spans the plan, no room gets too thin or too long, touching rooms never share a
  paint, doors never pop in or out, and fixtures stay inside their walls at their size.
- Nagaya: the rooms fill the plan at any moment, mid-glide too and across the tile's edges; every wall rule holds
  at every moment, whatever order the walls move in; no room gets below 70 % of its size at rest; nothing moves
  between glides; a cold start equals a long run; 50 seeds at 2000 random times never throw or overflow; doors sit
  only on long enough walls, open clockwise, never swing across a wall and follow one opening in a ripple; the
  camera stays in its ranges and never jumps, also across tile edges; and the wash is the W3C soft light, bluer
  at the left and darker at the bottom.
- Both: the swaps, their dips and the wait for Nagaya follow the clock; a look that fails to build gives Yojōhan.
- And: the tilted, zooming plan covers the screen and the blur's strip, the GL matrix matches the camera, the
  grain and the lens map follow their formulas, the triangle buffer never overflows, and a key that wakes the
  faded UI does nothing else.

`tools/shot.sh OUT_DIR 1920 1080 T...` renders the same code on a computer, without a TV: PNGs of the plan at
times T (before the lens; add `--look nagaya` for Nagaya, and `--flat` to see it untilted). With `--stats` it
prints layout numbers instead: rooms on screen, room shapes, doors, fixtures and paint lightness, and for Nagaya
its tile's rooms, steps and joins and how its walls move.

## Based on cleanyourtv's launcher

Tatami Home's launcher (`home/app/`) is cleanyourtv's `launcher/` at commit `6e8615eb`, with its config defaults.
Its classes keep their package, `com.gathin.tvhome` (the Gradle namespace), so later upstream fixes copy in
cleanly. The installed app is `io.github.pbanddev.tatamihome`, next to cleanyourtv's own. What Tatami changed in
those files:

- `MainActivity`: the plan and the idle fade (`FloorPlanBackdrop`), a few lines.
- `Config` and `config.xml`: the three `floor_plan` keys.
- `LauncherSettingsActivity` and its layout: the Floor plan effects row, in place of the Wallpaper row.
- `styles.xml`: the window theme says it is opaque, as cleanyourtv's `PROMPT.md` asks.
- The manifest: Tatami's name, icon and banner, and its preview, effects list and screensaver.
- `home/tools/theme.sh` and `home/tools/theme_restart.py`: Tatami Home's package; `tv.py` comes from the
  cleanyourtv checkout.

`FloorPlanBackdrop` and `IdleFade` (the plan behind Home, and the fade) live in `home/app/`, in the plan's
package, `io.github.pbanddev.tatami`.

`floor_plan_idle_minutes` takes the place of the launcher's `home_idle_clock_minutes`.

## Small facts

- On the test TV, the remote's Home key, the screensaver and a reboot all land on Tatami. Home and the
  screensaver run in one app process: about 100 MB of memory with both on screen, Nagaya and every effect on.
- The release build is signed with your local debug key.
- Set the screensaver over adb: the add-on's is
  `adb shell settings put secure screensaver_components io.github.pbanddev.tatami/.FloorPlanDream`, Tatami Home's
  is `io.github.pbanddev.tatamihome/io.github.pbanddev.tatami.FloorPlanDream` (`install.sh` sets the add-on's,
  and `home/install.sh` moves Tatami Home 0.2's old name to the new one).
- Give Home back to cleanyourtv's launcher:
  `cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity` (`install.sh --home` prints the
  right one for your TV).
