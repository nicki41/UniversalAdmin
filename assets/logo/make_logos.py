"""Writes the UniversalAdmin logo family as SVG and renders 512px PNGs with headless Chrome.

Usage: python make_logos.py <out-dir>
"""
import pathlib
import subprocess
import sys

OUT = pathlib.Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"

# The core emblem: a shield with a "U" cut into it. Coordinates on a 512 grid.
SHIELD = "M256 84 L396 134 L396 258 C396 350 336 414 256 446 C176 414 116 350 116 258 L116 134 Z"
U_CUT = "M198 166 V266 A58 58 0 0 0 314 266 V166"

FAMILY = {
    # name: (gradient from, gradient to, glyph svg drawn in white on the tile)
    "universaladmin": ("#4338CA", "#7C3AED", None),
    "universaladmin-discord": ("#3730A3", "#2563EB", """
        <path d="M128 150 h256 a40 40 0 0 1 40 40 v122 a40 40 0 0 1 -40 40 h-138 l-74 58 v-58 h-44 a40 40 0 0 1 -40 -40 v-122 a40 40 0 0 1 40 -40 z" fill="#fff"/>
        <circle cx="196" cy="252" r="22" fill="{dark}"/><circle cx="256" cy="252" r="22" fill="{dark}"/><circle cx="316" cy="252" r="22" fill="{dark}"/>"""),
    "universaladmin-placeholders": ("#0F766E", "#14B8A6", """
        <text x="256" y="318" text-anchor="middle" font-family="Segoe UI, Arial, sans-serif" font-weight="800" font-size="190" fill="#fff">{%}</text>"""),
    "universaladmin-voicechat": ("#C2410C", "#F59E0B", """
        <rect x="214" y="120" width="84" height="170" rx="42" fill="#fff"/>
        <path d="M166 236 v18 a90 90 0 0 0 180 0 v-18" fill="none" stroke="#fff" stroke-width="26" stroke-linecap="round"/>
        <path d="M256 344 v52 M210 400 h92" fill="none" stroke="#fff" stroke-width="26" stroke-linecap="round"/>
        <path d="M120 190 a150 150 0 0 0 0 118 M392 190 a150 150 0 0 1 0 118" fill="none" stroke="#fff" stroke-opacity=".55" stroke-width="18" stroke-linecap="round"/>"""),
    "universaladmin-tab": ("#9D174D", "#EC4899", """
        <rect x="118" y="132" width="276" height="248" rx="30" fill="#fff"/>
        <rect x="146" y="162" width="46" height="46" rx="8" fill="{dark}"/><rect x="208" y="174" width="156" height="22" rx="11" fill="{dark}" fill-opacity=".55"/>
        <rect x="146" y="233" width="46" height="46" rx="8" fill="{dark}"/><rect x="208" y="245" width="126" height="22" rx="11" fill="{dark}" fill-opacity=".55"/>
        <rect x="146" y="304" width="46" height="46" rx="8" fill="{dark}"/><rect x="208" y="316" width="96" height="22" rx="11" fill="{dark}" fill-opacity=".55"/>"""),
    "universaladmin-proxy": ("#075985", "#06B6D4", """
        <path d="M256 168 L160 330 M256 168 L352 330 M160 330 L352 330 M256 168 L256 268 M160 330 L256 268 M352 330 L256 268" stroke="#fff" stroke-width="16" stroke-linecap="round" stroke-opacity=".8"/>
        <circle cx="256" cy="168" r="42" fill="#fff"/><circle cx="160" cy="330" r="42" fill="#fff"/>
        <circle cx="352" cy="330" r="42" fill="#fff"/><circle cx="256" cy="268" r="26" fill="#fff"/>"""),
}


def tile(name, light, dark, glyph):
    if glyph is None:
        body = f"""
        <path d="{SHIELD}" fill="#fff"/>
        <path d="{U_CUT}" fill="none" stroke="url(#bg)" stroke-width="46" stroke-linecap="round"/>"""
    else:
        # The family badge: a small core shield at the bottom right.
        body = glyph.replace("{dark}", dark) + f"""
        <g transform="translate(362 362) scale(0.25)">
          <circle cx="256" cy="264" r="230" fill="{dark}"/>
          <path d="{SHIELD}" fill="#fff"/>
          <path d="{U_CUT}" fill="none" stroke="{dark}" stroke-width="46" stroke-linecap="round"/>
        </g>"""
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="{light}"/>
      <stop offset="1" stop-color="{dark}"/>
    </linearGradient>
    <radialGradient id="shine" cx="0.3" cy="0.2" r="0.8">
      <stop offset="0" stop-color="#fff" stop-opacity=".22"/>
      <stop offset="1" stop-color="#fff" stop-opacity="0"/>
    </radialGradient>
  </defs>
  <rect width="512" height="512" rx="112" fill="url(#bg)"/>
  <rect width="512" height="512" rx="112" fill="url(#shine)"/>{body}
</svg>
"""


for name, (dark, light, glyph) in FAMILY.items():
    svg = OUT / (name + ".svg")
    svg.write_text(tile(name, light, dark, glyph), encoding="utf-8")
    png = OUT / (name + ".png")
    html = OUT / (name + ".html")
    html.write_text(f'<html><body style="margin:0;background:transparent"><img src="{svg.name}" width="512" height="512"></body></html>',
                    encoding="utf-8")
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--default-background-color=00000000",
                    "--window-size=512,512", "--screenshot=" + str(png), html.as_uri()],
                   capture_output=True, timeout=60)
    print(name, png.exists() and png.stat().st_size)

# One preview sheet with every logo, light and dark background.
cells = "".join(f'<div class="c"><img src="{n}.svg"><span>{n}</span></div>' for n in FAMILY)
sheet = OUT / "preview.html"
sheet.write_text("""<html><head><style>
body{margin:0;font-family:Segoe UI,Arial,sans-serif;display:flex;flex-direction:column}
.row{display:flex;gap:28px;padding:28px}
.light{background:#f4f4f5;color:#18181b}.dark{background:#18181b;color:#e4e4e7}
.c{display:flex;flex-direction:column;align-items:center;gap:10px;font-size:15px}
img{width:200px;height:200px}.small img{width:48px;height:48px}
</style></head><body>
<div class="row light">""" + cells + """</div>
<div class="row dark">""" + cells + """</div>
<div class="row dark small">""" + cells + """</div>
</body></html>""", encoding="utf-8")
subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--window-size=1270,760",
                "--screenshot=" + str(OUT / "preview.png"), sheet.as_uri()], capture_output=True, timeout=60)
print("preview", (OUT / "preview.png").exists())
