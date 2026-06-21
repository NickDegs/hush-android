#!/usr/bin/env python3
"""Hush Android — Google Play ekran görüntüsü üreteci.
iOS gen_screens.py'deki LANGS sözlüğünü ve UI çizim fonksiyonlarını yeniden
kullanır; rsvg-convert ile Android boyutlarına render eder.

Boyutlar:
  - phone     : 1080x1920  (16:9 portrait)
  - tablet7   : 1200x1920  (Play 7" tablet)
  - tablet10  : 1600x2560  (Play 10" tablet)
"""
import os, sys, subprocess

IOS = "/root/dev5/nickchat-ios/appstore"
OUT = os.path.dirname(os.path.abspath(__file__))
SCREENS_OUT = os.path.join(OUT, "screens")

sys.path.insert(0, IOS)
# iOS gen_screens'i import et: LANGS, build(), SCREENS listesi
import gen_screens as G

ANDROID_SIZES = [
    ("phone",    1080, 1920),
    ("tablet7",  1200, 1920),
    ("tablet10", 1600, 2560),
]

# Hangi UI ekranları kullanılacak (iOS'taki sırayla)
# G.SCREENS yapısı: [(shift, uifn), ...]
def screens_list():
    if hasattr(G, "SCREENS"):
        return G.SCREENS
    # Fallback: gen_screens.py'de tanımlıysa
    return [
        (0.10, G.ui_chatlist),
        (0.18, G.ui_chat),
        (0.05, G.ui_login),
        (0.22, G.ui_groups),
        (0.00, G.ui_security),
    ]


def main():
    SCREENS = screens_list()
    os.makedirs(SCREENS_OUT, exist_ok=True)
    tmp_svg = os.path.join(OUT, "_tmp.svg")
    total = 0
    for lang, t in G.LANGS.items():
        # dil kodunu Android Play formatına çevir (en-US → en-US, tr → tr, zh-Hans → zh-CN, vb.)
        play_lang = {
            "en-US": "en-US", "tr": "tr-TR", "es-ES": "es-ES", "de-DE": "de-DE",
            "fr-FR": "fr-FR", "pt-BR": "pt-BR", "it": "it-IT", "ru": "ru-RU",
            "ja": "ja-JP", "zh-Hans": "zh-CN", "ko": "ko-KR", "ar-SA": "ar",
        }.get(lang, lang)
        for size_name, sw, sh in ANDROID_SIZES:
            d = os.path.join(SCREENS_OUT, play_lang, size_name)
            os.makedirs(d, exist_ok=True)
            for idx, (shift, uifn) in enumerate(SCREENS, 1):
                svg = G.build(t, idx - 1, shift, uifn)
                with open(tmp_svg, "w") as fh:
                    fh.write(svg)
                op = os.path.join(d, f"{idx:02d}.png")
                subprocess.run(
                    ["rsvg-convert", "-w", str(sw), "-h", str(sh), tmp_svg, "-o", op],
                    check=True,
                )
                total += 1
        print(f"✓ {play_lang}")
    if os.path.exists(tmp_svg):
        os.remove(tmp_svg)
    print(f"\nToplam: {len(G.LANGS)} dil × {len(ANDROID_SIZES)} boyut × {len(SCREENS)} ekran = {total} PNG")


if __name__ == "__main__":
    main()
