# Tatami

An Android TV home screen and screensaver: a tilted floor plan whose walls keep sliding, so one room grows
while its neighbour shrinks, with no gaps and no overlaps. Inspired by the ending of *The Tatami Galaxy* (2010).
It is drawn natively every frame, not played from a video. No footage or frames from the show are included.

The home screen is [cleanyourtv](https://github.com/Gathin23/cleanyourtv)'s launcher (dock, app grid, clock,
Inputs, Wi-Fi and Settings) with the floor plan moving behind it. After two minutes without a key the UI fades
and only the plan shows; any key brings it back.

![Tatami Home: the highlight moves along the dock, then the UI fades to the plan and comes back](docs/home.webp)

*Recorded on the TV. [Full video: MP4, 720p, 20 s](docs/home.mp4)*

| Home | The screensaver | The effects list |
|---|---|---|
| ![The dock and the clock over the moving plan](docs/home.jpg) | ![The floor plan, sharp in the middle and soft at the top and bottom](docs/screenshot.jpg) | ![The effects list over the live floor plan](docs/effects.jpg) |

[The screensaver alone: animation](docs/demo.webp) · [MP4, 720p, 15 s](docs/demo.mp4)

## Tested on

A Sony Android TV 10 set at 1080p:

- Screensaver: a steady 60 fps with every effect on, about 35 MB of memory.
- Home: the plan at 60 fps behind the UI, and the launcher's own frames shown on every refresh while the
  highlight moves along the dock.
- The remote's Home, the screensaver and a reboot all land on Tatami.

## What it asks for

No `INTERNET` permission: nothing here uses the network. The APK is about 580 KB.

| In the manifest | Why |
|---|---|
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | The Wi-Fi pill: connected or not, and the network's name. |
| `ACCESS_FINE_LOCATION` | Android 10 and newer only show the Wi-Fi name to apps with location access. Never asked for on screen: `tools/install.sh` grants it. |
| `WRITE_SECURE_SETTINGS` | Only for the launcher's optional remote-key helpers. Nothing grants it unless you set those up (cleanyourtv's `launcher/build-and-install.sh`). |
| Two accessibility services (`HomeKeyService`, `InputKeyService`) and a notification listener (`GuardRevival`) | The same optional remote-key helpers. All off until you switch them on in TV Settings; Tatami does not. |
| `yandex.tv.permission.ACCESS_TV_SOURCE` | Inputs on Yandex TV OS; ignored on other TVs. |

All of these come from cleanyourtv's launcher, unchanged.

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0. The install script talks to the TV with `tools/tv.py`
from a [cleanyourtv](https://github.com/Gathin23/cleanyourtv) checkout next to this one (`../cleanyourtv`, or set
`CLEANYOURTV` to an absolute path), with its `tools/venv` made as its README says.

```bash
./gradlew testReleaseUnitTest assembleRelease
TV_HOST=192.168.1.50 tools/install.sh          # install or update, next to the launcher the TV has
TV_HOST=192.168.1.50 tools/install.sh --home   # also make Tatami Home, and check that it took
```

The release build is signed with your local debug key. `install.sh` never disables a launcher: keep the one you
had, so there is always a way back. `--home` prints the command that gives Home back to it, such as
`cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity`.

## Use it

- **Home:** as in cleanyourtv's launcher ([its guide](https://github.com/Gathin23/cleanyourtv/blob/main/docs/launcher.md)).
  Hold OK on **Settings** for the launcher's menu, then **Launcher settings**.
- **Effects:** Launcher settings > **Floor plan effects** (in place of the Wallpaper row). Each one switches on or
  off and the plan behind the list shows the result at once: film grain, vignette, tilt-shift edges, doors,
  fixtures (plan symbols), camera drift and zoom. All are on by default. Home and the screensaver share them.
- **Idle:** after `floor_plan_idle_minutes` (2) without a key, the UI fades and the plan shows at full strength.
  An arrow, OK or Back only brings the UI back; volume still changes the volume.
- **Screensaver:** Settings > Device Preferences > Screen saver > *Tatami floor plan*. Or over adb:
  `adb shell settings put secure screensaver_components io.github.pbanddev.tatamihome/.FloorPlanDream`.
  Its frame matches Home's (both follow the wall clock), so the hand-over does not jump.
- **Preview:** from another launcher, the *Tatami* tile shows the plan alone; OK opens the effects.

## Settings and themes

Tatami reads the launcher's settings the same way: `app/src/main/res/values/config.xml`, overlaid by a theme's
`config.json` on the TV. Every key in
[cleanyourtv's themes/README.md](https://github.com/Gathin23/cleanyourtv/blob/main/themes/README.md) works, plus:

| Key | Default | What it does |
|---|---|---|
| `floor_plan` | `true` | The plan behind Home. `false` gives the launcher's own background and wallpapers back. |
| `floor_plan_dim` | `35` | How dark the plan is behind the UI, 0 to 90 %, so white text stays easy to read. |
| `floor_plan_idle_minutes` | `2` | Minutes without a key before the UI fades; `0` never fades it. Takes the place of `home_idle_clock_minutes`. |

Keep your own settings (your name, your dock) out of this repo: put them in a folder of your own with a
`config.json`, and push it with `tools/theme.sh`, a copy of cleanyourtv's that targets Tatami's package.

```json
{
  "greeting_name": "Sam",
  "columns": 6,
  "dock_apps": ["com.google.android.youtube.tv", "com.netflix.ninja", "tv.twitch.android.app"]
}
```

```bash
TV_HOST=192.168.1.50 tools/theme.sh ~/my-tatami-theme     # --reset removes it
```

As in cleanyourtv, a push or a reset clears the choices made in Launcher settings on the TV.

## How it works

- One rectangle is cut in two, then each half again, 6 levels deep: 64 rooms (a split tree).
- Every cut is a ratio between 0.25 and 0.75 that slides on its own slow sine. Rooms on both
  sides of a wall share that one number, so the area always adds up.
- Doors are quarter circles fixed to the walls, opening and closing. A tub, a toilet and stairs
  sit in some rooms and shrink with them rather than cross a wall. The camera is tilted 20
  degrees, drifts and slowly pulls in and back; the plan is sized so the corners never show.
- Time is the wall clock, so every view of the plan shows the same frame at the same moment.
- OpenGL ES 2, all on the GPU. The scene is drawn with 4x MSAA. Tilt-shift draws it again at full
  size, averages it down to 1/4 (a 4x4 box, so thin walls do not flicker), blurs it with a
  separable Gaussian and blends it over the top and bottom. Grain and the vignette are one last pass.
  Only the scene's triangles go from the CPU to the GPU each frame.
- On Home the plan is a `GLSurfaceView` under the launcher's views, which draw over it with a dark layer at
  their bottom; the idle fade fades those views as one. The plan draws one frame per display refresh, asked
  for by the `Choreographer`. Left to draw as fast as the surface takes frames, it could settle a frame ahead
  after Home sat still, and the launcher's frames then waited behind it on the GPU: 98 % janky over the dock
  on the test TV, while the plan itself still showed 60 fps.
- Home must not crash: on Android 10 one crash of a Home that is not a system app makes Android forget it as
  the default, and the next Home press shows the chooser. So a GL failure never ends the app. The renderer
  catches its own (a shader or a render target the driver refuses, memory running out) and clears to the floor
  colour; a failure in Android's EGL setup ends only the drawing thread, the window is let go of the frame it
  waited for, and Home's next start leaves the plan out until the app is updated. Both were forced on the test TV:
  Home stayed the default and its UI kept working.

`FloorPlan`, `Camera`, `CameraMatrix`, `Fixtures`, `SceneMesh` and `IdleFade` are plain Java with unit tests:
the rooms fill the plan at any time and never overlap, every door sits on a wall, fixtures stay in
their rooms, the tilted, zooming plan always covers the screen, the GL matrix matches the camera,
the triangle buffer never overflows, and a key that wakes the faded UI does nothing else.

## Based on cleanyourtv's launcher

The launcher is cleanyourtv's `launcher/` at commit `6e8615eb`, with its config defaults. Its classes keep their
package, `com.gathin.tvhome` (the Gradle namespace), so later upstream fixes copy in cleanly; the installed app
is `io.github.pbanddev.tatamihome`, next to cleanyourtv's own. What Tatami changed in those files:

- `MainActivity`: the plan and the idle fade (`FloorPlanBackdrop`), a few lines.
- `Config` and `config.xml`: the three keys above.
- `LauncherSettingsActivity` and its layout: the Floor plan effects row.
- `styles.xml`: the window theme says it is opaque, as cleanyourtv's `PROMPT.md` asks.
- The manifest: Tatami's name, icon and banner, and its preview, effects list and screensaver.
- `tools/theme.sh` and `tools/theme_restart.py`: Tatami's package; `tv.py` comes from the cleanyourtv checkout.

## Credits

- Heavily inspired by the ending of *The Tatami Galaxy* (Madhouse, 2010). This is a fan work,
  drawn from code.
- The home screen is [cleanyourtv](https://github.com/Gathin23/cleanyourtv)'s launcher by Gathin (MIT), and
  window and TV rules follow its `PROMPT.md`.
- The Outfit typeface ([`FONT-LICENSE-OFL.txt`](FONT-LICENSE-OFL.txt), SIL OFL 1.1) and the Halloween skin's
  pictures from Microsoft's Fluent Emoji ([`ICONS-LICENSE-FLUENT-EMOJI.txt`](ICONS-LICENSE-FLUENT-EMOJI.txt), MIT)
  come with the launcher.

## License

MIT: [LICENSE](LICENSE).
