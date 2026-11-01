# How Tatami works

Notes for people who want to change Tatami or check what it does. To install and use it, read the
[README](../README.md).

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

## The moving plan

- One rectangle is cut in two, then each half again, 6 levels deep: 64 rooms (a split tree).
- Every cut is a ratio between 0.25 and 0.75 that slides on its own slow sine. Rooms on both sides of a wall
  share that one number, so the area always adds up.
- Doors are quarter circles fixed to the walls, opening and closing. A tub, a toilet and stairs sit in some
  rooms and shrink with them rather than cross a wall.
- The camera is tilted 20 degrees, drifts and slowly pulls in and back. The plan is sized so the corners
  never show.
- Time is the wall clock, so every view of the plan shows the same frame at the same moment. That is why the
  hand-over from Home to the screensaver does not jump.

## Drawing

- OpenGL ES 2, all on the GPU. The scene is drawn with 4x MSAA.
- Tilt-shift draws it again at full size, averages it down to 1/4 (a 4x4 box, so thin walls do not flicker),
  blurs it with a separable Gaussian and blends it over the top and bottom. Grain and the vignette are one
  last pass.
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

Both were forced on the test TV: Home stayed the default and its UI kept working.

## Tests

`FloorPlan`, `Camera`, `CameraMatrix`, `Fixtures`, `SceneMesh` and `IdleFade` are plain Java with unit tests:
the rooms fill the plan at any time and never overlap, every door sits on a wall, fixtures stay in their rooms,
the tilted, zooming plan always covers the screen, the GL matrix matches the camera, the triangle buffer never
overflows, and a key that wakes the faded UI does nothing else.

## Based on cleanyourtv's launcher

The launcher is cleanyourtv's `launcher/` at commit `6e8615eb`, with its config defaults. Its classes keep their
package, `com.gathin.tvhome` (the Gradle namespace), so later upstream fixes copy in cleanly. The installed app
is `io.github.pbanddev.tatamihome`, next to cleanyourtv's own. What Tatami changed in those files:

- `MainActivity`: the plan and the idle fade (`FloorPlanBackdrop`), a few lines.
- `Config` and `config.xml`: the three `floor_plan` keys.
- `LauncherSettingsActivity` and its layout: the Floor plan effects row, in place of the Wallpaper row.
- `styles.xml`: the window theme says it is opaque, as cleanyourtv's `PROMPT.md` asks.
- The manifest: Tatami's name, icon and banner, and its preview, effects list and screensaver.
- `tools/theme.sh` and `tools/theme_restart.py`: Tatami's package; `tv.py` comes from the cleanyourtv checkout.

`floor_plan_idle_minutes` takes the place of the launcher's `home_idle_clock_minutes`.

## Small facts

- On the test TV, the remote's Home key, the screensaver and a reboot all land on Tatami. The screensaver uses
  about 35 MB of memory with every effect on.
- The release build is signed with your local debug key.
- Set the screensaver over adb:
  `adb shell settings put secure screensaver_components io.github.pbanddev.tatamihome/.FloorPlanDream`
- Give Home back to cleanyourtv's launcher:
  `cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity` (`install.sh --home` prints the
  right one for your TV).
