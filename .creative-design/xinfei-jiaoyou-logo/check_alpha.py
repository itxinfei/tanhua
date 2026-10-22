# -*- coding: utf-8 -*-
"""将透明 PNG 合成到白/黑/橙底上，检查抠图边缘质量"""
import os
from PIL import Image

BASE = r"D:\MyCode\mytanhua\docs\logo"
CHECK = os.path.join(BASE, "_alpha_check")
os.makedirs(CHECK, exist_ok=True)

BG = {"white": (255, 255, 255, 255), "black": (0, 0, 0, 255), "orange": (255, 110, 0, 255)}

for name in ["logo-icon-transparent.png", "logo-horizontal-transparent.png", "logo-primary-transparent.png"]:
    fg = Image.open(os.path.join(BASE, name)).convert("RGBA")
    # 缩到统一检查尺寸，3 列横排
    maxh = 700
    ratio = maxh / fg.height
    fg_r = fg.resize((int(fg.width * ratio), maxh), Image.LANCZOS)
    pad = 30
    canvas = Image.new("RGBA", (fg_r.width * 3 + pad * 4, fg_r.height + pad * 2), (120, 120, 120, 255))
    for i, (label, color) in enumerate(BG.items()):
        tile = Image.new("RGBA", (fg_r.width, fg_r.height), color)
        tile.alpha_composite(fg_r)
        canvas.alpha_composite(tile, (pad + i * (fg_r.width + pad), pad))
    out = os.path.join(CHECK, name.replace(".png", "_check.png"))
    canvas.convert("RGB").save(out, quality=92)
    print("saved", out)
print("done")
