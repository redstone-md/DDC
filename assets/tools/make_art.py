"""Generates DDC's banner and mod icon.

The art is computed rather than drawn: a real icosahedron is projected and shaded, and the voxels it
breaks into are real cubes sharing the same camera. That is the whole idea of the mod in one image --
the die of the tabletop coming apart into the blocks of Minecraft -- so it is worth being geometry
rather than a lookalike.

Run from the repository root:

    python assets/tools/make_art.py

Fonts come from the system (Bookman Old Style, Constantia, Segoe UI). Only the outputs are committed,
so nothing in the build or CI depends on this script or on those fonts.
"""

from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

# Palette: a Game Master's screen seen by candlelight -- oxblood leather, brass, parchment.
INK = (18, 16, 14)
OXBLOOD = (74, 28, 36)
BRASS_DARK = (110, 74, 30)
BRASS = (201, 151, 63)
BRASS_LIGHT = (242, 200, 121)
PARCHMENT = (232, 220, 192)
MUTED = (138, 127, 107)

FONT_DIR = Path("C:/Windows/Fonts")
DISPLAY_FONT = FONT_DIR / "BOOKOSB.TTF"   # Bookman Old Style Bold: an old rulebook cover
BODY_FONT = FONT_DIR / "constan.ttf"      # Constantia: warm, humanist
DATA_FONT = FONT_DIR / "segoeuib.ttf"     # Segoe UI Bold: the technical register

OUT_DIR = Path("assets")
ICON_TARGETS = [
    Path("common/src/main/resources/assets/ddc/icon.png"),
    OUT_DIR / "icon.png",
]

PHI = (1 + math.sqrt(5)) / 2


# ---------------------------------------------------------------------------- geometry


def icosahedron():
    """The 12 vertices and 20 faces of a unit-ish icosahedron, edge length 2."""
    verts = []
    for s1 in (-1, 1):
        for s2 in (-1, 1):
            verts.append((0.0, s1 * 1.0, s2 * PHI))
            verts.append((s1 * 1.0, s2 * PHI, 0.0))
            verts.append((s1 * PHI, 0.0, s2 * 1.0))

    def dist(a, b):
        return math.dist(a, b)

    faces = []
    for i in range(len(verts)):
        for j in range(i + 1, len(verts)):
            for k in range(j + 1, len(verts)):
                if (abs(dist(verts[i], verts[j]) - 2) < 1e-6
                        and abs(dist(verts[j], verts[k]) - 2) < 1e-6
                        and abs(dist(verts[i], verts[k]) - 2) < 1e-6):
                    faces.append((i, j, k))
    return verts, faces


def cube(size=1.0):
    """A cube's 8 vertices and 6 quad faces, centred on the origin."""
    h = size / 2
    verts = [(x * h, y * h, z * h)
             for x in (-1, 1) for y in (-1, 1) for z in (-1, 1)]
    faces = [
        (0, 1, 3, 2), (4, 6, 7, 5),  # -x, +x
        (0, 4, 5, 1), (2, 3, 7, 6),  # -y, +y
        (0, 2, 6, 4), (1, 5, 7, 3),  # -z, +z
    ]
    return verts, faces


def rotate(v, ax, ay, az=0.0):
    x, y, z = v
    cy, sy = math.cos(ay), math.sin(ay)
    x, z = x * cy + z * sy, -x * sy + z * cy
    cx, sx = math.cos(ax), math.sin(ax)
    y, z = y * cx - z * sx, y * sx + z * cx
    cz, sz = math.cos(az), math.sin(az)
    x, y = x * cz - y * sz, x * sz + y * cz
    return (x, y, z)


def normal(points):
    """The outward normal of a face of a solid centred on the origin.

    The winding of a face cannot be assumed here -- the icosahedron's faces come out of a search over
    vertex triples, in whatever order the search found them -- so the normal is flipped to agree with
    the face's own centroid. Without this, half the normals point inward: those faces get culled and
    shaded as though lit from behind, and the die renders with holes in it.
    """
    (ax, ay, az), (bx, by, bz), (cx, cy, cz) = points[0], points[1], points[2]
    ux, uy, uz = bx - ax, by - ay, bz - az
    vx, vy, vz = cx - ax, cy - ay, cz - az
    n = [uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx]
    length = math.sqrt(sum(c * c for c in n)) or 1.0
    n = [c / length for c in n]

    centroid = [sum(p[i] for p in points) / len(points) for i in range(3)]
    if sum(n[i] * centroid[i] for i in range(3)) < 0:
        n = [-c for c in n]
    return tuple(n)


def lit(n, light=(-0.45, 0.75, 0.62)):
    """Lambert intensity, floored so no facet goes fully black."""
    length = math.sqrt(sum(c * c for c in light))
    l = tuple(c / length for c in light)
    return max(0.0, sum(a * b for a, b in zip(n, l)))


def ramp(t, low=BRASS_DARK, mid=BRASS, high=BRASS_LIGHT):
    """Brass shading ramp: dark -> brass -> highlight."""
    t = min(1.0, max(0.0, t))
    if t < 0.5:
        a, b, k = low, mid, t / 0.5
    else:
        a, b, k = mid, high, (t - 0.5) / 0.5
    return tuple(round(a[i] + (b[i] - a[i]) * k) for i in range(3))


# ---------------------------------------------------------------------------- drawing


def die_orientation():
    """Rotation that puts one face toward the camera, then tilts to show the facets around it."""
    verts, faces = icosahedron()
    # Bring the normal of a chosen face onto +z, so the die reads as a die rather than a blob.
    front = faces[0]
    n = normal([verts[i] for i in front])
    ay = math.atan2(n[0], n[2])
    n2 = rotate(n, 0.0, -ay)
    ax = -math.atan2(n2[1], n2[2])
    return verts, faces, (-ay, ax)


def draw_die(draw, cx, cy, radius, label=None, label_font=None, edge_alpha=170):
    verts, faces, (ay, ax) = die_orientation()
    tilt_x, tilt_y = math.radians(-13), math.radians(9)

    placed = []
    for face in faces:
        pts3 = [rotate(rotate(verts[i], ax, ay), tilt_x, tilt_y) for i in face]
        n = normal(pts3)
        if n[2] <= 0.02:  # cull the far side
            continue
        scale = radius / PHI
        pts2 = [(cx + p[0] * scale, cy - p[1] * scale) for p in pts3]
        depth = sum(p[2] for p in pts3) / 3
        placed.append((depth, pts2, ramp(lit(n)), n, pts3))

    placed.sort(key=lambda f: f[0])
    for _, pts2, colour, _, _ in placed:
        draw.polygon(pts2, fill=colour, outline=(*INK, edge_alpha))

    if label and label_font:
        front = max(placed, key=lambda f: f[3][2])
        lx = sum(p[0] for p in front[1]) / 3
        ly = sum(p[1] for p in front[1]) / 3
        # Nudge down: a triangle's centroid sits above its optical centre for a two-digit label.
        draw.text((lx, ly + radius * 0.06), label, font=label_font,
                  fill=(*INK, 230), anchor="mm")


def draw_voxels(draw, cx, cy, radius, reach, seed=20):
    """Cubes coming off the die, in the same camera as the die itself.

    `reach` bounds how far right they travel, so the dissolve stays a bridge between the die and the
    text rather than drifting across the words.
    """
    rng = random.Random(seed)
    cverts, cfaces = cube()
    tilt_x, tilt_y = math.radians(-13), math.radians(9)

    blocks = []
    for i in range(22):
        t = i / 21
        # One arc rising to the right, tight at the die and loosening as it goes: the die coming
        # apart along a path, not an explosion. Scatter grows with t so the near blocks stay
        # attached to the silhouette they broke off.
        x = radius * 0.80 + t * reach + rng.uniform(-0.06, 0.06) * radius
        y = -t * radius * 0.62 + rng.gauss(0.0, 0.16 + t * 0.34) * radius
        size = radius * (0.28 - 0.20 * t) * rng.uniform(0.85, 1.15)
        alpha = int(240 * (1.0 - t) ** 1.3)
        if alpha < 25 or size < radius * 0.05:
            continue
        blocks.append((x, y, size, alpha, rng.uniform(-0.5, 0.5)))

    blocks.sort(key=lambda b: -b[2])  # big ones (nearest the die) drawn first
    for x, y, size, alpha, spin in blocks:
        faces3 = []
        for face in cfaces:
            pts3 = [rotate(rotate(cverts[i], 0.0, spin), tilt_x, tilt_y + 0.5) for i in face]
            n = normal(pts3)
            if n[2] <= 0:
                continue
            pts2 = [(cx + x + p[0] * size, cy + y - p[1] * size) for p in pts3]
            faces3.append((sum(p[2] for p in pts3) / 4, pts2, ramp(lit(n) * 0.92)))
        faces3.sort(key=lambda f: f[0])
        for _, pts2, colour in faces3:
            draw.polygon(pts2, fill=(*colour, alpha), outline=(*INK, min(alpha, 120)))


def battle_grid(size, cell, alpha):
    """The faint grid a battle map and a voxel world happen to share."""
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for x in range(0, size[0], cell):
        draw.line([(x, 0), (x, size[1])], fill=(*PARCHMENT, alpha), width=max(1, cell // 60))
    for y in range(0, size[1], cell):
        draw.line([(0, y), (size[0], y)], fill=(*PARCHMENT, alpha), width=max(1, cell // 60))
    return layer


def background(size):
    """Ink at the edges warming to oxblood behind the die: one candle, off to the left."""
    w, h = size
    base = Image.new("RGB", size, INK)
    draw = ImageDraw.Draw(base)
    for y in range(h):
        t = y / h
        k = 0.35 * (1 - abs(t - 0.45) * 1.6)
        k = max(0.0, k)
        draw.line([(0, y), (w, y)],
                  fill=tuple(round(INK[i] + (OXBLOOD[i] - INK[i]) * k) for i in range(3)))

    glow = Image.new("L", size, 0)
    ImageDraw.Draw(glow).ellipse(
        [w * 0.02, -h * 0.35, w * 0.62, h * 1.35], fill=90)
    glow = glow.filter(ImageFilter.GaussianBlur(radius=w // 14))
    base = Image.composite(Image.new("RGB", size, OXBLOOD), base, glow)

    base = base.convert("RGBA")
    base.alpha_composite(battle_grid(size, cell=max(8, w // 42), alpha=9))
    return base


def tracked_text(draw, xy, text, font, fill, tracking):
    """Letterspaced text; PIL has no tracking, and the eyebrow needs it."""
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, font=font, fill=fill)
        x += draw.textlength(ch, font=font) + tracking
    return x


# ---------------------------------------------------------------------------- outputs


def fitted_font(draw, path, text, max_width, start):
    """Largest size at which `text` still fits `max_width`. The title must never run off the edge."""
    size = start
    while size > 8:
        font = ImageFont.truetype(str(path), int(size))
        if draw.textlength(text, font=font) <= max_width:
            return font
        size *= 0.97
    return ImageFont.truetype(str(path), 8)


def make_banner(path, width=1600, height=500, ss=3):
    w, h = width * ss, height * ss
    img = background((w, h))
    draw = ImageDraw.Draw(img, "RGBA")

    left = w * 0.42
    right_margin = w * 0.05
    column = w - left - right_margin

    radius = h * 0.32
    cx, cy = w * 0.155, h * 0.52
    # The dissolve stops short of the text column.
    draw_voxels(draw, cx, cy, radius, reach=left - cx - radius * 0.9)
    draw_die(draw, cx, cy, radius,
             label="20", label_font=ImageFont.truetype(str(DISPLAY_FONT), int(radius * 0.40)))

    eyebrow = ImageFont.truetype(str(DATA_FONT), int(h * 0.036))
    display = fitted_font(draw, DISPLAY_FONT, "DUNGEONS, DRAGONS", column, h * 0.150)
    body = ImageFont.truetype(str(BODY_FONT), int(h * 0.060))

    y = h * 0.21
    tracked_text(draw, (left, y), "MINECRAFT 26.1.2   FABRIC   NEOFORGE",
                 eyebrow, (*MUTED, 255), tracking=h * 0.011)

    y += h * 0.10
    draw.text((left, y), "DUNGEONS, DRAGONS", font=display, fill=PARCHMENT)
    y += h * 0.165
    draw.text((left, y), "& CRAFTING", font=display, fill=BRASS_LIGHT)

    y += h * 0.205
    draw.line([(left, y), (left + h * 0.28, y)], fill=(*BRASS, 255), width=max(1, int(h * 0.005)))

    y += h * 0.055
    draw.text((left, y), "Dice, character sheets, and a Game Master", font=body, fill=(*PARCHMENT, 220))
    draw.text((left, y + h * 0.082), "who runs the world.", font=body, fill=(*PARCHMENT, 220))

    img = img.resize((width, height), Image.LANCZOS)
    path.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGB").save(path, optimize=True)
    print(f"wrote {path} ({path.stat().st_size // 1024} KB)")


def make_icon(size=128, ss=8):
    s = size * ss
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img, "RGBA")

    # A rounded ink tile so the die reads against any mod-list background.
    draw.rounded_rectangle([0, 0, s - 1, s - 1], radius=s * 0.14, fill=(*INK, 255))
    draw.rounded_rectangle([0, 0, s - 1, s - 1], radius=s * 0.14,
                           outline=(*BRASS_DARK, 255), width=max(1, s // 64))

    draw_die(draw, s / 2, s * 0.51, s * 0.40,
             label="20", label_font=ImageFont.truetype(str(DISPLAY_FONT), int(s * 0.15)),
             edge_alpha=200)

    img = img.resize((size, size), Image.LANCZOS)
    for target in ICON_TARGETS:
        target.parent.mkdir(parents=True, exist_ok=True)
        img.save(target, optimize=True)
        print(f"wrote {target} ({target.stat().st_size // 1024} KB)")





def make_wand_texture(size=16, ss=16):
    """The GM wand's item texture: a brass rod with a d20 head, in the banner's palette.

    Drawn at 16x16 because that is Minecraft's item grid; anything else looks foreign next to
    vanilla items no matter how nicely it renders.
    """
    s = size * ss
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img, "RGBA")

    # The rod, running corner to corner as vanilla tools do.
    rod = s * 0.09
    for i in range(int(s * 0.52)):
        t = i / (s * 0.52)
        x = s * 0.16 + i * 0.86
        y = s * 0.84 - i * 0.86
        colour = ramp(0.30 + t * 0.25, low=(60, 40, 18), mid=BRASS_DARK, high=BRASS)
        draw.ellipse([x - rod, y - rod, x + rod, y + rod], fill=(*colour, 255))

    # The d20 head, the same solid the banner and the icon use.
    draw_die(draw, s * 0.66, s * 0.32, s * 0.28, edge_alpha=210)

    img = img.resize((size, size), Image.NEAREST if ss == 1 else Image.LANCZOS)
    target = Path("common/src/main/resources/assets/ddc/textures/item/gm_wand.png")
    target.parent.mkdir(parents=True, exist_ok=True)
    img.save(target, optimize=True)
    print(f"wrote {target}")


def make_spellbook_texture(size=16, ss=16):
    """The spellbook's item texture: a closed tome with a brass clasp, in the banner's palette."""
    s = size * ss
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img, "RGBA")

    # Cover, spine to the left, the oxblood of the GM screen.
    draw.rounded_rectangle([s * 0.16, s * 0.10, s * 0.86, s * 0.90], radius=s * 0.06,
                           fill=(*OXBLOOD, 255), outline=(*INK, 255), width=max(1, s // 40))
    draw.rectangle([s * 0.16, s * 0.10, s * 0.30, s * 0.90], fill=(60, 22, 29, 255))
    # Pages.
    draw.rectangle([s * 0.32, s * 0.16, s * 0.82, s * 0.84], fill=(*PARCHMENT, 255))
    draw.rectangle([s * 0.32, s * 0.16, s * 0.36, s * 0.84], fill=(200, 188, 160, 255))
    # Clasp.
    draw.rectangle([s * 0.62, s * 0.44, s * 0.90, s * 0.56], fill=(*BRASS, 255),
                   outline=(*BRASS_DARK, 255), width=max(1, s // 48))

    img = img.resize((size, size), Image.LANCZOS)
    target = Path("common/src/main/resources/assets/ddc/textures/item/spellbook.png")
    target.parent.mkdir(parents=True, exist_ok=True)
    img.save(target, optimize=True)
    print(f"wrote {target}")


if __name__ == "__main__":
    make_banner(OUT_DIR / "banner.png")
    make_icon()
    make_wand_texture()
    make_spellbook_texture()
