#!/usr/bin/env python3
"""Draws the 1024 x 500 Play Store feature graphic from the app mark and two Play screenshots.
Run from the repository root:  python3 store/tools/generate_feature_graphic.py
Needs store/graphics/screenshots/en/ (see PlayScreenshots.kt). Fonts: macOS Helvetica Neue."""
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H, SS = 1024, 500, 2
BG = (10, 13, 15)
GREEN = (38, 242, 115)
FONT = "/System/Library/Fonts/HelveticaNeue.ttc"


def font(size, bold):
    return ImageFont.truetype(FONT, size * SS, index=1 if bold else 0)


img = Image.new("RGB", (W * SS, H * SS), BG)

# Soft green glow behind the phones.
glow = Image.new("RGB", img.size, BG)
gd = ImageDraw.Draw(glow)
cx, cy, r = 760 * SS, 260 * SS, 330 * SS
gd.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(14, 52, 32))
glow = glow.filter(ImageFilter.GaussianBlur(120 * SS // 2))
img = Image.blend(img, glow, 0.9)


def phone(path, width, rotate):
    shot = Image.open(path).convert("RGB")
    h = int(shot.height * width / shot.width)
    shot = shot.resize((width, h), Image.LANCZOS)
    pad = 6 * SS
    frame = Image.new("RGBA", (width + 2 * pad, h + 2 * pad), (0, 0, 0, 0))
    mask = Image.new("L", frame.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, frame.width - 1, frame.height - 1], radius=34 * SS, fill=255)
    body = Image.new("RGBA", frame.size, (34, 40, 44, 255))
    frame.paste(body, (0, 0), mask)
    inner = Image.new("L", shot.size, 0)
    ImageDraw.Draw(inner).rounded_rectangle([0, 0, shot.width - 1, shot.height - 1], radius=28 * SS, fill=255)
    frame.paste(shot, (pad, pad), inner)
    return frame.rotate(rotate, resample=Image.BICUBIC, expand=True)


shots = "store/graphics/screenshots/en"
back = phone(f"{shots}/3_history.png", 250 * SS, 8)
front = phone(f"{shots}/2_home_clocked_in.png", 270 * SS, -6)
img.paste(back, (690 * SS, 60 * SS), back)
img.paste(front, (520 * SS, 90 * SS), front)

# Mark, name and tagline on the left.
sys_path = "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
icon = Image.open(sys_path).convert("RGBA").resize((104 * SS, 104 * SS), Image.LANCZOS)
img.paste(icon, (56 * SS, 88 * SS), icon)
d = ImageDraw.Draw(img)
d.text((56 * SS, 214 * SS), "HoursTracker", font=font(58, True), fill=(242, 244, 245))
d.text((58 * SS, 292 * SS), "Know what you earn,", font=font(30, False), fill=GREEN)
d.text((58 * SS, 332 * SS), "minute by minute.", font=font(30, False), fill=GREEN)
d.text((58 * SS, 410 * SS), "Shifts, overtime and take-home pay", font=font(19, False), fill=(163, 171, 178))
d.text((58 * SS, 436 * SS), "for hourly workers in Israel", font=font(19, False), fill=(163, 171, 178))

img = img.resize((W, H), Image.LANCZOS)
img.save("store/graphics/feature-graphic-1024x500.png", optimize=True)
print(img.size)
