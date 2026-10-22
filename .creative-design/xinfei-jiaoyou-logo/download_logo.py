# -*- coding: utf-8 -*-
"""下载心飞交友整套 Logo 到项目目录并核验 Alpha 通道"""
import os
import urllib.request
from PIL import Image

BASE = r"D:\MyCode\mytanhua\docs\logo"
os.makedirs(BASE, exist_ok=True)

# 文件名 -> (URL, 是否应为透明PNG)
FILES = [
    ("logo-primary.jpg", "https://aka.doubaocdn.com/s/5wtFHUsMnG", False),                # 竖版彩色组合 白底
    ("logo-icon.jpg", "https://aka.doubaocdn.com/s/1r9DGbWkEL", False),                  # 纯图标 白底
    ("logo-horizontal.jpg", "https://aka.doubaocdn.com/s/WVxjsNUVSs", False),            # 横版组合 白底
    ("logo-mono-black.jpg", "https://aka.doubaocdn.com/s/RRHeYrcGDO", False),            # 黑色单色版
    ("logo-reverse-white.jpg", "https://aka.doubaocdn.com/s/VkOa4HVhml", False),         # 白色反白版
    ("logo-icon-transparent.png",
     "https://mediakit-image.cn-beijing.volces.com/tos-cn-i-mcagdqv95q/veImageX-store/imagesegment/ad1aab36-570c-46ae-afe6-27c6bf3a5b30/992116f5546fa4c428b3d32c83755aceb58c362c908b244c29758bdc07552bf5.png~tplv-mcagdqv95q-image.image?sign=1792674405-rand-imagex-d40ad02f8eb7ad019ee1ec8ce0728cbd",
     True),
    ("logo-horizontal-transparent.png",
     "https://mediakit-image.cn-beijing.volces.com/tos-cn-i-mcagdqv95q/veImageX-store/imagesegment/3fca936d-b5b3-4f2e-a7d0-d4ee6a3cc42c/1dedd60d09cfc20119f3c681f144e7e709117d9b06a2a8025d1410355617b1a7.png~tplv-mcagdqv95q-image.image?sign=1792674431-rand-imagex-6d728015421827301704f6fb0fd509e8",
     True),
    ("logo-primary-transparent.png",
     "https://mediakit-image.cn-beijing.volces.com/tos-cn-i-mcagdqv95q/veImageX-store/imagesegment/f7a66a1b-1c32-4e2f-b385-504e6be6d497/195d124b4e793246a0d3dc8c114632a513ef99e8161082befc2edc2fd364298b.png~tplv-mcagdqv95q-image.image?sign=1792674439-rand-imagex-3827bab14b19f4a25f083049f5f503aa",
     True),
]

req_headers = {"User-Agent": "Mozilla/5.0"}
for name, url, expect_alpha in FILES:
    dst = os.path.join(BASE, name)
    req = urllib.request.Request(url, headers=req_headers)
    with urllib.request.urlopen(req, timeout=120) as r, open(dst, "wb") as f:
        f.write(r.read())
    size = os.path.getsize(dst)
    img = Image.open(dst)
    alpha_ok = ""
    if expect_alpha:
        if img.mode == "RGBA":
            a = img.getchannel("A")
            lo, hi = a.getextrema()
            transparent_px = sum(1 for _ in range(0))  # extrema is enough
            alpha_ok = f" | RGBA alpha范围[{lo},{hi}] {'透明通道OK' if lo < 255 else '警告:无透明像素'}"
        else:
            alpha_ok = f" | 警告:模式为{img.mode}，无Alpha通道"
    print(f"{name}: {img.size[0]}x{img.size[1]} {img.mode} {size}字节{alpha_ok}")

print("全部下载完成:", BASE)
