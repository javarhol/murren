# Mürren

Named for the car-free village above Lauterbrunnen. Mürren rewards you for leaving your phone alone.

Every stretch you don't unlock your phone earns a quiet, distinctive buzz. Nothing appears on screen, so there's nothing to check. The buzzes get rarer as you improve, and eventually they stop. The goal is for the app to make itself unnecessary.

## How it works

**The day ladder.** The timer starts when the screen turns off.

```
screen off ──30m──▶ buzz ──60m──▶ buzz ──120m──▶ buzz ──▶ silent for the rest of the day
```

- A *compulsive* unlock restarts the clock and drops you **one rung**, never to zero. Nothing announces it.
- If a rung is earned while you're on a forgiven unlock, the buzz waits until the screen goes off. It never buzzes in your hand.
- **Silent means silent.** With the ringer on silent or Do Not Disturb on, rungs are still earned, but without the buzz.

**What doesn't count against you**
- **Allowed apps** (phone, texts, work chat): pick them in the app. Defaults to your phone and SMS apps.
- **Quick checks**: unlocks shorter than the grace period (20 s by default), up to 4 an hour.
- **Screen waking for a notification**: only an actual unlock counts.

**Quiet hours** (22:00–07:00 by default)
- No buzzes overnight.
- Get through the whole night phone-free and you get a distinct **morning buzz** when quiet hours end, plus "Phone-free overnight ✓" in that day's summary.

**Pace clock.** Your status bar shows **`54/hr`**, and the lock screen shows *"You're on pace for 54 min off phone / hour · On track for about 12h 10m off phone today"*.

- While you're in an app that isn't allowed, the number **counts down live**, about a minute per minute, like a taxi meter.
- Put the phone down and it **starts climbing right away**. When you next pick the phone up, the lock screen shows the pace you've earned back.
- Allowed apps never cost pace. It's hidden during quiet hours.
- Under the hood: an exponential moving average of time off the phone, with a 45-minute half-life.
- On Android 16+, it's a Live Update chip. If Live Updates are turned off for Mürren, the number is drawn as the status bar icon instead.
- It replaces the "Running quietly" notification Android requires anyway. It never makes a sound.

**Daily summary.** One silent notification when quiet hours begin:

> 6h 10m phone-free · longest stretch 2h 15m
> 19 unlocks — 11 for people, 3 quick checks, 5 others

No streaks and no comparisons. The app has no countdown to the next buzz either, because a timer would be one more reason to unlock. The pace clock follows the same rule: you only see it when you're already holding the phone.

## Install

Every push to `main` builds an APK on GitHub Actions and publishes it as the **`latest`** release.

1. On your phone, open the repo's **Releases → latest** and download `murren.apk`.
2. Open it and allow "Install unknown apps" for your browser when asked.
3. In Mürren, grant the setup permissions (usage access, precise timing, keep running), then tap **Start**.

Builds are signed with the committed debug key, so a new APK installs over the old one and keeps your data.

**Test mode** (in settings) turns minutes into seconds, so the ladder runs 30 s → 60 s → 120 s. **Feel the buzzes** plays each pattern on demand.

## Development

- Kotlin, no third-party dependencies, minSdk 29 / targetSdk 36.
- `./gradlew assembleDebug` needs a local Android SDK. CI uses the one on GitHub's runners.
- With a phone connected over USB: `./gradlew installDebug`, and `adb logcat` for logs.

| File | Role |
|---|---|
| `Engine.kt` | The ladder, quiet hours, and day rollover: all reward logic |
| `Judge.kt` | Classifies an unlock as people / quick check / compulsive |
| `WatchService.kt` | Foreground service that receives unlock and screen-off events |
| `Alarms.kt` | Reward and quiet-hour alarms |
| `Pace.kt` | The pace clock math |
| `Notifier.kt` | Pace notification (Live Update / status bar) and the evening summary |
| `Buzz.kt` | The vibration patterns |
| `Prefs.kt` | Settings, engine state, per-day stats |

## Roadmap

- **Multi-day fading**: phases that move from 30/60/120 to 60/120, then 120 only, then rare random buzzes, then summary only, advancing on a rolling 3-day window (about 66 days is the median for a habit to stick).
- **Pause before chosen apps** (one sec style): a short breathing overlay, no typing.
- **Watch rewards**: buzz on the wrist instead of the phone (Pebble first). Probably the path to iPhone, where apps can't detect unlocks or vibrate from the background.
