# Shorts / Reels blocking — physical-device test checklist

The unit tests in `DetectionEngineTest.kt` and `PipelineFormatTest.kt` run on the
JVM against fabricated accessibility nodes. They prove the **rule engine** and
the **enforcement bookkeeping** behave deterministically. They do **not** prove
that the real YouTube / Instagram builds expose a signal the engine can see.

Never mark a real-device row as passing because its unit test passes.

## Before you start

Record the environment for every run:

- FocusESP build: `git rev-parse --short HEAD` (or the installed versionCode).
- Android version / API level.
- Device manufacturer + model.
- YouTube version, Instagram version (Play Store → app info).
- Whether the accessibility service reports **Enabled** on the Focus screen.

Open **Blocking → Content → Live pipeline**. Keep it visible while you navigate,
or return to it after each step and read the last summary + event log. Only
metadata is shown; no titles or messages are recorded.

## Pre-conditions

1. Enable the FocusESP accessibility service.
2. Turn on **Block YouTube Shorts** and **Block Instagram Reels**.
3. Do **not** start a Focus session. These restrictions are always-on and are
   independent of Focus Mode and the focus timer. The **Content** screen shows
   `Accessibility service is on and your content restrictions are enforced.`
4. Confirm the pipeline reports the restriction as enabled (the
   `blocking active` value now reflects the always-on restriction state).

## Unlock commitment

The three restriction features (YouTube Shorts, Instagram Reels and the
whole-app fallback) can always be **turned on**, but they can only be **turned
off** during an unlock window:

1. Press **Request unlock in 6 hours**. The appointment is persisted.
2. Wait the full six hours. Every restriction stays active the whole time.
3. At the appointment time a 60-second window opens. Open this screen and press
   **Confirm unlock** inside that exact window.
4. Once confirmed, the controls are editable until the window closes at
   `appointment + 60s`, then they lock again.
5. If you miss the window, the appointment is marked missed and you must file a
   brand new six-hour request. There is no grace period or late unlock.

These are device-test rows: consistent UI, correct countdown, and correct
re-locking are best verified by hand.

## Scenarios

For each row record: event type seen, `Window class`, `nodes=`, matched block /
allow rules, `Decision`, `Enforcement`, and whether the phone actually returned
Home.

| # | Scenario | Expected | Result / notes |
|---|----------|----------|----------------|
| 1 | Open a YouTube Short from the Shorts tab | Sent Home; `BLOCK_REQUESTED` then `BLOCK_VERIFIED` | |
| 2 | Open a normal YouTube video (watch) | Stays; `Decision: ALLOW` | |
| 3 | Search YouTube for "shorts", stay on results | Stays; `Decision: ALLOW` (no false block) | |
| 4 | Tap the Shorts nav tab but don't play a Short | Stays; `Decision: ALLOW` or `UNKNOWN` | |
| 5 | Open an Instagram Reel | Sent Home; `BLOCK_REQUESTED` then `BLOCK_VERIFIED` | |
| 6 | Instagram home feed / profile / Direct | Stay; `Decision: ALLOW` | |
| 7 | Chrome → `m.youtube.com/shorts/<id>` | Sent Home; rule `browser_yt_shorts_url` | |
| 8 | Chrome → `instagram.com/reel/<id>` | Sent Home; rule `browser_ig_reels_url` | |
| 9 | Chrome → `youtube.com/watch?v=...` | Stays; rule `browser_yt_watch_url` | |
| 10 | Rapidly switch allowed ↔ blocked screens | No repeated home-bounce; gate suppresses duplicates | |
| 11 | Stop Focus (or never start one), open a Short | Still blocked; restrictions are always-on | |
| 12 | Turn the toggles off | Refused while locked; controls lock until a confirmed window | |
| 13 | Revoke accessibility permission mid-session | `Service: disconnected` in the panel; the Content screen says it is **Not enforcing** | |
| 14 | Enable **Whole-App Fallback (YouTube)**, open YouTube | Sent Home for any YouTube screen; Instagram is unaffected | |
| 15 | Enable **Whole-App Fallback (Instagram)**, open Instagram | Sent Home for any Instagram screen | |
| 16 | Request unlock, wait 6 h, confirm inside the minute | Controls become editable, then lock again at the window close | |
| 17 | Request unlock, miss the minute | Missed message; all restrictions still active; new 6 h request required | |

## Interpreting failures

- **`Decision: UNKNOWN`, `nodes=0` or `(truncated)`** — the tree was not
  retrievable. Confirm "Retrieve window content" is granted. This is the case
  where per-screen detection cannot work; use the whole-app fallback.
- **`Decision: ALLOW` while a Short is clearly playing** — no strong signal was
  observed on this app version (its player activity/labels don't match). Capture
  the `Window class` and `nodes=` values and enable the whole-app fallback. Do
  not add a speculative rule; add one only if the observed signal is stable
  across reloads and app restarts.
- **`Enforcement: BLOCK_ACTION_FAILED`** — `GLOBAL_ACTION_HOME` was rejected or
  the app stayed on the same screen after the bounded retry. Note the device;
  some OEMs restrict global actions.
- **`Enforcement: SUPPRESSED_BY_GATE`** — expected for repeated identical
  events; confirms the loop guard is working, not a failure.

## Reporting a detection gap

When a Short/Reel is not blocked, record:

1. `Window class` from the Live pipeline panel (sanitized — it is a class name).
2. `nodes=` and whether it was `(truncated)`.
3. Which block/allow rules matched and the final `Decision`.
4. The exact app version.

That evidence is what justifies adding or adjusting a rule. Guessing at internal
activity names is explicitly out of scope.

## Honest limitations and tamper resistance

The restriction system is intentionally strong but it is **not unbypassable**.
Do not describe it as such. It persists its state and appointment timestamps in
app storage and derives eligibility from those timestamps, so it survives
backgrounding, process death and (subject to Android) device restarts. However:

- **Accessibility access can be revoked** by the user from Android settings. While
  it is off, nothing is enforced, and the Content screen reports this honestly
  rather than pretending blocking still works.
- **FocusESP can be force-stopped or uninstalled.** These are normal Android
  user controls; FocusESP deliberately does not add anti-uninstall traps or block
  access to system settings or recovery.
- **The device clock can be changed.** Within a single boot, a forward clock jump
  cannot open the window early because the monotonic anchor caps the elapsed time.
  After a reboot the monotonic clock resets and the wall clock becomes
  authoritative, so a user who changes the clock and reboots may shorten the
  wait. There is no network time source and perfect tamper resistance is not
  claimed.
- **Time-zone changes** do not affect eligibility (only elapsed time matters);
  the displayed local appointment time naturally follows the device time zone.
- **Detection is best-effort.** YouTube and Instagram change their screens, so
  Shorts/Reels detection can miss a surface. The whole-app fallback is the honest
  answer when per-screen detection is unreliable, and it is never enabled
  automatically.
- **Alarms are reminders only.** A delayed or missing notification never opens,
  extends or restores a window; only the persisted timestamps decide.
