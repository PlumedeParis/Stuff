"""Génère les textures pixel-art des blocs et objets de Rail Express.

Usage : python3 tools/generate_textures.py
Les icônes des véhicules sont rendues séparément par tools/render_icons.py.
"""
import math
import os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "railexpress", "textures")
BLOCK = os.path.join(ROOT, "block")
ITEM = os.path.join(ROOT, "item")
ENTITY = os.path.join(ROOT, "entity")
for d in (BLOCK, ITEM, ENTITY):
    os.makedirs(d, exist_ok=True)


def rgba(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


# ---------------------------------------------------------------- rails
def rail_straight(sleeper, sleeper_dark, rail, rail_hi, sleepers=(1, 5, 9, 13), centre=None, sleeper_h=2):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y0 in sleepers:
        for y in range(y0, y0 + sleeper_h):
            for x in range(1, 15):
                px[x, y] = sleeper if y == y0 else sleeper_dark
    if centre:
        for y in range(16):
            for x in (7, 8):
                px[x, y] = centre[(y // 2) % len(centre)] if isinstance(centre, list) else centre
    for y in range(16):
        for x, c in ((3, rail_hi), (4, rail), (11, rail), (12, rail_hi)):
            px[x, y] = c
    return im


def rail_corner(sleeper, sleeper_dark, rail, rail_hi, centre=None, n_sleepers=4):
    """Courbe reliant le bord sud et le bord est (centre de courbure : coin sud-est)."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        for x in range(16):
            dx, dy = 16 - (x + 0.5), 16 - (y + 0.5)
            r = math.hypot(dx, dy)
            ang = math.degrees(math.atan2(dy, dx))  # 0 = bord est, 90 = bord sud
            t = ang / 90 * n_sleepers
            on_sleeper = abs(t - round(t)) < 0.22 and 1.5 < r < 15
            if on_sleeper:
                px[x, y] = sleeper
            if centre and 7.5 < r <= 9.0:
                px[x, y] = centre[int(ang // 12) % len(centre)] if isinstance(centre, list) else centre
            if 12.0 < r <= 13.0 or 3.0 < r <= 4.0:
                px[x, y] = rail_hi
            elif 11.0 < r <= 12.0 or 4.0 < r <= 5.0:
                px[x, y] = rail
    return im


def save(im, folder, name):
    im.save(os.path.join(folder, name + ".png"))


CONCRETE = rgba("b9b6ad")
CONCRETE_D = rgba("8f8c84")
WOOD = rgba("6b4a2e")
WOOD_D = rgba("4f3520")
STEEL = rgba("5d646c")
STEEL_HI = rgba("b8c0c8")
CHROME = rgba("8e979f")
CHROME_HI = rgba("e6ecf1")

save(rail_straight(CONCRETE, CONCRETE_D, STEEL, STEEL_HI), BLOCK, "track")
save(rail_corner(CONCRETE, CONCRETE_D, STEEL, STEEL_HI), BLOCK, "track_corner")
save(rail_straight(rgba("d9d6cf"), rgba("a9a69f"), CHROME, CHROME_HI, sleepers=(0, 4, 8, 12)), BLOCK, "high_speed_track")
save(rail_corner(rgba("d9d6cf"), rgba("a9a69f"), CHROME, CHROME_HI, n_sleepers=5), BLOCK, "high_speed_track_corner")
copper_off = rgba("7a4a2a")
copper_on = [rgba("ffd36b"), rgba("ffb53b")]
save(rail_straight(rgba("d9d6cf"), rgba("a9a69f"), CHROME, CHROME_HI, sleepers=(0, 4, 8, 12), centre=copper_off), BLOCK, "electrified_track")
save(rail_straight(rgba("d9d6cf"), rgba("a9a69f"), CHROME, CHROME_HI, sleepers=(0, 4, 8, 12), centre=copper_on), BLOCK, "electrified_track_on")
save(rail_corner(rgba("d9d6cf"), rgba("a9a69f"), CHROME, CHROME_HI, centre=copper_off, n_sleepers=5), BLOCK, "electrified_track_corner")
save(rail_corner(rgba("d9d6cf"), rgba("a9a69f"), CHROME, CHROME_HI, centre=copper_on, n_sleepers=5), BLOCK, "electrified_track_corner_on")
hazard = [rgba("f2c14e"), rgba("1d1d1f")]
save(rail_straight(CONCRETE, CONCRETE_D, STEEL, STEEL_HI, centre=[rgba("e5484d"), rgba("f4f4f4")]), BLOCK, "station_track")
save(rail_straight(CONCRETE, CONCRETE_D, STEEL, STEEL_HI, centre=[rgba("3dd68c"), rgba("f4f4f4")]), BLOCK, "station_track_on")


# ---------------------------------------------------------------- blocs décoratifs
def substation(face):
    im = Image.new("RGBA", (16, 16), rgba("5f6670"))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 15], outline=rgba("3b4048"))
    if face == "top":
        d.rectangle([2, 2, 13, 13], fill=rgba("4a5059"))
        for x in range(3, 13, 3):
            d.line([x, 3, x, 12], fill=rgba("3b4048"))
        return im
    d.rectangle([2, 2, 13, 13], fill=rgba("6f7781"), outline=rgba("464c55"))
    d.polygon([(9, 3), (5, 9), (8, 9), (6, 13), (11, 6), (8, 6), (10, 3)], fill=rgba("f2c14e"))
    d.point([(3, 14), (12, 14)], fill=rgba("e5484d"))
    return im


save(substation("side"), BLOCK, "substation_side")
save(substation("top"), BLOCK, "substation_top")

mast = Image.new("RGBA", (16, 16), rgba("7d858e"))
md = ImageDraw.Draw(mast)
for y in range(0, 16, 4):
    md.line([0, y, 15, y], fill=rgba("6a727b"))
md.line([0, 0, 0, 15], fill=rgba("a3abb3"))
save(mast, BLOCK, "catenary_mast")
insulator = Image.new("RGBA", (16, 16), rgba("8e3b2e"))
idr = ImageDraw.Draw(insulator)
for y in range(0, 16, 3):
    idr.line([0, y, 15, y], fill=rgba("b0503f"))
save(insulator, BLOCK, "insulator")

stripes = Image.new("RGBA", (16, 16), hazard[0])
sd = ImageDraw.Draw(stripes)
for k in range(-16, 32, 6):
    sd.polygon([(k, 0), (k + 3, 0), (k + 3 - 16, 16), (k - 16, 16)], fill=hazard[1])
save(stripes, BLOCK, "hazard_stripes")
red = Image.new("RGBA", (16, 16), rgba("b3261e"))
ImageDraw.Draw(red).rectangle([0, 0, 15, 15], outline=rgba("7e1a14"))
save(red, BLOCK, "buffer_red")
dark_steel = Image.new("RGBA", (16, 16), rgba("33373d"))
ImageDraw.Draw(dark_steel).rectangle([0, 0, 15, 15], outline=rgba("23262a"))
save(dark_steel, BLOCK, "dark_steel")

plat_side = Image.new("RGBA", (16, 16), rgba("a8a59e"))
pd = ImageDraw.Draw(plat_side)
pd.rectangle([0, 0, 15, 1], fill=rgba("cfccc4"))
for y in (6, 11):
    pd.line([0, y, 15, y], fill=rgba("96938c"))
save(plat_side, BLOCK, "platform_side")
plat_top = Image.new("RGBA", (16, 16), rgba("c3c0b8"))
ptd = ImageDraw.Draw(plat_top)
for x in range(0, 16, 4):
    ptd.line([x, 0, x, 15], fill=rgba("b3b0a8"))
ptd.rectangle([0, 0, 15, 1], fill=rgba("f2c14e"))
ptd.rectangle([0, 3, 15, 3], fill=rgba("e8e5dc"))
for x in range(1, 16, 3):
    ptd.point([(x, 3)], fill=rgba("ffffff"))
save(plat_top, BLOCK, "platform_top")

# ---------------------------------------------------------------- objets (32 x 32)
def item(draw_fn, name):
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(im), im)
    save(im, ITEM, name)


def steel_ingot(d, im):
    d.polygon([(4, 18), (10, 11), (28, 11), (22, 18)], fill=rgba("c9d2db"))
    d.polygon([(4, 18), (22, 18), (22, 23), (4, 23)], fill=rgba("8e99a4"))
    d.polygon([(22, 18), (28, 11), (28, 16), (22, 23)], fill=rgba("6b7681"))
    d.line([(11, 12), (26, 12)], fill=rgba("f2f6f9"))
    d.line([(4, 23), (22, 23)], fill=rgba("4d565f"))


def wheel_set(d, im):
    d.rectangle([5, 15, 27, 17], fill=rgba("6b737c"))
    for cx in (8, 24):
        d.ellipse([cx - 6, 9, cx + 6, 23], fill=rgba("2b2e33"))
        d.ellipse([cx - 4, 11, cx + 4, 21], fill=rgba("8a9198"))
        d.ellipse([cx - 1, 15, cx + 1, 17], fill=rgba("d2a93f"))


def boiler(d, im):
    d.rounded_rectangle([3, 9, 27, 23], 6, fill=rgba("1f5a35"))
    d.rectangle([3, 10, 27, 12], fill=rgba("2f7a4a"))
    for x in (9, 15, 21):
        d.rectangle([x, 9, x + 1, 23], fill=rgba("d2a93f"))
    d.ellipse([24, 9, 30, 23], fill=rgba("1d1d1f"))
    d.rectangle([12, 4, 15, 9], fill=rgba("1d1d1f"))
    d.rectangle([11, 3, 16, 4], fill=rgba("2a2a2d"))


def motor(d, im):
    d.rounded_rectangle([5, 9, 23, 25], 4, fill=rgba("4a5059"))
    for x in range(7, 22, 3):
        d.rectangle([x, 9, x + 1, 25], fill=rgba("c87533"))
    d.rectangle([23, 15, 29, 18], fill=rgba("b8c0c8"))
    d.rectangle([3, 12, 5, 22], fill=rgba("2b2e33"))
    d.polygon([(14, 2), (10, 9), (13, 9), (11, 14), (17, 6), (14, 6), (16, 2)], fill=rgba("f2c14e"))


def pantograph(d, im):
    d.rectangle([6, 26, 26, 28], fill=rgba("3a3d42"))
    d.line([(8, 26), (20, 15)], fill=rgba("9ea4aa"), width=2)
    d.line([(20, 15), (10, 6)], fill=rgba("9ea4aa"), width=2)
    d.rectangle([3, 4, 29, 6], fill=rgba("55595f"))
    for x in (9, 23):
        d.rectangle([x - 1, 27, x + 1, 30], fill=rgba("b5463a"))


def seat(d, im):
    d.rounded_rectangle([8, 3, 22, 18], 3, fill=rgba("2c55a8"))
    d.rectangle([10, 5, 20, 7], fill=rgba("4f7ad0"))
    d.rounded_rectangle([6, 17, 26, 23], 3, fill=rgba("23468c"))
    d.rectangle([9, 23, 11, 29], fill=rgba("3a3d42"))
    d.rectangle([21, 23, 23, 29], fill=rgba("3a3d42"))
    d.rectangle([7, 4, 8, 8], fill=rgba("e6ecf1"))


def coupler(d, im):
    d.rectangle([3, 13, 14, 18], fill=rgba("6b737c"))
    d.rectangle([3, 13, 14, 14], fill=rgba("b8c0c8"))
    d.arc([12, 9, 22, 22], 270, 90, fill=rgba("8a9198"), width=3)
    for i, x in enumerate(range(20, 30, 4)):
        d.ellipse([x, 12 + (i % 2) * 2, x + 4, 16 + (i % 2) * 2], outline=rgba("d2a93f"), width=1)
    d.rectangle([2, 11, 4, 20], fill=rgba("b3261e"))


item(steel_ingot, "steel_ingot")
item(wheel_set, "wheel_set")
item(boiler, "boiler")
item(motor, "electric_motor")
item(pantograph, "pantograph")
item(seat, "train_seat")
item(coupler, "coupler")

Image.new("RGBA", (16, 16), (255, 255, 255, 255)).save(os.path.join(ENTITY, "white.png"))

# ---------------------------------------------------------------- textures des rails 3D
import random
rnd = random.Random(42)


def noise_tex(base, var, name, seed_pattern=None):
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            f = 1 + rnd.uniform(-var, var)
            if seed_pattern:
                f *= seed_pattern(x, y)
            px[x, y] = shade(base, f)
    save(im, BLOCK, name)


noise_tex(rgba("7d776e"), 0.22, "ballast", lambda x, y: 0.8 if (x * 7 + y * 3) % 5 == 0 else 1.0)
noise_tex(rgba("6b4a2e"), 0.08, "sleeper_wood", lambda x, y: 0.85 if y % 4 == 0 else 1.0)
noise_tex(rgba("b9b6ad"), 0.06, "sleeper_concrete")
noise_tex(rgba("5d646c"), 0.05, "rail_steel")
noise_tex(rgba("c9d0d6"), 0.05, "rail_head")
noise_tex(rgba("8a5a32"), 0.06, "copper_off")
noise_tex(rgba("ffc84a"), 0.08, "copper_on")
noise_tex(rgba("e5484d"), 0.04, "marker_red")
noise_tex(rgba("3dd68c"), 0.04, "marker_green")
print("textures générées")
