# Changelog

All notable changes to Memora are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Look and feel

- A second theme, Departures: the app as a station board, with split-flap date tiles, condensed capitals and mono times, in light and dark. Choose it in Settings → Appearance → Theme; Postcard stays the default.
- Settings → Appearance → Theme is now Mode (System, Light or Dark), and applies to both themes.
- Backups keep the chosen theme.
- The reminder time is picked on a clock face drawn in the theme, instead of Android's own clock.
- New card designs for each kind of day: Party, Blush and Ribbons for birthdays; Love letter, Golden rings and Wildflower for anniversaries; Plain, Classic and In memory for graduations, remembrance days and other occasions. The heading on the card ("Happy birthday, dear Ananya") can be changed before sending.

### Reminders

- Settings → Reminders → How to remind: Notification, Alarm or Both. An alarm rings on your alarm tone until you stop it, fills the lock screen, and can be put off for 2 hours.
- Reminders keep working with hundreds of people. They now share one alarm instead of one each, which ran into Android's limit of 500 alarms per app.
- Reminder checks shows when the next reminder is due, when reminders last went out and anything Android refused, and can share a diagnostics log (what the app did, never names or dates).

### Privacy and security

- Names stay off the lock screen: reminders and alarms say "You have a reminder" until you unlock. On by default; Settings → Privacy.
- Settings → Privacy → Hide from screenshots keeps Memora out of screenshots, screen recordings and Recents, without needing app lock.
- App lock accepts only strong fingerprint or face unlock; phones without one use the screen lock or the Memora PIN.
- Backups: saving without a password is now a separate, deliberate choice, passwords need at least 8 characters, and a protected backup's header can no longer be altered without the password failing. Older backups still restore.
- Settings → Backup and restore → Erase everything removes every person, note, setting and the PIN. Settings → Cards can delete the cards saved to your gallery.
- Home reminds you to back up when you have no backup, or none for 3 months.
- Festival dates are downloaded only after you switch festivals on.
- New [PRIVACY.md](https://github.com/smnexstudio/Memora/blob/main/PRIVACY.md) and [SECURITY.md](https://github.com/smnexstudio/Memora/blob/main/SECURITY.md) say exactly what Memora stores, what goes online and how to report a security problem.

### Fixes

- Tapping a reminder, or its WhatsApp or SMS button, now opens that person's card instead of the home screen or the last screen you were on.

### Under the hood

- Release builds can only be signed with the release key, never the public debug key. Debug builds install beside the release app as Memora Debug.
- Every change is built, unit-tested and linted automatically, and library versions are pinned.

## [1.0.0] - 2026-10-10

The first release of the native Android Memora, rebuilt in Kotlin and Jetpack Compose. Needs Android 13 or later. Backups from the earlier Memora 1.x app restore into it.

### Reminders

- Birthdays, anniversaries and other events (housewarmings, graduations, farewells), with or without the year.
- One or two early reminders (1 week, 3 days, 2 days or 1 day before) and a reminder on the day, at the time you choose.
- Milestone reminders two weeks before big birthdays and anniversaries.
- A Sunday digest of the week ahead, and a Missed list for belated wishes.
- Lunar-date events that repeat on the same Hindu tithi each year, worked out on the phone.
- Import birthdays and anniversaries from phone contacts, and contact suggestions while typing a name or phone number.
- Reminder checks that flag notifications, exact alarms and battery settings that could delay reminders.

### Sending wishes

- "How to send" shows an envelope addressed to the person, with WhatsApp, email and SMS as stamp tiles, and can always use the same one for that person.
- Postcards drawn on the phone in five designs (Marigold, Airmail, Rangoli, Big number, Confetti), shaped for a WhatsApp chat, a two-sided postcard or WhatsApp Status.
- Plain-text messages with chat and email previews.
- Ready-made wishes in formal, fun and family tones, with `{name}`, `{first}`, `{age}` and `{ordinal}` filled in.
- Sign cards with your name, and optionally save card pictures to the gallery.
- Card pictures shared with other apps are cleared from the phone after a day.

### Festivals

- Festivals from Google's public holiday calendars: up to two of 19 countries and 3 religious calendars.
- A reminder on the festival morning, and optionally one a week before.
- The next festival shows on Home and in the calendar, for information only.

### People and notes

- A page for each person with an About note, gift ideas, gifts given by year and other notes.
- A history of every card sent or skipped.
- Home, Calendar, People and Settings tabs, a day sheet, and search across names, notes, phone and email.

### Widget

- A home-screen widget in large and 2 × 2 sizes, with today's events, a Wish button and what's coming up.
- While app lock is on, the widget shows only a count and the next date, nothing at all, or full details, as chosen in Settings → Widget.
- Widget settings for showing ages, including festivals and how many events to list. The large widget shows as many as fit its size.

### Updates

- In-app updates from GitHub releases: Settings → About Memora shows the version and checks for a new release once a week or on demand.
- See what's new, download with progress, and install through Android's own prompt. Skip a version or turn automatic checks off.
- An update is installed only when it matches the release's SHA-256 checksum and is signed with the same key as the installed app.

### Privacy and security

- All data stays on the phone. The app only goes online to fetch festival dates and check GitHub for updates.
- App lock with fingerprint, the phone's screen lock or a Memora PIN, which still works if the phone's own lock is removed. Wrong PINs make you wait longer each time.
- Locks turned on before the Memora PIN existed are asked to set one after unlocking.
- While locked, Memora is hidden from screenshots and Recents, and its notifications are marked private.
- Optional password-protected backups (AES-256-GCM, passwords of at least 6 characters). Backup files over 5 MB are refused.
- Links into the app only work from Memora's own notifications and widget.

### Look and feel

- A postcard design with paper cards, postage stamps and handwritten wishes.
- Light, dark or system theme; the status bar follows the theme you pick.
- A new stamp logo.
