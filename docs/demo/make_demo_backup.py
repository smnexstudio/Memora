#!/usr/bin/env python3
"""
Writes memora-demo-backup.json: made-up people for README screenshots.

Dates are worked out from the day you run it, so the screenshots always show
someone's birthday today, a missed one yesterday and more coming up.

    python docs/demo/make_demo_backup.py            # dates relative to today
    python docs/demo/make_demo_backup.py 2026-10-09 # or to a chosen day

Then copy the file to the phone and use Memora → Settings → Restore.
Restoring REPLACES everything in Memora, so use a test phone or emulator,
or back up your own data first.

Every name, number and email here is invented. Phone numbers start with 0
after +91 (+91 00000 0xxxx), which India never assigns, and emails use
example.com, so none of them can reach anyone. Screenshots still show numbers
masked as +91 ••••• •••••.
"""
import json
import sys
from datetime import date, datetime, time, timedelta
from pathlib import Path

today = date.fromisoformat(sys.argv[1]) if len(sys.argv) > 1 else date.today()


def on(offset_days: int, born: int | None):
    """Month, day and year for an event whose next date is `offset_days` from today."""
    d = today + timedelta(days=offset_days)
    if d.month == 2 and d.day == 29:  # keep it valid in every year
        d = d - timedelta(days=1)
    return d.month, d.day, born


def millis(d: date, hour=9, minute=4):
    return int(datetime.combine(d, time(hour, minute)).timestamp() * 1000)


Y = today.year
events, history, notes = [], [], []


def event(id, type, name, when, message, phone=None, email=None, title=None, offsets=(7, 1),
          about=None, last_sent=None, lunar=False, send_via=None):
    m, d, y = when
    events.append({
        "id": id, "type": type, "name": name, "title": title,
        "month": m, "day": d, "year": y,
        "phone": phone, "email": email, "message": message,
        "offsets": json.dumps(list(offsets)), "notes": about,
        "contact_id": None, "last_sent_year": last_sent,
        "lunar": 1 if lunar else 0, "send_via": send_via,
    })


def sent(event_id, year, channel, message, when: date):
    history.append({
        "id": len(history) + 1, "event_id": event_id, "year": year,
        "channel": channel, "message": message, "sent_at": millis(when),
    })


def note(event_id, kind, text, year=None):
    notes.append({
        "id": len(notes) + 1, "event_id": event_id, "kind": kind, "text": text,
        "year": year, "created_at": millis(today - timedelta(days=30 - len(notes))),
    })


# ---- Today ----
event(1, "birthday", "Ananya Rao", on(0, 1999),
      "Happy {ordinal} birthday, {first}! Wishing you a wonderful year ahead. Filter coffee is on me next time.",
      phone="+91 00000 01001", email="ananya.rao@example.com", offsets=(7, 1),
      about="College friend from Bangalore. Loves filter coffee and Carnatic music.",
      last_sent=Y - 1, send_via=None)
note(1, "idea", "Noise-cancelling earphones")
note(1, "idea", "Concert tickets for the December season")
note(1, "gift", "Hand-painted coffee mug", Y - 1)
note(1, "gift", "Book: The Sea of Poppies", Y - 2)
sent(1, Y - 1, "whatsapp", "Happy 26th birthday, Ananya! Have the best year yet.", today - timedelta(days=365))
sent(1, Y - 2, "sms", "Happy birthday Ananya! Sorry for the late wish.", today - timedelta(days=729))
sent(1, Y - 3, "skipped", None, today - timedelta(days=1095))

event(2, "other", "Rohan Kulkarni", on(0, None), "All the best for the new journey, {first}! We'll miss you.",
      title="Farewell", phone="+91 00000 01002", offsets=(1,), last_sent=Y)
sent(2, Y, "whatsapp", "All the best for the new journey, Rohan! We'll miss you.", today)

# ---- Missed ----
event(3, "birthday", "Divya Shetty", on(-1, 2002), "Happy birthday {first}! Have a lovely day.",
      phone="+91 00000 01003", about="Cousin of Priya Nair. Into trekking.", offsets=(3,))
event(4, "anniversary", "Vikram & Sneha", on(-2, Y - 3), "Happy {ordinal} anniversary, Vikram and Sneha!",
      phone="+91 00000 01004", offsets=(3,))

# ---- Coming up ----
event(5, "anniversary", "Raghav & Meera", on(3, Y - 10), "Happy {ordinal} anniversary to my favourite couple!",
      phone="+91 00000 01005", email="raghav.meera@example.com", offsets=(7, 1))
note(5, "idea", "Brass diya set")
event(6, "other", "Karthik Nair", on(8, None), "Congratulations on the new home, {first}! Wishing you many happy years in it.",
      title="Housewarming", phone="+91 00000 01006", email="karthik.nair@example.com", offsets=(3,))
event(7, "birthday", "Priya Nair", on(14, Y - 30), "Happy {ordinal} birthday, {first}! Here's to a big year.",
      phone="+91 00000 01007", email="priya.nair@example.com", offsets=(7, 1),
      about="Works in Bengaluru. Big fan of filter coffee and long drives.")
note(7, "idea", "Weekend trip voucher")
event(8, "birthday", "Amma", on(20, 1962), "Happy birthday Amma! Love you always.",
      phone="+91 00000 01008", offsets=(7, 1), lunar=True, send_via="whatsapp",
      about="Prefers a call in the morning before temple.")
note(8, "gift", "Silk saree", Y - 1)
event(9, "birthday", "Harish Gowda", on(36, 1994), "Happy birthday {first}!", phone="+91 00000 01009", offsets=(1,))
event(10, "anniversary", "Suresh & Lakshmi Nair", on(67, Y - 25), "Happy {ordinal} anniversary! Silver jubilee wishes to you both.",
      phone="+91 00000 01010", offsets=(7, 1))
event(11, "birthday", "Kavya M", on(85, None), "Happy birthday {first}! Have a great one.", phone="+91 00000 01011", offsets=(1,))
event(12, "birthday", "Mohan Kumar", on(172, 1988), "Many happy returns of the day, {first}!", phone="+91 00000 01012", offsets=(3,))

settings = [
    {"key": "reminderHour", "value": "9"},
    {"key": "reminderMinute", "value": "0"},
    {"key": "defaultOffsets", "value": "[7,1]"},
    {"key": "weeklyDigest", "value": "1"},
    {"key": "calendars", "value": "IN,HINDU"},
    # Festival ids come from Google's names; ones not in the calendar are simply ignored.
    {"key": "festivals", "value": json.dumps(["diwali-deepavali", "dussehra", "christmas", "makar-sankranti", "holi", "ugadi", "ganesh-chaturthi"])},
    {"key": "festivalEarly", "value": "1"},
    {"key": "signature", "value": "SM"},
]

backup = {
    "app": "memora", "version": 1, "exportedAt": datetime.now().astimezone().isoformat(),
    "events": events, "history": history, "personNotes": notes, "settings": settings,
}
out = Path(__file__).with_name("memora-demo-backup.json")
out.write_text(json.dumps(backup, indent=2, ensure_ascii=False), encoding="utf-8")
print(f"Wrote {out} ({len(events)} people, {len(history)} sent cards, {len(notes)} notes), dates relative to {today}")
