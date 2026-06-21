#!/usr/bin/env python3
"""Hush — Play Store hi-res icon (512x512) + feature graphic (1024x500) üret."""
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from pathlib import Path
import math

OUT = Path(__file__).parent
VIOLET = (124, 58, 237, 255)
BLUE   = (37, 99, 235, 255)
CYAN   = (34, 211, 238, 255)
DEEP   = (13, 11, 26, 255)
WHITE  = (255, 255, 255, 255)


def vertical_gradient(size, top, bottom):
    w, h = size
    img = Image.new("RGBA", size, top)
    base = Image.new("RGBA", (1, h))
    for y in range(h):
        t = y / max(h - 1, 1)
        r = int(top[0] + (bottom[0] - top[0]) * t)
        g = int(top[1] + (bottom[1] - top[1]) * t)
        b = int(top[2] + (bottom[2] - top[2]) * t)
        a = int(top[3] + (bottom[3] - top[3]) * t)
        base.putpixel((0, y), (r, g, b, a))
    return base.resize(size)


def radial_blob(size, center, radius, color):
    """Soft glowing blob at center."""
    w, h = size
    cx, cy = center
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([cx - radius, cy - radius, cx + radius, cy + radius], fill=color)
    return img.filter(ImageFilter.GaussianBlur(radius // 3))


def draw_chat_bubble_lock(img, cx, cy, scale=1.0):
    """Chat bubble with a small lock badge — Hush logo."""
    d = ImageDraw.Draw(img)
    # Chat bubble (rounded rect)
    bw = int(260 * scale)
    bh = int(200 * scale)
    bx0 = cx - bw // 2
    by0 = cy - bh // 2 - int(18 * scale)
    bx1 = bx0 + bw
    by1 = by0 + bh
    r = int(48 * scale)
    d.rounded_rectangle([bx0, by0, bx1, by1], radius=r, fill=WHITE)
    # Tail
    tail_w = int(36 * scale)
    tail_h = int(30 * scale)
    tx = bx0 + int(48 * scale)
    ty = by1
    d.polygon([
        (tx, ty - int(6 * scale)),
        (tx + tail_w, ty - int(6 * scale)),
        (tx + int(8 * scale), ty + tail_h)
    ], fill=WHITE)
    # Lock badge over bubble
    lw = int(80 * scale)
    lh = int(70 * scale)
    lx = cx - lw // 2
    ly = cy - lh // 2 + int(8 * scale)
    d.rounded_rectangle([lx, ly, lx + lw, ly + lh], radius=int(14 * scale), fill=VIOLET)
    # Shackle (arch)
    sw = int(48 * scale)
    sh = int(40 * scale)
    sx = cx - sw // 2
    sy = ly - sh + int(8 * scale)
    d.arc([sx, sy, sx + sw, sy + sh], start=180, end=360, fill=VIOLET, width=int(10 * scale))
    # Keyhole
    kh_r = int(8 * scale)
    kx = cx
    ky = ly + lh // 2 - int(6 * scale)
    d.ellipse([kx - kh_r, ky - kh_r, kx + kh_r, ky + kh_r], fill=WHITE)
    d.rectangle([kx - int(3 * scale), ky, kx + int(3 * scale), ky + int(16 * scale)], fill=WHITE)


def make_hi_res_icon(path: Path):
    size = (512, 512)
    bg = vertical_gradient(size, (90, 30, 180, 255), (37, 99, 235, 255))
    # Add glow blob top-right
    blob1 = radial_blob(size, (380, 130), 180, (124, 58, 237, 180))
    blob2 = radial_blob(size, (110, 410), 200, (34, 211, 238, 130))
    bg = Image.alpha_composite(bg, blob1)
    bg = Image.alpha_composite(bg, blob2)
    # Slight vignette
    vignette = Image.new("RGBA", size, (0, 0, 0, 0))
    vd = ImageDraw.Draw(vignette)
    vd.rectangle([0, 0, size[0], size[1]], fill=(0, 0, 0, 0))
    # Logo
    draw_chat_bubble_lock(bg, 256, 256, scale=0.95)
    bg.save(path, "PNG")
    print(f"✓ icon: {path}")


def make_feature_graphic(path: Path):
    size = (1024, 500)
    bg = vertical_gradient(size, (78, 22, 168, 255), (24, 31, 142, 255))
    # Glows
    bg = Image.alpha_composite(bg, radial_blob(size, (180, 100), 200, (124, 58, 237, 200)))
    bg = Image.alpha_composite(bg, radial_blob(size, (900, 380), 240, (34, 211, 238, 160)))
    bg = Image.alpha_composite(bg, radial_blob(size, (520, 250), 280, (37, 99, 235, 100)))
    d = ImageDraw.Draw(bg)
    # Logo on the right
    draw_chat_bubble_lock(bg, 850, 250, scale=0.85)
    # Wordmark
    try:
        font_title = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 130)
        font_sub = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 38)
    except Exception:
        font_title = ImageFont.load_default()
        font_sub = ImageFont.load_default()
    d.text((80, 150), "Hush", fill=WHITE, font=font_title)
    d.text((85, 305), "Your server. Your privacy.", fill=(220, 220, 255, 255), font=font_sub)
    bg.save(path, "PNG")
    print(f"✓ feature: {path}")


if __name__ == "__main__":
    make_hi_res_icon(OUT / "hi_res_icon_512.png")
    make_feature_graphic(OUT / "feature_graphic_1024x500.png")
    print("Tamamlandı.")
