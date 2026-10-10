#!/usr/bin/env python3
"""Draws docs/images/social-preview.png (1280 x 640) for GitHub's social preview.

    python docs/demo/make_social_preview.py

Uses the app's fonts and the screenshots in docs/images/.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
FONTS = ROOT / "app/src/main/res/font"
IMAGES = ROOT / "docs/images"

W, H = 1280, 640
S = 2  # draw at 2x, then scale down for smooth edges
GROUND = (230, 223, 210)
PAPER = (255, 253, 248)
NAVY = (35, 57, 91)
RED = (178, 58, 46)
MUTED = (74, 91, 120)
GOLD = (154, 106, 0)


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size * S)


img = Image.new("RGB", (W * S, H * S), GROUND)
d = ImageDraw.Draw(img)

# Airmail stripe along the top edge.
x, u = 0, 14 * S
while x < W * S:
    d.rectangle([x, 0, x + 2 * u, 10 * S], fill=RED)
    d.rectangle([x + 3 * u, 0, x + 5 * u, 10 * S], fill=NAVY)
    x += 6 * u


def paste_card(src: Image.Image, center, width, angle):
    """A screenshot as a tilted paper card with a soft shadow."""
    w = width * S
    h = int(src.height * w / src.width)
    shot = src.convert("RGB").resize((w, h), Image.LANCZOS)
    pad = 10 * S
    card = Image.new("RGBA", (w + 2 * pad, h + 2 * pad), PAPER + (255,))
    card.paste(shot, (pad, pad))
    shadow = Image.new("RGBA", (card.width + 80 * S, card.height + 80 * S), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rectangle([40 * S, 52 * S, 40 * S + card.width, 52 * S + card.height], fill=(35, 57, 91, 90))
    shadow = shadow.filter(ImageFilter.GaussianBlur(18 * S))
    shadow = shadow.rotate(angle, resample=Image.BICUBIC, expand=True)
    card = card.rotate(angle, resample=Image.BICUBIC, expand=True)
    cx, cy = center[0] * S, center[1] * S
    img.paste(shadow, (cx - shadow.width // 2, cy - shadow.height // 2), shadow)
    img.paste(card, (cx - card.width // 2, cy - card.height // 2), card)


# Two real screenshots on the right, cropped to their top so they read at this size.
def top(name, frac):
    im = Image.open(IMAGES / name)
    return im.crop((0, 0, im.width, int(im.height * frac)))

paste_card(top("screenshot-postcard.png", 0.72), (1095, 345), 250, -5)
paste_card(top("screenshot-home.png", 0.70), (880, 360), 270, 3)

# Left: logo, name, pitch.
logo = Image.open(IMAGES / "memora-logo.png").convert("RGBA").resize((104 * S, 104 * S), Image.LANCZOS)
img.paste(logo, (72 * S, 92 * S), logo)

d.text((72 * S, 210 * S), "Memora", font=font("fraunces_extrabold_italic.ttf", 92), fill=NAVY)
d.text((76 * S, 322 * S), "Never miss a birthday,", font=font("work_sans_semibold.ttf", 31), fill=NAVY)
d.text((76 * S, 362 * S), "anniversary or festival.", font=font("work_sans_semibold.ttf", 31), fill=NAVY)
d.text((76 * S, 418 * S), "Wishes and postcards ready for WhatsApp, SMS & email.",
       font=font("work_sans_regular.ttf", 19), fill=MUTED)

# Small stamp-style tags.
x = 76 * S
y = 470 * S
for label in ["Free & open source", "Offline", "No ads", "Android"]:
    f = font("work_sans_bold.ttf", 16)
    tw = d.textlength(label, font=f)
    box = [x, y, x + tw + 28 * S, y + 38 * S]
    # dashed border
    dash, gap = 7 * S, 5 * S
    for (ax, ay, bx, by) in [(box[0], box[1], box[2], box[1]), (box[0], box[3], box[2], box[3]),
                             (box[0], box[1], box[0], box[3]), (box[2], box[1], box[2], box[3])]:
        length = max(bx - ax, by - ay)
        p = 0
        while p < length:
            e = min(p + dash, length)
            if ax == bx:
                d.line([(ax, ay + p), (ax, ay + e)], fill=GOLD, width=2 * S)
            else:
                d.line([(ax + p, ay), (ax + e, ay)], fill=GOLD, width=2 * S)
            p = e + gap
    d.text((x + 14 * S, y + 8 * S), label, font=f, fill=(74, 51, 0))
    x = box[2] + 12 * S

d.text((76 * S, 548 * S), "github.com/smnexstudio/Memora", font=font("work_sans_medium.ttf", 18), fill=MUTED)

out = IMAGES / "social-preview.png"
img.resize((W, H), Image.LANCZOS).save(out, optimize=True)
print(f"Wrote {out} ({out.stat().st_size // 1024} KB)")
