#!/usr/bin/env python3.11
"""应用图标预览。

`ic_launcher_foreground.xml` / `ic_launcher_background.xml` 是矢量，
这里按同样的坐标用 PIL 复现一遍，输出：

* `preview/app-icon.png`    —— 432px（手表上图标约 48~60dp，这里放大看细节）
* `preview/app-icon-48.png` —— 48px，验证"缩小之后还认不认得出"

两版都套圆形遮罩：Galaxy Watch 的应用列表是圆形图标，方图预览会高估
四角的可用空间。坐标与 XML 里的 108 viewport 一一对应。
"""
import os

from PIL import Image, ImageDraw, ImageFont

FONT_B = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"

INK900 = (27, 23, 19)
INK800 = (34, 28, 23)
INK700 = (42, 36, 30)
PAPER = (232, 225, 209)
LINE = (185, 174, 151)
INK = (36, 30, 24)
RED = (201, 96, 79)
AMBER = (224, 163, 92)

V = 108.0  # viewport


def draw(size: int) -> Image.Image:
    """按 108 viewport 画一张 [size]×[size] 的图。"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    k = size / V

    def s(v):
        return v * k

    # 背景：三层同心圆（近似径向渐变）
    d.rectangle([0, 0, size, size], fill=INK900)
    d.ellipse([s(8), s(8), s(100), s(100)], fill=INK800)
    d.ellipse([s(24), s(24), s(84), s(84)], fill=INK700)

    # 牌身
    d.rounded_rectangle([s(32), s(23), s(76), s(89)], radius=s(4), fill=PAPER,
                        outline=INK, width=max(1, int(s(1.6))))
    # 内框
    d.rounded_rectangle([s(35.5), s(28), s(72.5), s(80)], radius=s(2),
                        outline=LINE, width=max(1, int(s(0.8))))

    def cubic(p0, p1, p2, p3, n=28):
        out = []
        for i in range(n + 1):
            t = i / n
            u = 1 - t
            x = u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0]
            y = u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1]
            out.append((s(x), s(y)))
        return out

    # 帽尖球
    d.ellipse([s(51.1), s(32.1), s(56.9), s(37.9)], fill=RED)
    # 帽身：弯尖（与 XML 的三次曲线同参）
    d.polygon([(s(54), s(37.8))] + cubic((54, 37.8), (58.5, 44), (67.5, 50.5), (68.5, 60))
              + [(s(42), s(60))], fill=INK)
    # 帽檐
    d.rounded_rectangle([s(39), s(60), s(69), s(63.4)], radius=s(1.7), fill=INK)
    # 分隔线
    d.rectangle([s(47), s(72), s(61), s(73.2)], fill=LINE)

    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    img.putalpha(mask)
    return img


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, "preview")
    os.makedirs(out, exist_ok=True)
    draw(432).save(os.path.join(out, "app-icon.png"))
    draw(48).save(os.path.join(out, "app-icon-48.png"))
    print("wrote preview/app-icon.png, preview/app-icon-48.png")


if __name__ == "__main__":
    main()
