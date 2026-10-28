# Demo data for screenshots

Made-up people, notes and sent cards, so README and store screenshots never show real contacts.

## 1. Make the demo backup

```bash
python docs/demo/make_demo_backup.py              # dates relative to today
python docs/demo/make_demo_backup.py 2026-10-09   # or to the day you'll take the shots
```

This writes `docs/demo/memora-demo-backup.json` (not committed, since its dates go stale). Make it on the day you take the screenshots, so "today", "missed yesterday" and "in 3 days" line up.

What's in it:

| When | Who | Shows off |
|---|---|---|
| Today | Ananya Rao, birthday (turns 27) | Today card, Wish button, notes, gift log, 3 years of sent cards |
| Today | Rohan Kulkarni, farewell | "Sent" stamp, other event type |
| Yesterday | Divya Shetty, birthday | Missed list, belated card |
| 2 days ago | Vikram & Sneha, anniversary | Missed list |
| In 3 days | Raghav & Meera, 10th anniversary | Milestone, gift idea in the early reminder |
| In 8 days | Karthik Nair, housewarming | Other event with email |
| In 14 days | Priya Nair, turns 30 | Milestone |
| In about 20 days | Amma, birthday on the lunar date | Hindu lunar date, "always send on WhatsApp" |
| Later | Harish, Suresh & Lakshmi (25th), Kavya (no year), Mohan | Calendar dots, People tab |

Settings: reminders at 9:00 AM, India + Hindu holiday calendars with Diwali, Dussehra, Christmas and others switched on, one-week festival reminders, cards signed "SM".

Every phone number is `+91 00000 0xxxx`, which India never assigns (no Indian number starts with 0 after +91), and every email is `@example.com`. Before committing screenshots, still cover any number with `+91 ••••• •••••`.

## 2. Load it

**Restoring replaces everything in Memora.** Use an emulator or a spare phone, or back up your own data first (Settings → Back up) and restore it afterwards.

```bash
adb push docs/demo/memora-demo-backup.json /sdcard/Download/
```

Then Memora → Settings → Restore → pick the file. Turn the app lock **off**, or names are hidden on the widget and the app is blocked from screenshots.

## 3. Clean status bar (optional)

Android's demo mode shows a full battery, full signal and 9:41 instead of your real notifications:

```bash
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile show -e level 4 -e datatype none
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
# when done
adb shell am broadcast -a com.android.systemui.demo -e command exit
```

## 4. Shots worth taking

1. **Home**: Ananya's today card, the week strip, the festival banner, "In the post soon".
2. **Calendar**: the month with dots, today selected.
3. **Person page**: Ananya, scrolled to show notes, gift ideas and "Cards sent over the years".
4. **How to send** sheet, then the **WhatsApp postcard** screen with the Marigold design.
5. **Add event** with the lunar-date switch on.
6. **Festivals** with the calendars card and switches.
7. **Widget** on the home screen, in light and dark.
8. **App lock**: skip this one, or use the mockup. While the lock is on, Android blocks every capture of Memora (including `adb screencap`), which is the point of it.

Take each in light and dark (Settings → Mode), and in both looks if you want them (Settings → Theme: Postcard or Departures). Save them as PNGs in `docs/images/`, for example `home-light.png` or `home-board-dark.png`, and list them in the README where the screenshots comment is.
