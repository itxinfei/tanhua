# -*- coding: utf-8 -*-
"""
心飞交友 App 底部 tabBar 图标生成脚本（纯 PIL，无外部依赖）。

设计说明：
- 业务图标（匹配/圈子/视频/我的）统一为圆润实心风格，普通态中性灰、选中态品牌粉；
- 发布按钮为品牌渐变圆角方形（紫 -> 粉）+ 白色加粗加号 + 顶部高光 + 柔和投影；
- 4 倍超采样绘制后 LANCZOS 下采样，保证小尺寸下边缘平滑。

运行：python scripts/gen-tabbar-icons.py
输出：src/static/tabbar/*.png（原始图标已备份至 src/static/tabbar-origin-backup）
"""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageMath

# ---- 基础参数 ----
SS = 4                      # 超采样倍数
GRID = 96                   # 逻辑网格
CANVAS = GRID * SS          # 384 实际绘制画布
OUT = 192                   # 输出像素（实际显示 24~44px，高分屏清晰）

# ---- 品牌色 ----
INACTIVE = (154, 163, 175)          # #9aa3af 中性灰
ACTIVE = (255, 94, 156)             # #ff5e9c 品牌粉
PURPLE = (177, 59, 255)             # #b13bff 品牌深紫
WHITE = (255, 255, 255)

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "static", "tabbar")


def new_mask():
    """新建一张黑色（全透明）alpha 蒙版。"""
    return Image.new("L", (CANVAS, CANVAS), 0)


def solid_from_mask(mask, color):
    """用 alpha 蒙版 + 纯色合成 RGBA 图。"""
    img = Image.new("RGBA", (CANVAS, CANVAS), color + (0,))
    img.putalpha(mask)
    return img


def finish(img):
    """超采样图下采样到输出尺寸。"""
    return img.resize((OUT, OUT), Image.LANCZOS)


# ---- 业务图标造型（均画在 alpha 蒙版上，255=不透明）----
def draw_heart():
    m = new_mask()
    d = ImageDraw.Draw(m)
    # 两个圆瓣
    d.ellipse((80, 80, 220, 220), fill=255)        # 左瓣 圆心(150,150) r70
    d.ellipse((164, 80, 304, 220), fill=255)       # 右瓣 圆心(234,150) r70
    # 底部汇聚三角
    d.polygon([(100, 178), (284, 178), (192, 318)], fill=255)
    return m


def draw_ring():
    m = new_mask()
    d = ImageDraw.Draw(m)
    d.ellipse((82, 82, 302, 302), fill=255)        # 外圆 r110
    d.ellipse((130, 130, 254, 254), fill=0)        # 内镂空 r62
    return m


def draw_play():
    m = new_mask()
    d = ImageDraw.Draw(m)
    pts = [(128, 104), (128, 280), (288, 192)]
    d.polygon(pts, fill=255)
    # 顶点小圆弧柔化，避免尖锐也不显骨感
    for (x, y) in pts:
        d.ellipse((x - 10, y - 10, x + 10, y + 10), fill=255)
    return m


def draw_user():
    m = new_mask()
    d = ImageDraw.Draw(m)
    d.ellipse((56, 175, 328, 420), fill=255)       # 肩/身体（先画，底部出画布裁掉）
    d.ellipse((136, 72, 248, 184), fill=255)       # 头 圆心(192,128) r56，叠在身体上方
    return m


ICONS = {
    "match": draw_heart,
    "circle": draw_ring,
    "video": draw_play,
    "mine": draw_user,
}


def build_business_icons():
    for name, fn in ICONS.items():
        mask = fn()
        for suffix, color in (("", INACTIVE), ("-active", ACTIVE)):
            img = finish(solid_from_mask(mask, color))
            img.save(os.path.join(OUT_DIR, f"{name}{suffix}.png"))


# ---- 发布按钮 ----
def linear_gradient(size, c0, c1):
    """生成 135°（左上->右下）线性渐变 RGB 图（纯 PIL，无需 numpy）。"""
    # 垂直 0..255 渐变，再转 90° 得到水平渐变
    v = Image.linear_gradient("L").resize((size, size))
    h = v.transpose(Image.Transpose.ROTATE_90)
    # 对角系数 t = (垂直 + 水平) / 2
    t = ImageMath.unsafe_eval("(v + h) / 2", v=v, h=h).convert("L")
    chans = []
    for i in range(3):
        a, b = c0[i], c1[i]
        ch = ImageMath.unsafe_eval("a + (b - a) * t / 255", a=a, b=b, t=t).convert("L")
        chans.append(ch)
    return Image.merge("RGB", chans)


def rounded_mask(box, radius):
    m = new_mask()
    ImageDraw.Draw(m).rounded_rectangle(box, radius=radius, fill=255)
    return m


def build_publish():
    box = (76, 64, 308, 296)            # 主体圆角方形
    radius = 54
    x0, y0, x1, y1 = box
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2

    # 1. 柔和投影
    shadow = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    sm = rounded_mask(box, radius)
    shadow.putalpha(sm)
    shadow_colored = Image.new("RGBA", (CANVAS, CANVAS), (96, 30, 140, 0))
    shadow_colored.putalpha(sm.point(lambda v: int(v * 0.55)))
    shadow_colored = shadow_colored.filter(ImageFilter.GaussianBlur(18))
    shadow_layer = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    shadow_layer.paste(shadow_colored, (0, 16), shadow_colored)

    # 2. 渐变主体（在按钮 box 尺寸内生成，紫->粉在按钮内充分展开，色彩更饱和）
    bw, bh = x1 - x0, y1 - y0
    g = linear_gradient(max(bw, bh), PURPLE, ACTIVE).resize((bw, bh))
    grad_full = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    grad_full.paste(g.convert("RGBA"), (x0, y0))
    body_mask = rounded_mask(box, radius)
    body = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    body.paste(grad_full, (0, 0), body_mask)

    # 3. 顶部高光（克制的白色椭圆，模糊后乘纵向渐隐，只在顶部轻微提亮）
    hl_mask = new_mask()
    hd = ImageDraw.Draw(hl_mask)
    hd.ellipse((x0 + 24, y0 + 10, x1 - 24, y0 + 130), fill=255)
    hl_mask = hl_mask.filter(ImageFilter.GaussianBlur(14))
    fade = Image.new("L", (CANVAS, CANVAS), 0)
    fd = ImageDraw.Draw(fade)
    for i, y in enumerate(range(y0, y0 + 140)):
        fd.line((0, y, CANVAS, y), fill=int(50 * (1 - i / 140)))
    hl_final = ImageMath.unsafe_eval("h * f / 255", h=hl_mask, f=fade).convert("L")
    highlight = Image.new("RGBA", (CANVAS, CANVAS), (255, 255, 255, 0))
    highlight.putalpha(hl_final)

    # 4. 白色加粗加号
    plus_mask = new_mask()
    pd = ImageDraw.Draw(plus_mask)
    pd.rounded_rectangle((cx - 16, cy - 68, cx + 16, cy + 68), radius=16, fill=255)
    pd.rounded_rectangle((cx - 68, cy - 16, cx + 68, cy + 16), radius=16, fill=255)
    plus = Image.new("RGBA", (CANVAS, CANVAS), WHITE + (0,))
    plus.putalpha(plus_mask)

    # 合成
    out = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    out = Image.alpha_composite(out, shadow_layer)
    out = Image.alpha_composite(out, body)
    out = Image.alpha_composite(out, highlight)
    out = Image.alpha_composite(out, plus)

    finish(out).save(os.path.join(OUT_DIR, "publish.png"))


if __name__ == "__main__":
    os.makedirs(OUT_DIR, exist_ok=True)
    build_business_icons()
    build_publish()
    print("tabBar icons generated at:", os.path.abspath(OUT_DIR))
