"""Render the Battery screen artwork to transparent PNGs.

Original artwork drawn here as SVG and rasterised with headless Chromium
(Playwright) with a transparent background. Output goes to
app/src/main/assets/www/img/battery/ at 512 px (1x) and 1024 px (2x).

Layers used by the live graphic (bars are drawn live in CSS on top):
  (the dashed ring is inline SVG in index.html so it follows the theme accent;
   ring() below is only used for the static icon.png)
  body-<tone>.png  battery shell, cap and empty bar slots
  bolt.png         charging bolt
Plus a full static icon: icon.png (100%, charging, green).

Run: python3 tools/battery-icon/render.py
"""
import math, os
from playwright.sync_api import sync_playwright

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "../../app/src/main/assets/www/img/battery"))
TONES = {
    "green": ("#0f8a5f", "#4dffa6", "rgba(60,255,160,0.55)"),
    "amber": ("#c97a0a", "#ffd166", "rgba(255,190,70,0.55)"),
    "red":   ("#a51d2d", "#ff6b6b", "rgba(255,80,80,0.6)"),
}
BAR_H, BAR_GAP, BAR_BOTTOM = 13, 3.5, 117

def defs(lo, hi, glow):
    return f'''<defs>
<linearGradient id="ringg" gradientUnits="userSpaceOnUse" x1="10" y1="150" x2="150" y2="10"><stop offset="0" stop-color="{lo}"/><stop offset="1" stop-color="{hi}"/></linearGradient>
<linearGradient id="shellg" x1="0" y1="1" x2="0" y2="0"><stop offset="0" stop-color="{lo}"/><stop offset="1" stop-color="{hi}"/></linearGradient>
<linearGradient id="barg" gradientUnits="userSpaceOnUse" x1="0" y1="{BAR_BOTTOM}" x2="0" y2="37"><stop offset="0" stop-color="{lo}"/><stop offset="1" stop-color="{hi}"/></linearGradient>
<filter id="glow" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="1.6" result="b"/><feFlood flood-color="{glow}"/><feComposite in2="b" operator="in"/><feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge></filter>
</defs>'''

def ring():
    out = []
    for i in range(48):
        a = math.radians(i * 7.5)
        x1, y1 = 80 + 60 * math.sin(a), 80 - 60 * math.cos(a)
        x2, y2 = 80 + 70 * math.sin(a), 80 - 70 * math.cos(a)
        op = 0.35 + 0.65 * (0.5 + 0.5 * math.cos(a - math.pi * 1.75))
        out.append(f'<line x1="{x1:.2f}" y1="{y1:.2f}" x2="{x2:.2f}" y2="{y2:.2f}" opacity="{op:.2f}"/>')
    return '<g filter="url(#glow)" stroke="url(#ringg)" stroke-width="3.2" stroke-linecap="round">' + "".join(out) + "</g>"

def body(lo):
    slots = "".join(
        f'<rect x="62" y="{BAR_BOTTOM - i * (BAR_H + BAR_GAP) - BAR_H}" width="36" height="{BAR_H}" rx="2.5" fill="{lo}" fill-opacity="0.16" stroke="{lo}" stroke-opacity="0.35" stroke-width="0.6"/>'
        for i in range(5))
    return ('<g filter="url(#glow)"><rect x="71" y="29" width="18" height="6" rx="2" fill="url(#shellg)"/>'
            '<rect x="56" y="35" width="48" height="88" rx="8" fill="none" stroke="url(#shellg)" stroke-width="2.6"/></g>' + slots)

def bars():
    return "".join(f'<rect x="62" y="{BAR_BOTTOM - i * (BAR_H + BAR_GAP) - BAR_H}" width="36" height="{BAR_H}" rx="2.5" fill="url(#barg)"/>' for i in range(5))

BOLT = ('<path d="M83.5 60 71 82h8.2l-3.4 18L89 77h-8.4l2.9-17z" fill="#ffffff" stroke="#06331f" stroke-opacity="0.55" '
        'stroke-width="1" stroke-linejoin="round" filter="url(#glow)"/>')

def svg(inner, tone="green"):
    lo, hi, glow = TONES[tone]
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 160" width="512" height="512">{defs(lo, hi, glow)}{inner}</svg>'

def jobs():
    for t in TONES:
        yield f"body-{t}", svg(body(TONES[t][0]), t)
    yield "bolt", svg(BOLT, "green")
    yield "icon", svg(ring() + body(TONES["green"][0]) + bars() + BOLT, "green")

def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(os.path.join(HERE, "svg"), exist_ok=True)
    with sync_playwright() as p:
        b = p.chromium.launch()
        for scale, suffix in ((1, ""), (2, "@2x")):
            pg = b.new_page(viewport={"width": 512, "height": 512}, device_scale_factor=scale)
            for name, s in jobs():
                if scale == 1:
                    with open(os.path.join(HERE, "svg", name + ".svg"), "w") as f:
                        f.write(s)
                pg.set_content('<html><body style="margin:0;background:transparent">' + s + "</body></html>")
                pg.locator("svg").screenshot(path=os.path.join(OUT, name + suffix + ".png"), omit_background=True)
            pg.close()
        b.close()
    print("wrote", OUT)

if __name__ == "__main__":
    main()
