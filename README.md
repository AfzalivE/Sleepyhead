# Sleepyhead

Sleepyhead is an Android app that helps you get out of bed and to your desk in the morning,
instead of reading news, social media and work chat in bed.

## What it does

- **Morning lock.** From your wake-up time, the apps you choose (news, social, work chat) are blocked.
  When you open one, Sleepyhead sends you back to its calm screen. The lock stops when you finish
  your routine, or at the "lock ends" time.
- **Proof-based checklist.** Each step of your routine has a printed QR code in its place:
  bathroom, kitchen, desk. You finish a step only when you scan its code. You must do the steps in order.
- **Calm start screen.** The screen shows only the next step, a timer since wake-up,
  and the time left until you should be at your desk.
- **Streaks and log.** You see how many on-time mornings you have in a row, your best streak,
  and a 30-day log. The log also counts how often you reached for a blocked app.
- **Emergency skip.** Hold the text at the bottom of the calm screen for 10 seconds to unlock for the day.
  A skipped day breaks the streak.

Default routine (you can edit it in Settings → Steps):

1. Brush your teeth (Bathroom)
2. Eat breakfast (Kitchen)
3. Sit down at your desk (Home office desk)

Default times: wake-up 6:30, at the desk by 8:00, lock ends 11:00, Monday to Friday.

## Build

Requirements: JDK 17 or newer and the Android SDK (platform 35). Android Studio has both.

```sh
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests for the routine and streak rules
./gradlew installDebug         # install on a connected phone (USB debugging on)
```

## Setup on the phone

1. Install the APK.
2. Open Sleepyhead → Settings (gear icon).
3. **Morning lock:** tap *Turn on*. In Accessibility, open *Sleepyhead morning lock* and turn it on.
   - On Android 13 and newer, a sideloaded app can show **"Restricted setting"**.
     Open *App info* for Sleepyhead (there is a button in Settings), tap the **⋮** menu,
     choose **Allow restricted settings**, then turn on the service again.
   - The service only checks which app is in front. It does not read the screen.
4. **Camera:** tap *Allow*. The camera is used only to scan your codes.
5. **Blocked apps:** check the list. Sleepyhead selects common news, social and chat apps
   that are installed (for example X, Instagram, Reddit, Slack, Teams, Gmail, YouTube).
6. Set your wake-up time, "at my desk by" time, lock end time and days.

## Print the QR codes

1. Tap the QR icon on the home screen.
2. Tap **Print or save as PDF**. You get one page per step. You can also save a PDF and print it later.
3. Tape each code in its place. Put the bathroom code where you cannot scan it from bed.

Each install makes its own random codes. If you reinstall the app, or tap *Make new codes*
in Settings → Steps, print the codes again.

## Try it now

Set the wake-up time to a few minutes ago, then open a blocked app. Sleepyhead opens instead.
Scan the codes in order. *Settings → Clear today's progress* resets today.

## Limits

- You can change the routine and blocked apps only when the lock is off.
  You can still turn off the Accessibility service in system settings. Sleepyhead does not try to stop that.
- The morning window must be within one day (wake-up time before lock end time).
- Data stays on the phone (SharedPreferences). There is no account and no network use.

## Code map

| Path | What it holds |
| --- | --- |
| `data/Routine.kt` | Pure rules: lock window, step order, on-time, streaks (unit tested) |
| `data/Store.kt` | Saves settings, steps and the daily log as JSON |
| `lock/LockService.kt` | Accessibility service that sends you back from blocked apps |
| `qr/QrCodes.kt` | QR generation and the print/PDF document |
| `ui/` | Jetpack Compose screens: home, settings, blocked apps, steps, codes, history |
