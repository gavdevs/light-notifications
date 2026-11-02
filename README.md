# Notifications

A standalone viewer for the notifications currently posted on a Light Phone III,
built with the [Light SDK](https://github.com/lightphone/light-sdk).

The Light Phone deliberately does not surface the Android notification shade.
This tool shows what is actually posted right now, as plain text rows, and lets
you act on it: tap to open, ✕ to dismiss.

There is no history and nothing is stored — it is a window onto the live list,
so it answers "what is waiting for me" and nothing more.

## Behavior

- Rows show the notification title, with the body as a single line when there
  is one, sorted alphabetically.
- **Tap** opens the notification's own target; if that intent is gone, the app
  is launched instead.
- **✕** dismisses it, from the list and from the system.
- **Long-press** for: open app, notification settings, app info, copy text, and
  mute app.

Media-session notifications, ongoing notifications, and the LightOS keep-alive
post are filtered out, as are group summaries that duplicate their children.
The rules live in the SDK's `NotificationFilter`, where they are unit tested.

## Access

Reading notifications requires the user to grant notification access in system
settings. The tool checks for it on every resume and shows a single button that
opens the right settings screen when it is missing. Revoking access takes you
straight back to that screen.

## Swipe down to open

While the toolbox is in front, a swipe down from the top of the screen opens this
tool — the gesture LightOS does not have, since it has no notification shade.
The strip only exists on the toolbox and never appears over another app, so it
cannot take a touch from anything that was not already empty screen.

This is optional and is not part of the SDK tool: it arrives from `:server`, a
module the Light SDK plugin does not scan. That means **this build is
sideload-only.** Declaring an accessibility service and an overlay permission
puts it outside what Light's dashboard will accept, so install it yourself.

Two grants are needed, once, over adb:

```sh
# Lets the swipe start this app from the background. Without it Android drops the
# launch silently: the swipe animates and opens nothing.
adb shell appops set com.loosewiredev.notifications SYSTEM_ALERT_WINDOW allow

# Enables the service. Settings > Accessibility also works, but LightOS ships no
# screen for it, so this is the shorter path. Written as one shell so it appends to
# whatever is already enabled rather than replacing it — BrightControl and others
# live in the same list.
adb shell sh -c 'cur=$(settings get secure enabled_accessibility_services); \
  if [ -z "$cur" ] || [ "$cur" = null ]; then \
    settings put secure enabled_accessibility_services \
      com.loosewiredev.notifications/com.loosewiredev.notifications.server.SwipeDownService; \
  else settings put secure enabled_accessibility_services \
      "$cur:com.loosewiredev.notifications/com.loosewiredev.notifications.server.SwipeDownService"; fi; \
  settings put secure accessibility_enabled 1'
```

Leave the app out of it with `adb shell appops set com.loosewiredev.notifications
SYSTEM_ALERT_WINDOW deny` and the service stands down on its own; it checks the
grant before drawing anything rather than animating a swipe that cannot fire.

The service reads one thing: which package came to the front. It is declared
`canRetrieveWindowContent="false"` and never reads the screen.

## Building

Consumes the Light SDK as an adjacent composite build:

```
light/
├── light-sdk/
└── light-notifications/
```

```sh
JAVA_HOME=/path/to/jdk17 ANDROID_HOME=/path/to/android-sdk ./gradlew :app:assembleDebug
```

Point at a different SDK with `-Pnotifications.sdkPath=/path/to/light-sdk`.

The swipe-down service lives in `:server` rather than `:app` for one reason: the
Light SDK plugin skips any module named `server` for its source scan, its
manifest check and its dependency allowlist. A hand-written manifest in `:app`
is a build error, and an accessibility service cannot be declared in
`lighttool.toml`, so a module the plugin never looks at is the only route.
Everything privileged is in `:server`; nothing of it sits in `:app`.

## License

MIT.
