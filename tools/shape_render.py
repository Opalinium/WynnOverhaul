import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

SCALE = 4


class Gfx:
    def __init__(self, w, h, bg=(40, 52, 30)):
        self.px = np.zeros((h, w, 3), dtype=float)
        self.px[:] = bg
        self.w = w
        self.h = h

    def _blend(self, x0, y0, x1, y1, argb_top, argb_bottom=None):
        x0, y0 = max(x0, 0), max(y0, 0)
        x1, y1 = min(x1, self.w), min(y1, self.h)
        if x1 <= x0 or y1 <= y0:
            return
        n = y1 - y0
        for i in range(n):
            t = i / max(n - 1, 1) if argb_bottom is not None else 0.0
            c = lerp(argb_top, argb_bottom, t) if argb_bottom is not None else argb_top
            a = ((c >> 24) & 0xFF) / 255.0
            rgb = np.array([(c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF], dtype=float)
            self.px[y0 + i, x0:x1] = self.px[y0 + i, x0:x1] * (1 - a) + rgb * a

    def fill(self, x0, y0, x1, y1, color):
        self._blend(x0, y0, x1, y1, color & 0xFFFFFFFF)

    def fillGradient(self, x0, y0, x1, y1, top, bottom):
        self._blend(x0, y0, x1, y1, top & 0xFFFFFFFF, bottom & 0xFFFFFFFF)

    def outline(self, x, y, w, h, color):
        self.fill(x, y, x + w, y + 1, color)
        self.fill(x, y + h - 1, x + w, y + h, color)
        self.fill(x, y, x + 1, y + h, color)
        self.fill(x + w - 1, y, x + w, y + h, color)

    def image(self, text=None):
        arr = np.clip(self.px, 0, 255).astype(np.uint8)
        img = Image.fromarray(arr).resize((self.w * SCALE, self.h * SCALE), Image.NEAREST)
        if text:
            d = ImageDraw.Draw(img)
            for (tx, ty, s, col) in text:
                d.text((tx * SCALE, ty * SCALE), s, fill=col)
        return img


def lerp(a, b, t):
    t = min(max(t, 0.0), 1.0)
    out = 0
    for shift in (24, 16, 8, 0):
        ca = (a >> shift) & 0xFF
        cb = (b >> shift) & 0xFF
        out |= int(round(ca + (cb - ca) * t)) << shift
    return out


def alpha(argb, f):
    a = int(round(((argb >> 24) & 0xFF) * min(max(f, 0), 1)))
    return (argb & 0xFFFFFF) | (a << 24)


def mix(a, b, t):
    out = a & 0xFF000000
    for shift in (16, 8, 0):
        ca = (a >> shift) & 0xFF
        cb = (b >> shift) & 0xFF
        out |= int(round(ca + (cb - ca) * min(max(t, 0), 1))) << shift
    return out


def lighten(c, t):
    return mix(c, 0xFFFFFFFF, t)


def darken(c, t):
    return mix(c, 0xFF000000, t)


ACCENT = 0xFFD4AF37
ACCENT_DIM = 0xFF7A5F1E
BORDER_BRIGHT = 0xFFC9A227
HAIRLINE = 0xFF5A4A28
TILE_BG = 0xF0161412
TILE_BORDER = 0xFF3A2E1C
OUTLINE = 0xFF050403
WELL_EDGE = 0xFF2A2012
HIGHLIGHT = 0x26FFE9B0
SHADE = 0x5A000000
LIP = 0x12FFE9B0
PAGE_TOP = 0xF01D1710
PAGE_BOTTOM = 0xF00C0907
GLASS_TOP = 0xB81B150E
GLASS_BOTTOM = 0xCC0B0806


def clamp_cut(cut, w, h):
    return min(max(cut, 0), min(w, h) // 2)


def s_fill(g, x, y, w, h, cut, color):
    if w <= 0 or h <= 0 or (color >> 24) == 0:
        return
    c = clamp_cut(cut, w, h)
    if c == 0:
        g.fill(x, y, x + w, y + h, color)
        return
    for i in range(c):
        inset = c - i
        g.fill(x + inset, y + i, x + w - inset, y + i + 1, color)
        g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color)
    g.fill(x, y + c, x + w, y + h - c, color)


def s_gradient(g, x, y, w, h, cut, top, bottom):
    if w <= 0 or h <= 0:
        return
    c = clamp_cut(cut, w, h)
    for i in range(c):
        inset = c - i
        g.fill(x + inset, y + i, x + w - inset, y + i + 1, lerp(top, bottom, (i + 0.5) / h))
        g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, lerp(top, bottom, (h - 0.5 - i) / h))
    g.fillGradient(x, y + c, x + w, y + h - c, lerp(top, bottom, c / h), lerp(top, bottom, (h - c) / h))


def s_frame(g, x, y, w, h, cut, color):
    if w <= 0 or h <= 0 or (color >> 24) == 0:
        return
    c = clamp_cut(cut, w, h)
    if c == 0:
        g.outline(x, y, w, h, color)
        return
    g.fill(x + c, y, x + w - c, y + 1, color)
    g.fill(x + c, y + h - 1, x + w - c, y + h, color)
    g.fill(x, y + c, x + 1, y + h - c, color)
    g.fill(x + w - 1, y + c, x + w, y + h - c, color)
    for i in range(1, c):
        g.fill(x + c - i, y + i, x + c - i + 1, y + i + 1, color)
        g.fill(x + w - c + i - 1, y + i, x + w - c + i, y + i + 1, color)
        g.fill(x + c - i, y + h - 1 - i, x + c - i + 1, y + h - i, color)
        g.fill(x + w - c + i - 1, y + h - 1 - i, x + w - c + i, y + h - i, color)


def s_shadow(g, x, y, w, h, cut, depth=3, strength=1.0):
    if strength <= 0:
        return
    layer = max(1, min(int(0x15 * strength), 255)) << 24
    for k in range(depth, 0, -1):
        s_fill(g, x - k + 1, y + 2, w + 2 * (k - 1), h + k - 1, cut, layer)


def s_raised(g, x, y, w, h, fill, border, cut=2, hovered=False, pressed=False, glow=0, fade=1.0, shadow=True):
    if w <= 2 or h <= 2:
        return
    c = clamp_cut(cut, w, h)
    if shadow and not pressed:
        s_shadow(g, x, y, w, h, c, 2, fade)
    top = darken(fill, 0.3) if pressed else mix(fill, 0xFFFFD9A0, 0.2 if hovered else 0.1)
    bottom = lighten(fill, 0.04) if pressed else darken(fill, 0.32)
    s_gradient(g, x, y, w, h, c, alpha(top, fade), alpha(bottom, fade))
    if glow:
        g.fillGradient(x + 1, y + h // 2, x + w - 1, y + h - 1, alpha(glow, 0), alpha(glow, 0.3 * fade if hovered else 0.16 * fade))
    if pressed:
        g.fill(x + c, y + 1, x + w - c, y + 2, alpha(SHADE, fade))
        g.fill(x + 1, y + c, x + 2, y + h - c, alpha(0x30000000, fade))
    else:
        g.fill(x + c, y + 1, x + w - c, y + 2, alpha(HIGHLIGHT, fade))
        g.fill(x + c, y + h - 2, x + w - c, y + h - 1, alpha(SHADE, fade))
    s_frame(g, x, y, w, h, c, alpha(border, fade))


def s_well(g, x, y, w, h, cut=1, fill=TILE_BG, border=WELL_EDGE):
    if w <= 2 or h <= 2:
        return
    c = clamp_cut(cut, w, h)
    s_gradient(g, x, y, w, h, c, darken(fill, 0.3), fill)
    g.fillGradient(x + 1, y + 1, x + w - 1, y + min(h - 1, 5), 0x70000000, 0x00000000)
    g.fill(x + 1, y + c, x + 2, y + h - c, 0x2A000000)
    g.fill(x + c, y + h - 2, x + w - c, y + h - 1, LIP)
    s_frame(g, x, y, w, h, c, border)


def diamond(g, cx, cy, r, color):
    if r < 0:
        return
    for dy in range(-r, r + 1):
        half = r - abs(dy)
        g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color)


def ornaments(g, x, y, w, h, c, color):
    length = min(9, w // 3, h // 3)
    g.fill(x + c, y, x + c + length, y + 1, color)
    g.fill(x + w - c - length, y, x + w - c, y + 1, color)
    g.fill(x + c, y + h - 1, x + c + length, y + h, color)
    g.fill(x + w - c - length, y + h - 1, x + w - c, y + h, color)
    g.fill(x, y + c, x + 1, y + c + length, color)
    g.fill(x + w - 1, y + c, x + w, y + c + length, color)
    g.fill(x, y + h - c - length, x + 1, y + h - c, color)
    g.fill(x + w - 1, y + h - c - length, x + w, y + h - c, color)
    for i in range(1, c):
        g.fill(x + c - i, y + i, x + c - i + 1, y + i + 1, color)
        g.fill(x + w - c + i - 1, y + i, x + w - c + i, y + i + 1, color)
        g.fill(x + c - i, y + h - 1 - i, x + c - i + 1, y + h - i, color)
        g.fill(x + w - c + i - 1, y + h - 1 - i, x + w - c + i, y + h - i, color)
    if c >= 2:
        diamond(g, x + c + 1, y + c + 1, 1, color)
        diamond(g, x + w - c - 2, y + c + 1, 1, color)
        diamond(g, x + c + 1, y + h - c - 2, 1, color)
        diamond(g, x + w - c - 2, y + h - c - 2, 1, color)


def s_panel(g, x, y, w, h, glass=False, cut=4, accent=ACCENT_DIM, fade=1.0):
    c = clamp_cut(cut, w, h)
    s_shadow(g, x, y, w, h, c, 4, fade)
    top = GLASS_TOP if glass else PAGE_TOP
    bottom = GLASS_BOTTOM if glass else PAGE_BOTTOM
    s_gradient(g, x, y, w, h, c, alpha(top, fade), alpha(bottom, fade))
    g.fillGradient(x + 2, y + 3, x + w - 2, y + 3 + h // 4, alpha(0x12D4AF37, fade), 0x00D4AF37)
    g.fillGradient(x + 2, y + h - 2 - h // 6, x + w - 2, y + h - 2, 0x00000000, alpha(0x34000000, fade))
    s_frame(g, x + 1, y + 1, w - 2, h - 2, max(c - 1, 0), alpha(HAIRLINE, fade))
    g.fill(x + c + 1, y + 2, x + w - c - 1, y + 3, alpha(HIGHLIGHT, fade))
    s_frame(g, x, y, w, h, c, alpha(OUTLINE, fade))
    ornaments(g, x, y, w, h, c, alpha(accent, fade))


def fade_rule(g, x, y, w, color, left_solid=True):
    if w <= 0:
        return
    slices = min(max(w // 3, 1), 12)
    slice_w = w / slices
    for i in range(slices):
        t = (i + 0.5) / slices
        c = alpha(color, 1 - t * t if left_solid else t * t)
        g.fill(x + round(i * slice_w), y, x + round((i + 1) * slice_w), y + 1, c)


def groove(g, x, y, w):
    g.fill(x, y, x + w, y + 1, HAIRLINE)
    g.fill(x, y + 1, x + w, y + 2, SHADE)
    g.fill(x, y + 2, x + w, y + 3, LIP)


def title_band(g, x, y, w, title, out):
    h = 28
    g.fillGradient(x + 2, y + 3, x + w - 2, y + h - 3, 0x22D4AF37, 0x04D4AF37)
    groove(g, x + 2, y + h - 3, w - 4)
    label = title.upper()
    tw = len(label) * 6
    cx = x + w // 2
    ty = y + (h - 3 - 8) // 2
    out.append((cx - tw // 2, ty - 1, label, (237, 224, 200)))
    rule = min(max(w // 6, 16), 56)
    mid = ty + 4
    left_end = cx - tw // 2 - 9
    right_start = cx + tw // 2 + 9
    fade_rule(g, left_end - rule, mid, rule, ACCENT_DIM, False)
    fade_rule(g, right_start, mid, rule, ACCENT_DIM, True)
    diamond(g, left_end + 3, mid, 2, ACCENT)
    diamond(g, right_start - 4, mid, 2, ACCENT)


def thumb(g, x, y, w, h):
    body = ACCENT_DIM
    g.fillGradient(x, y, x + w, y + h, lighten(body, 0.2), darken(body, 0.35))
    g.fill(x, y, x + w, y + 1, HIGHLIGHT)
    g.outline(x - 1, y - 1, w + 2, h + 2, OUTLINE)


def checkbox(g, x, y, w, h, checked, hovered):
    border = BORDER_BRIGHT if hovered else (ACCENT_DIM if checked else WELL_EDGE)
    s_well(g, x, y, w, h, 1, TILE_BG, border)
    if checked:
        r = max(min(w, h) // 2 - 3, 1)
        diamond(g, x + w // 2, y + h // 2, r, ACCENT)
        diamond(g, x + w // 2, y + h // 2 - 1, max(r - 2, 0), lighten(ACCENT, 0.45))


def slider(g, x, y, w, h, value, hovered):
    s_well(g, x, y, w, h, 1, TILE_BG, BORDER_BRIGHT if hovered else WELL_EDGE)
    fw = int((w - 2) * value)
    body = ACCENT if hovered else ACCENT_DIM
    g.fillGradient(x + 1, y + 1, x + 1 + fw, y + h - 1, lighten(body, 0.12), darken(body, 0.35))
    g.fill(x + 1, y + 1, x + 1 + fw, y + 2, HIGHLIGHT)
    g.fill(x + fw, y + 1, x + 1 + fw, y + h - 1, lighten(body, 0.5))


def item_tile(g, x, y, rgb):
    T = 18
    bg = 0x8014100B
    if rgb is None:
        s_well(g, x, y, T, T, 1, bg, TILE_BORDER)
        return
    s_fill(g, x, y, T, T, 1, bg)
    g.fillGradient(x + 1, y + 1, x + T - 1, y + T - 1, 0x6E000000 | rgb, 0x1C000000 | rgb)
    g.fillGradient(x + 1, y + 1, x + T - 1, y + 6, 0x50000000, 0x00000000)
    g.fill(x + 2, y + 1, x + T - 2, y + 2, 0x30FFFFFF)
    g.fill(x + 2, y + T - 2, x + T - 2, y + T - 1, 0x48000000 | rgb)
    s_frame(g, x, y, T, T, 1, 0xFF000000 | rgb)


def main(out_dir):
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    g = Gfx(300, 190, (46, 58, 34))
    texts = []
    s_panel(g, 10, 10, 280, 170)
    title_band(g, 10, 10, 280, "Options", texts)
    s_raised(g, 24, 54, 120, 20, TILE_BG, HAIRLINE)
    s_raised(g, 156, 54, 120, 20, TILE_BG, BORDER_BRIGHT, hovered=True)
    s_raised(g, 24, 84, 120, 20, ACCENT_DIM, ACCENT, glow=ACCENT)
    s_raised(g, 156, 84, 120, 20, TILE_BG, BORDER_BRIGHT, hovered=True, pressed=True)
    slider(g, 24, 114, 120, 18, 0.62, False)
    slider(g, 156, 114, 120, 18, 0.35, True)
    checkbox(g, 24, 142, 14, 14, True, False)
    checkbox(g, 48, 142, 14, 14, False, False)
    s_well(g, 156, 140, 120, 22, 2)
    thumb(g, 268, 144, 3, 14)
    for i, rgb in enumerate([None, 0xD4AF37, 0xAA55FF, 0x55FF55, 0xFF5555]):
        item_tile(g, 78 + i * 22, 142, rgb)
    img = g.image(texts)
    img.save(out / "menu_kit.png")
    g2 = Gfx(220, 120, (60, 48, 40))
    s_panel(g2, 10, 10, 200, 100, glass=True, cut=3)
    s_raised(g2, 24, 30, 172, 34, TILE_BG, ACCENT, cut=3, glow=ACCENT)
    s_raised(g2, 24, 72, 172, 22, TILE_BG, HAIRLINE)
    g2.image().save(out / "menu_glass.png")
    print(out)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "shape_out")
