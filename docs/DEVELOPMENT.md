# Developing Memora

Native Android app in Kotlin with Jetpack Compose. All data stays on the phone (SQLite); reminders are local notifications scheduled with AlarmManager.

## Setup

1. **JDK 17 or 21**, with `JAVA_HOME` pointing to it (Android Studio's bundled JDK works).
2. **Android SDK** with platform 36 and build tools. `local.properties` holds its path:
   ```
   sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   ```
   Android Studio creates this file when you open the project.
3. Open the folder in **Android Studio**, or build from a terminal (below).

## Build and install

```bash
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
./gradlew installDebug           # installs on the connected phone/emulator
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

Without a release key, both build types are signed with `app/debug.keystore`. **Before publishing**, create your own key:

```bash
keytool -genkeypair -v -keystore memora.jks -alias memora -keyalg RSA -keysize 2048 -validity 10000
```

and set `MEMORA_KEYSTORE_FILE`, `MEMORA_KEYSTORE_PASSWORD`, `MEMORA_KEY_ALIAS` and `MEMORA_KEY_PASSWORD` in the environment. Keep that key safe: every future update must be signed with it.

## Releasing (GitHub Actions)

`.github/workflows/release.yml` builds the release APK and publishes it as a GitHub release with a SHA-256 checksum. Push a tag:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Before tagging, move the `## [Unreleased]` notes in `CHANGELOG.md` under a `## [1.0.1] - date` heading: the workflow uses that section as the release notes (or GitHub's generated notes if there isn't one). Then push the tag, or run **Release** from the Actions tab with a version number. The tag sets `versionName` (1.0.0) and `versionCode` (major×10000 + minor×100 + patch, so 1.0.0 is 10000), so each release installs over the last.

To sign with your own key, add repository secrets `MEMORA_KEYSTORE_BASE64` (`base64 -w0 memora.jks`), `MEMORA_KEYSTORE_PASSWORD`, `MEMORA_KEY_ALIAS` and `MEMORA_KEY_PASSWORD`. Without them the build is signed with the shared debug key and the run shows a warning. Local builds read the same `MEMORA_*` environment variables, and `-PversionName` / `-PversionCode`.

## Project layout

```
app/src/main/
  java/com/smnexstudio/memora/
    MainActivity.kt        Navigation, app lock, theme
    MemoraApp.kt           App start: database, templates, notification channel
    data/                  Models, Repo (SQLite: events, history, notes, festivals, settings; backup JSON),
                           AppPin (lock PIN), BackupCrypto (password-protected backups)
    logic/                 Dates & milestones, lunar dates, ready wishes, festivals, postcard drawing
    reminders/             Alarm scheduling, notification + snooze + boot receivers
    platform/              Contacts, sending via WhatsApp/SMS/email, card sharing, link guard
    widget/                "Coming up" home screen widget
    ui/                    "Postcard" theme, line icons, shared components (stamps, paper cards, tab bar, day sheet, PIN pad)
    ui/screens/            Tabs: Home, Calendar, People, Settings. Also Search, Edit, Event, Approve (send),
                           Missed, Import, Festivals, Reminder checks
  assets/templates.json    Ready wishes (formal / fun / family, English)
  res/                     Stamp logo, widget layout, colours
  res/font/                Fraunces, Caveat and Work Sans (SIL Open Font License, from Google Fonts)
```

## How reminders work

- Each event gets its 1–2 early reminders plus one on the day, at the reminder time from Settings. Each is a one-off exact alarm for its next date; when it fires, the next year's is scheduled.
- Milestones (18th, 21st, 25th, 50th… birthdays; 1st, 10th, 25th, 50th… anniversaries) get an extra reminder 2 weeks before.
- The on-the-day notification has WhatsApp, SMS and "Remind in 2h" buttons. Snooze works without opening the app.
- The Sunday digest lists the coming week's events, worked out when it fires.
- Alarms are rebuilt at app start, after any change, after a reboot and when the clock or time zone changes.
- Messages can use `{name}`, `{first}`, `{age}` and `{ordinal}` ("25th"), filled in when you send.

## Sending

"Send card" first asks how it should reach the person (WhatsApp, email or SMS); "Always use this" remembers the answer per person, and the notification's WhatsApp/SMS buttons skip the question. WhatsApp and email can then send a **postcard** or **text only**; SMS is text only.

- Postcards are drawn on the phone as a picture (`logic/Cards.kt`): five designs (Marigold, Airmail, Rangoli, Big number, Confetti) and, on WhatsApp, three shapes (square chat card, two-sided postcard, 9:16 Status). The preview is the same picture that gets sent.
- WhatsApp opens the person's chat with the picture and a caption (or asks for the chat when no number is saved). The Status shape opens WhatsApp's share list, where you choose My status; it can hide the age and use a shorter public line.
- Email attaches the postcard and puts the wish in the body; text-only email and WhatsApp use the typed message.
- Pictures are shared through a FileProvider from the app's cache; "Save the picture to my gallery" puts a copy in Pictures/Memora.
- Settings → Cards → "Sign cards as" adds "— name" under postcard wishes.

## Lunar-date events

An event with a known year can follow the Hindu lunar calendar (Edit → "Follow the Hindu lunar date"). It then repeats on the same lunar month and tithi as the original date, so its Gregorian date moves each year; reminders, the calendar, the widget and ages all use that date. `logic/Lunar.kt` works it out on the phone: amanta months (as in Karnataka), the tithi running at 6 AM local time, and sun/moon positions from Meeus's low-precision formulas. Very rarely a date may differ by a day from a printed panchang. If the tithi is skipped that year, the next tithi's day is used; adhika (extra) months are skipped.

## Notes

Each person has a free-text "About" note on their page and a log of gift ideas, gifts given (saved with the year) and other notes, in the `person_notes` table. Both are included in backups.

## App lock

Turning on Settings → App lock asks you to choose a 4–6 digit Memora PIN, then confirms with the phone's fingerprint or screen lock (when it has one). The lock screen takes either; if the phone later loses its fingerprint and screen lock, the PIN still opens the app. Only a salted PBKDF2 hash of the PIN is stored (`data/AppPin.kt`), never in backups. After 5 wrong tries each further wrong one waits longer (30 s, doubling, up to an hour). While the lock is on, Memora is hidden from screenshots and Recents, the lock screen sits in its own window above any open sheet, the widget shows no names, and lock-screen notifications are made private. Leaving the app for under a minute (to send on WhatsApp, say) doesn't relock; that window is timed on the uptime clock.

Other apps can start the main screen (the launcher must), so screen links carry a per-install token (`platform/LinkGuard.kt`); links without it open the home screen only.

## Backup

Settings → Back up saves a JSON file wherever you choose (Drive, Downloads…). Settings → Restore loads one and replaces everything on the phone. The format is the same as Memora 1.x, so older backups still restore. App lock isn't carried over. Backing up asks for an optional password (at least 6 characters); with one, the file is encrypted (AES-256-GCM, key from PBKDF2-SHA256) and restoring asks for it. Files over 5 MB are refused.

## Festivals

Settings → Festivals → Holiday calendars picks up to two calendars, e.g. a country and a religion (India by default; the list is `CALENDARS` in `logic/Festivals.kt`). Festivals come from Google Calendar's public holiday calendars (`logic/GoogleHolidays.kt`): the app downloads their iCal feeds (no account or API key) and stores this year's and next year's holidays in the `festival_dates` table. At app start and on the Festivals screen it downloads again if the table is empty, a year is missing, or the stored holidays are for other calendars. A failed or incomplete download is retried at most once an hour; "Try again", changing calendars and restoring a backup retry straight away. Downloads over 2 MB are refused. This is the app's only network use. No festivals or dates are hardcoded.

Each picked festival gets a reminder on the day, and optionally one a week before.

## Tests

```bash
./gradlew testDebugUnitTest    # date helpers and lunar dates (app/src/test)
./gradlew lintDebug            # Android lint; must report no errors
```

The release workflow runs the unit tests before building.

## Testing tips

- Add an event dated today, then set the reminder time a minute or two ahead to see the notification.
- On Xiaomi, Oppo, Vivo, Realme and Samsung phones, set Memora's battery use to "Unrestricted", or reminders may arrive late.
