# Mürren

Named for the car-free village above Lauterbrunnen. Its icon is the Jungfrau as seen from there. Mürren rewards you for leaving your phone alone.

Every stretch you don't unlock your phone earns a quiet, distinctive buzz. Nothing appears on screen, so there's nothing to check. The buzzes get rarer as you improve, and eventually they stop. The goal is for the app to make itself unnecessary.

## How it works

Mürren treats your day like a run. It starts when quiet hours end, and your pace is live in the status bar. Every phone-free stretch is a **split**. Your own usual day is the **ghost** you race. When quiet hours begin, you cross the **finish**.

**Splits.** The timer starts when the screen turns off.

```
screen off ──30m──▶ split buzz ──60m──▶ split buzz ──120m──▶ split buzz ──▶ silent for the rest of the day
```

- A *compulsive* unlock restarts the clock and drops you **one rung**, never to zero. Nothing announces it.
- If a rung is earned while you're on a forgiven unlock, the buzz waits until the screen goes off. It never buzzes in your hand.
- **Silent means silent.** With the ringer on silent or Do Not Disturb on, rungs are still earned, but without the buzz.

**What doesn't count against you**
- **Allowed apps** (phone, texts, work chat): pick them in the app. Defaults to your phone and SMS apps.
- **Quick checks**: unlocks shorter than the grace period (20 s by default), up to 4 an hour.
- **Screen waking for a notification**: only an actual unlock counts.

**Zack Zack sprints.** Named for the Swiss-German *"zack zack!"* (quick, snap to it) from the football pitch: phone down, now.
- Start one from the **Zack Zack** Quick Settings tile (tap again to stop) or from the app: 15, 25 or 45 minutes.
- While it runs, the status bar chip and the lock screen count down.
- Make it to the end without using the phone and you get the zack-zack buzz (two sharp taps, twice).
- Allowed apps and quick checks don't break a sprint. Anything else ends it there, logged as how far you got ("18/25"), no failure message.
- A sprint is something you start with a clear end, which is exactly what Android's Live Updates are meant for.

**Quiet hours**, learned from your sleep
- Mürren learns quiet hours from the longest overnight stretch your phone sits untouched: the median bedtime and wake time over the last 14 nights, once 3 nights are recorded. Checks under 2 minutes, like glancing at the time, don't break a night.
- It learns from rest, not from use, so late-night scrolling can't push quiet hours later and hide from the pace clock. Until it has learned, or if you switch to manual, 22:00–07:00 or your own times apply.
- No buzzes overnight.
- Get through the whole night phone-free and you get a distinct **morning buzz** when quiet hours end, plus "Phone-free overnight ✓" in that day's summary.

**Pace clock.** Like a running pace: get your number down. Your status bar shows a **ring that drains like a battery** as you use the phone (and refills while you're away), with your pace beside it: **◔ 6**. The lock screen shows the same notification.

- While you're in an app that isn't allowed, the number **counts up live**, about a minute per minute, like a taxi meter.
- Put the phone down and it **starts falling right away**. When you next pick the phone up, the lock screen shows the time you've earned back.
- Allowed apps never count. It's hidden during quiet hours.
- A switch in Settings flips it to time *off* phone ("54 min off phone / hour"), where higher is better.
- Under the hood: an exponential moving average of time on the phone, with a 45-minute half-life.
- On Android 16+, it's a Live Update chip. If Live Updates are turned off for Mürren, the number is drawn as the status bar icon instead.
- It replaces the "Running quietly" notification Android requires anyway. It never makes a sound.

**The main screen** is a watch face for the day (dark or light, following your phone):
- **Pace**, in big condensed numerals inside the same draining ring.
- **Pacer line**: the same line as the notification, with your minutes under (−) or over (+) your usual day above the dot.
- **Average today · current split · splits so far**, side by side.
- **Race**: today's running total of time on phone against the ghost of your usual day (the same weekday over the last 4 weeks, or the last 7 days early on).
- **Splits**: numbered like laps, with a dot for each split buzz.
- **Finish**: the day's summary.

**The notification** is just the ring and one line: *"1:40 today · split 0:15"*, your time on the phone today and your current run off it (a split is the time since you last used something that isn't allowed). Swipe it away and it stays hidden until the next morning (opening the app brings it back); your pace is still tracked. The app adds a pacer line under its ring, with an orange mark at your usual pace for this time of day.

**Daily summary.** One silent notification when quiet hours begin:

> Phone-free overnight ✓
> 1h 40m on phone · average 5/hr
> 6 splits · longest 2h 15m
> Negative split ✓ (less phone in the second half)
> 12 min under your usual Thursday
> +22m for people · 3 quick checks
> Unlocked right after a split buzz: 1 of 4
> Z  25 ✓   18/25
> 19 unlocks — 11 for people, 3 quick checks, 5 others

No streaks and no comparisons. That last line checks whether the buzz itself pulls you back to the phone; if it often does, buzzes should move to your next pickup. The app has no countdown to the next buzz either, because a timer would be one more reason to unlock. The pace clock follows the same rule: you only see it when you're already holding the phone.

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

## Credits

Numerals are set in [Barlow Condensed](https://github.com/jpt/barlow) by Jeremy Tribby, licensed under the SIL Open Font License 1.1 (`app/src/main/assets/fonts/OFL.txt`).
