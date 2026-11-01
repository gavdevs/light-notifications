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

## License

MIT.
