# Sleepyhead

Sleepyhead is an Android app that helps you get out of bed and to your desk in the morning,
instead of reading news, social media and work chat in bed.

## What it does

- **Onboarding.** The first launch walks you through the lock times, the apps to block,
  your steps, the permissions, travel mode and printing the QR codes.
- **Morning lock.** From lock start (your wake-up time), the apps you choose (news, social, work chat) are blocked.
  When you open one, Sleepyhead sends you back to its calm screen. The lock stops when you finish
  your routine, or at lock end.
- **Proof-based checklist.** Each step of your routine has a printed QR code, an NFC tag, or both, in its place:
  bathroom, kitchen, desk. You finish a step only when you scan its code or tap its tag. You must do the steps in order.
- **Calm start screen.** The screen shows only the next step, a timer since lock start,
  and the time left until lock end.
- **Streaks and log.** A morning counts when you finish before lock end. You see your current streak,
  your best streak, and a 30-day log. The log also counts how often you reached for a blocked app.
- **Travel mode.** Save your home location once. At lock start, if your phone is away from home,
  the lock stays off for that day. Travel days do not break your streak. There is no manual
  "lock off" switch, so travel mode is not an easy way out.
- **Emergency skip.** Hold the text at the bottom of the calm screen for 10 seconds to unlock for the day.
  A skipped day breaks the streak.

Default routine (you can edit it in Settings → Steps):

1. Brush your teeth (Bathroom)
2. Eat breakfast (Kitchen)
3. Sit down at your desk (Home office desk)

Default times: lock starts 6:30, lock ends 9:00, Monday to Friday.

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
6. Set your lock start, lock end and days.
7. Optional: in Settings → Travel, allow location ("Allow all the time"), tap *Set to here* while you are at home,
   and check that *Travel mode* is on (saving home the first time turns it on). Anywhere within 2 km of home counts as home.

The first launch shows these steps as onboarding. Settings → *Show the welcome steps again* shows them again.

## NFC tags

1. Settings → Steps → *Pair NFC tag* on a step.
2. Hold your phone to the tag. Sleepyhead saves the tag ID. If the tag is writable (for example NTAG213 stickers),
   it also writes a Sleepyhead record to it, so a tap opens Sleepyhead and finishes the step even when the app is closed.
3. Stick the tag in that place. A read-only tag works too, but open Sleepyhead before you tap it.

## Print the QR codes

1. Tap the QR icon on the home screen.
2. Tap **Print or save as PDF**. You get one page per step. You can also save a PDF and print it later.
3. Tape each code in its place. Put the bathroom code where you cannot scan it from bed.

Each install makes its own random codes. If you reinstall the app, or tap *Make new QR codes*
in Settings → Steps, print the codes again.

## Try it now

Set the lock start to a few minutes ago, then open a blocked app. Sleepyhead opens instead.
Scan the codes in order. *Settings → Clear today's progress* resets today.

## Limits

- You can change the routine and blocked apps only when the lock is off.
  You can still turn off the Accessibility service in system settings. Sleepyhead does not try to stop that.
- The morning window must be within one day (lock start before lock end).
- Data stays on the phone (SharedPreferences). There is no account and no network use.
- If Sleepyhead cannot get your location at lock start, the lock stays on.

## Code map

| Path | What it holds |
| --- | --- |
| `data/Routine.kt` | Pure rules: lock window, step order, on-time, streaks (unit tested) |
| `data/Store.kt` | Saves settings, steps and the daily log as JSON |
| `lock/LockService.kt` | Accessibility service that sends you back from blocked apps |
| `nfc/Nfc.kt` | NFC reader mode, tag pairing and writing, and tags that open the app |
| `travel/TravelCheck.kt` | Home/away check at lock start, and the lock-start alarm |
| `qr/QrCodes.kt` | QR generation and the print/PDF document |
| `ui/` | Jetpack Compose screens: onboarding, home, settings, blocked apps, steps, codes, history |
