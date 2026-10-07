"""Génère les fichiers JSON (états de blocs, modèles, recettes, butins, tags) de Rail Express."""
import json
import os

NS = "railexpress"
RES = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", NS)
DATA = os.path.join(RES, "data")


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def blockstate(name, obj):
    write(os.path.join(ASSETS, "blockstates", name + ".json"), obj)


def block_model(name, obj):
    write(os.path.join(ASSETS, "models", "block", name + ".json"), obj)


def item_model(name, obj):
    write(os.path.join(ASSETS, "models", "item", name + ".json"), obj)


def item_def(name, model):
    write(os.path.join(ASSETS, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": model}})


def generated_item(name, texture):
    item_model(name, {"parent": "minecraft:item/generated", "textures": {"layer0": texture}})
    item_def(name, f"{NS}:item/{name}")


# ------------------------------------------------------------------ voies
import math


def el(fr, to, tex, rot=None):
    e = {"from": [round(v, 4) for v in fr], "to": [round(v, 4) for v in to],
         "faces": {f: {"texture": tex} for f in ("north", "south", "east", "west", "up", "down")}}
    if rot:
        e["rotation"] = rot
    return e


RAIL_X = (1.0, 15.0)  # axes des deux files de rails (écartement 14 px)


def straight_parts(t, y=0.0, extra=None):
    """Éléments d'une voie droite orientée nord-sud, plan de roulement à l'altitude y."""
    parts = [el([-4, y, 0], [20, y + 1, 16], "#ballast")]
    for zc in (2.67, 8.0, 13.33):
        parts.append(el([-3, y + 1, zc - 1.5], [19, y + 2.5, zc + 1.5], "#sleeper"))
    for xc in RAIL_X:
        parts.append(el([xc - 1.5, y + 2.5, 0], [xc + 1.5, y + 3, 16], "#rail"))
        parts.append(el([xc - 0.5, y + 3, 0], [xc + 0.5, y + 4.5, 16], "#rail"))
        parts.append(el([xc - 1, y + 4.5, 0], [xc + 1, y + 5.5, 16], "#head"))
    if extra:
        parts += extra(y)
    return parts


def rotated(e, axis, angle, origin):
    e["rotation"] = {"origin": origin, "axis": axis, "angle": angle, "rescale": True}
    return e


def oriented(cx, cz, length, width, y0, y1, beta, tex):
    """Élément centré en (cx, cz), long de `length` selon sa direction, tourné de beta degrés autour de Y."""
    snap = round(beta / 22.5) * 22.5
    if abs(snap) <= 45:
        fr, to, ang = [cx - width / 2, y0, cz - length / 2], [cx + width / 2, y1, cz + length / 2], snap
    else:
        ang = snap - 90 if snap > 0 else snap + 90
        fr, to = [cx - length / 2, y0, cz - width / 2], [cx + length / 2, y1, cz + width / 2]
    e = el(fr, to, tex)
    if ang:
        e["rotation"] = {"origin": [cx, y0, cz], "axis": "y", "angle": ang}
    return e


def corner_parts(extra_centre=None):
    """Courbe reliant le bord sud (8,16) et le bord est (16,8) ; centre de courbure (16,16)."""
    parts = []
    steps = [(180.0, 0.5), (202.5, 1), (225.0, 1), (247.5, 1), (270.0, 0.5)]
    dth = math.radians(22.5)
    for theta, frac in steps:
        th = math.radians(theta)
        tx, tz = -math.sin(th), math.cos(th)
        beta = math.degrees(math.atan2(tx, tz))
        while beta > 90:
            beta -= 180
        while beta <= -90:
            beta += 180
        shift = 0 if frac == 1 else (0.25 if theta == 180 else -0.25)
        thc = th + shift * dth
        def at(r):
            return 16 + r * math.cos(thc), 16 + r * math.sin(thc)
        cx, cz = at(8)
        parts.append(oriented(cx, cz, 8 * dth * frac * 1.35, 24, 0, 1, beta, "#ballast"))
        for r, tex, y0, y1, w in ((1.0, "#rail", 2.5, 3, 3), (15.0, "#rail", 2.5, 3, 3), (1.0, "#rail", 3, 4.5, 1),
                                  (15.0, "#rail", 3, 4.5, 1), (1.0, "#head", 4.5, 5.5, 2), (15.0, "#head", 4.5, 5.5, 2)):
            px, pz = at(r)
            parts.append(oriented(px, pz, max(0.6, r * dth * frac * 1.08), w, y0, y1, beta, tex))
        if extra_centre:
            px, pz = at(8)
            parts.append(oriented(px, pz, 8 * dth * frac * 1.1, 1, 2.5, 3, beta, extra_centre))
    for theta in (191.25, 225.0, 258.75):
        th = math.radians(theta)
        cx, cz = 16 + 8 * math.cos(th), 16 + 8 * math.sin(th)
        tx, tz = -math.sin(th), math.cos(th)
        beta = math.degrees(math.atan2(tx, tz)) + 90
        while beta > 90:
            beta -= 180
        while beta <= -90:
            beta += 180
        parts.append(oriented(cx, cz, 21, 3, 1, 2.5, beta, "#sleeper"))
    return parts


def raised_parts(extra=None):
    """Voie en pente montant vers le nord : la voie droite est tournée de 45° autour de X."""
    flat = straight_parts(None, 0.0, extra)
    out = []
    for e in flat:
        # Épaisseurs divisées par deux : avec la remise à l'échelle (x1,414) de la rotation à 45°,
        # le plan de roulement reste à la même hauteur verticale que sur la voie plate.
        e["from"][1] = 8.0 + e["from"][1] * 0.5
        e["to"][1] = 8.0 + e["to"][1] * 0.5
        out.append(rotated(e, "x", 45, [8, 8, 8]))
    return out


def rail_models(name, textures, centre=None, curves=True):
    tex = dict(textures)
    tex["particle"] = tex["sleeper"]
    extra = None
    if centre:
        tex["centre"] = centre
        extra = lambda y: [el([7.5, y + 2.5, 0], [8.5, y + 3, 16], "#centre")]
    block_model(f"{name}_flat", {"textures": tex, "elements": straight_parts(None, 0.0, extra)})
    block_model(f"{name}_raised", {"textures": tex, "elements": raised_parts(extra)})
    if curves:
        block_model(f"{name}_corner", {"textures": tex, "elements": corner_parts("#centre" if centre else None)})


RAIL_SHAPES = {
    "north_south": ("flat", 0), "east_west": ("flat", 90),
    "ascending_north": ("raised", 0), "ascending_east": ("raised", 90),
    "ascending_south": ("raised", 180), "ascending_west": ("raised", 270),
    "south_east": ("corner", 0), "south_west": ("corner", 90),
    "north_west": ("corner", 180), "north_east": ("corner", 270),
}


def rail_variants(name, curves=True, suffix=""):
    variants = {}
    for shape, (kind, rot) in RAIL_SHAPES.items():
        if kind == "corner" and not curves:
            continue
        v = {"model": f"{NS}:block/{name}{suffix}_{kind}"}
        if rot:
            v["y"] = rot
        variants[shape] = v
    return variants


WOOD = {"ballast": f"{NS}:block/ballast", "sleeper": f"{NS}:block/sleeper_wood", "rail": f"{NS}:block/rail_steel", "head": f"{NS}:block/rail_head"}
CONCRETE = {"ballast": f"{NS}:block/ballast", "sleeper": f"{NS}:block/sleeper_concrete", "rail": f"{NS}:block/rail_steel", "head": f"{NS}:block/rail_head"}

for name, tex in (("track", WOOD), ("high_speed_track", CONCRETE)):
    rail_models(name, tex)
    blockstate(name, {"variants": {f"shape={k}": v for k, v in rail_variants(name).items()}})
    generated_item(name, f"{NS}:block/{name}")

rail_models("electrified_track", CONCRETE, f"{NS}:block/copper_off")
rail_models("electrified_track_on", CONCRETE, f"{NS}:block/copper_on")
variants = {}
for powered, suffix in (("false", ""), ("true", "_on")):
    for shape, v in rail_variants("electrified_track", True, suffix).items():
        variants[f"powered={powered},shape={shape}"] = v
blockstate("electrified_track", {"variants": variants})
generated_item("electrified_track", f"{NS}:block/electrified_track")

rail_models("station_track", CONCRETE, f"{NS}:block/marker_red", curves=False)
rail_models("station_track_on", CONCRETE, f"{NS}:block/marker_green", curves=False)
variants = {}
for powered, suffix in (("false", ""), ("true", "_on")):
    for shape, v in rail_variants("station_track", False, suffix).items():
        variants[f"powered={powered},shape={shape}"] = v
blockstate("station_track", {"variants": variants})
generated_item("station_track", f"{NS}:block/station_track")

# ------------------------------------------------------------------ blocs décoratifs
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}


def facing_state(name):
    blockstate(name, {"variants": {f"facing={f}": ({"model": f"{NS}:block/{name}", "y": r} if r else {"model": f"{NS}:block/{name}"})
                                   for f, r in FACINGS.items()}})


def block_item(name):
    item_def(name, f"{NS}:block/{name}")


block_model("substation", {"parent": "minecraft:block/cube_column",
                           "textures": {"end": f"{NS}:block/substation_top", "side": f"{NS}:block/substation_side"}})
blockstate("substation", {"variants": {"": {"model": f"{NS}:block/substation"}}})
block_item("substation")

block_model("platform", {"parent": "minecraft:block/block", "textures": {
    "particle": f"{NS}:block/platform_side", "top": f"{NS}:block/platform_top", "side": f"{NS}:block/platform_side",
    "bottom": f"{NS}:block/platform_side"},
    "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
        "down": {"texture": "#bottom", "cullface": "down"}, "up": {"texture": "#top", "cullface": "up"},
        "north": {"texture": "#side", "cullface": "north"}, "south": {"texture": "#side", "cullface": "south"},
        "west": {"texture": "#side", "cullface": "west"}, "east": {"texture": "#side", "cullface": "east"}}}]})
facing_state("platform")
block_item("platform")


def box(fr, to, tex, uv=None):
    faces = {}
    for f in ("north", "south", "east", "west", "up", "down"):
        faces[f] = {"texture": tex, "uv": [0, 0, 16, 16]}
    return {"from": fr, "to": to, "faces": faces}


block_model("catenary_mast", {"parent": "minecraft:block/block", "textures": {
    "particle": f"{NS}:block/catenary_mast", "mast": f"{NS}:block/catenary_mast", "insulator": f"{NS}:block/insulator"},
    "elements": [
        box([6, 0, 6], [10, 16, 10], "#mast"),
        box([7, 13, -8], [9, 15, 6], "#mast"),
        box([7.5, 9, 7], [8.5, 13.5, 8], "#mast"),
        box([6.5, 11, -6], [9.5, 13, -3], "#insulator"),
        box([7.5, 7, -7.5], [8.5, 13, -6.5], "#mast"),
    ]})
facing_state("catenary_mast")
block_item("catenary_mast")

block_model("buffer_stop", {"parent": "minecraft:block/block", "textures": {
    "particle": f"{NS}:block/dark_steel", "steel": f"{NS}:block/dark_steel", "red": f"{NS}:block/buffer_red",
    "stripes": f"{NS}:block/hazard_stripes"},
    "elements": [
        box([2, 0, 6], [4, 10, 14], "#steel"),
        box([12, 0, 6], [14, 10, 14], "#steel"),
        box([1, 6, 3], [15, 11, 6], "#stripes"),
        box([2, 7, 1], [5, 10, 3], "#red"),
        box([11, 7, 1], [14, 10, 3], "#red"),
        box([1, 0, 12], [15, 3, 15], "#steel"),
        box([7, 11, 4], [9, 14, 6], "#red"),
    ]})
facing_state("buffer_stop")
block_item("buffer_stop")

# ------------------------------------------------------------------ objets
VEHICLES = ["steam_locomotive", "orient_express_locomotive", "diesel_locomotive", "electric_locomotive",
            "tgv_power_car", "tgv_orange_power_car", "eurostar_power_car", "ice_power_car", "shinkansen_head",
            "passenger_coach", "sleeper_car", "dining_car", "lounge_car", "luxury_car", "observation_car", "baggage_car",
            "post_car", "orient_express_sleeper", "orient_express_dining", "orient_express_salon", "orient_express_baggage",
            "tgv_car", "tgv_bar_car", "tgv_duplex_car", "tgv_orange_car", "eurostar_car", "ice_car", "shinkansen_car",
            "shinkansen_green_car", "tender", "freight_wagon", "tank_wagon", "hopper_wagon", "container_wagon", "log_wagon",
            "livestock_wagon", "caboose"]
for name in ["steel_ingot", "wheel_set", "boiler", "electric_motor", "pantograph", "train_seat", "coupler", "fuel_canister"] + VEHICLES:
    generated_item(name, f"{NS}:item/{name}")

# ------------------------------------------------------------------ butins & tags
BLOCKS = ["track", "high_speed_track", "electrified_track", "station_track", "substation", "catenary_mast", "buffer_stop", "platform"]
for b in BLOCKS:
    write(os.path.join(DATA, NS, "loot_table", "blocks", b + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{NS}:{b}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": f"{NS}:blocks/{b}"})

RAILS = [f"{NS}:{b}" for b in ("track", "high_speed_track", "electrified_track", "station_track")]
write(os.path.join(DATA, "minecraft", "tags", "block", "rails.json"), {"replace": False, "values": RAILS})
write(os.path.join(DATA, "minecraft", "tags", "item", "rails.json"), {"replace": False, "values": RAILS})
write(os.path.join(DATA, "minecraft", "tags", "block", "mineable", "pickaxe.json"), {"replace": False, "values": [f"{NS}:{b}" for b in BLOCKS]})

# ------------------------------------------------------------------ recettes


def shaped(name, pattern, key, result, count=1):
    write(os.path.join(DATA, NS, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": key,
        "result": {"id": result if ":" in result else f"{NS}:{result}", "count": count}})


S = f"{NS}:steel_ingot"
write(os.path.join(DATA, NS, "recipe", "steel_ingot_from_blasting.json"), {
    "type": "minecraft:blasting", "category": "misc", "ingredient": "minecraft:iron_ingot",
    "result": {"id": S}, "experience": 0.7, "cookingtime": 100})
write(os.path.join(DATA, NS, "recipe", "steel_ingot_from_smelting.json"), {
    "type": "minecraft:smelting", "category": "misc", "ingredient": "minecraft:iron_ingot",
    "result": {"id": S}, "experience": 0.7, "cookingtime": 400})

shaped("wheel_set", [" S ", "SIS", " S "], {"S": S, "I": "minecraft:iron_ingot"}, "wheel_set", 2)
shaped("boiler", ["SSS", "SFS", "SBS"], {"S": S, "F": "minecraft:furnace", "B": "minecraft:bucket"}, "boiler")
shaped("electric_motor", ["CRC", "SIS", "CRC"], {"C": "minecraft:copper_ingot", "R": "minecraft:redstone", "S": S, "I": "minecraft:iron_ingot"}, "electric_motor")
shaped("pantograph", ["CCC", " S ", "S S"], {"C": "minecraft:copper_ingot", "S": S}, "pantograph")
shaped("train_seat", ["W  ", "WWW", "S S"], {"W": "#minecraft:wool", "S": S}, "train_seat", 2)
shaped("coupler", [" S ", "SLS", " S "], {"S": S, "L": "minecraft:lead"}, "coupler")

shaped("track", ["S S", "SCS", "S S"], {"S": S, "C": "minecraft:stone"}, "track", 16)
shaped("high_speed_track", ["S S", "SCS", "S S"], {"S": S, "C": "minecraft:smooth_stone"}, "high_speed_track", 12)
shaped("electrified_track", ["TCT", "TRT", "TCT"], {"T": f"{NS}:high_speed_track", "C": "minecraft:copper_ingot", "R": "minecraft:redstone"}, "electrified_track", 6)
shaped("station_track", ["TPT", "TRT", "TPT"], {"T": f"{NS}:track", "P": "minecraft:heavy_weighted_pressure_plate", "R": "minecraft:redstone"}, "station_track", 6)
shaped("substation", ["SCS", "CRC", "SCS"], {"S": S, "C": "minecraft:copper_ingot", "R": "minecraft:redstone_block"}, "substation")
shaped("catenary_mast", ["SSC", "S  ", "S  "], {"S": S, "C": "minecraft:copper_ingot"}, "catenary_mast", 4)
shaped("buffer_stop", ["RYR", "S S", "SSS"], {"R": "minecraft:red_dye", "Y": "minecraft:yellow_dye", "S": S}, "buffer_stop")
shaped("platform", ["YYY", "CCC", "CCC"], {"Y": "minecraft:yellow_dye", "C": "minecraft:smooth_stone"}, "platform", 8)

W = f"{NS}:wheel_set"
shaped("steam_locomotive", ["  C", "BBS", "WWW"], {"C": "minecraft:iron_bars", "B": f"{NS}:boiler", "S": S, "W": W}, "steam_locomotive")
shaped("tender", ["SCS", "SCS", "W W"], {"S": S, "C": "minecraft:chest", "W": W}, "tender")
shaped("passenger_coach", ["PGP", "TTT", "W W"], {"P": "#minecraft:planks", "G": "minecraft:glass_pane", "T": f"{NS}:train_seat", "W": W}, "passenger_coach")
shaped("freight_wagon", ["PPP", "PCP", "W W"], {"P": "#minecraft:planks", "C": "minecraft:chest", "W": W}, "freight_wagon")
P, G, T, M, PA = "#minecraft:planks", "minecraft:glass_pane", f"{NS}:train_seat", f"{NS}:electric_motor", f"{NS}:pantograph"
WC, BC, OC, YC, RC = "minecraft:white_concrete", "minecraft:blue_concrete", "minecraft:orange_concrete", "minecraft:yellow_concrete", "minecraft:red_concrete"
shaped("fuel_canister", [" S ", "SBS", "SCS"], {"S": S, "B": "minecraft:bucket", "C": "#minecraft:coals"}, "fuel_canister", 4)
# Locomotives
for name, colour in (("tgv_power_car", BC), ("tgv_orange_power_car", OC), ("eurostar_power_car", YC), ("ice_power_car", RC), ("shinkansen_head", WC)):
    shaped(name, ["GPC", "MSM", "W W"], {"G": G, "P": PA, "C": colour, "M": M, "S": S, "W": W}, name)
shaped("electric_locomotive", ["CPC", "MSM", "W W"], {"C": RC, "P": PA, "M": M, "S": S, "W": W}, "electric_locomotive")
shaped("orient_express_locomotive", ["OCO", "BBS", "WWW"], {"O": "minecraft:gold_ingot", "C": "minecraft:iron_bars", "B": f"{NS}:boiler", "S": S, "W": W}, "orient_express_locomotive")
shaped("diesel_locomotive", ["YGY", "PSP", "W W"], {"Y": YC, "G": G, "P": "minecraft:piston", "S": S, "W": W}, "diesel_locomotive")
# Voitures voyageurs
def coach(name, top, mid, extra=None):
    key = {"W": W}
    for ch, item in (extra or {}).items():
        key[ch] = item
    shaped(name, [top, mid, "W W"], key, name)
coach("passenger_coach", "PGP", "TTT", {"P": P, "G": G, "T": T})
coach("sleeper_car", "PGP", "BBB", {"P": P, "G": G, "B": "#minecraft:beds"})
coach("dining_car", "PGP", "TKT", {"P": P, "G": G, "T": T, "K": "minecraft:smoker"})
coach("lounge_car", "PGP", "TLT", {"P": P, "G": G, "T": T, "L": "minecraft:flower_pot"})
coach("luxury_car", "PGP", "TOT", {"P": P, "G": G, "T": T, "O": "minecraft:gold_ingot"})
coach("observation_car", "GGG", "TTT", {"G": "minecraft:glass", "T": T})
coach("baggage_car", "PPP", "CXC", {"P": P, "C": "minecraft:chest", "X": "minecraft:barrel"})
coach("post_car", "YPY", "CQC", {"Y": "minecraft:yellow_dye", "P": P, "C": "minecraft:chest", "Q": "minecraft:paper"})
coach("orient_express_sleeper", "LGL", "BOB", {"L": "minecraft:blue_wool", "G": G, "B": "#minecraft:beds", "O": "minecraft:gold_ingot"})
coach("orient_express_dining", "LGL", "TKO", {"L": "minecraft:blue_wool", "G": G, "T": T, "K": "minecraft:smoker", "O": "minecraft:gold_ingot"})
coach("orient_express_salon", "LGL", "TNT", {"L": "minecraft:blue_wool", "G": G, "T": T, "N": "minecraft:note_block"})
coach("orient_express_baggage", "LLL", "CXC", {"L": "minecraft:blue_wool", "C": "minecraft:chest", "X": "minecraft:barrel"})
coach("tgv_car", "BGB", "TTT", {"B": BC, "G": G, "T": T})
coach("tgv_bar_car", "BGB", "TXT", {"B": BC, "G": G, "T": T, "X": "minecraft:glass_bottle"})
shaped("tgv_duplex_car", ["BGB", "TTT", "WBW"], {"B": BC, "G": G, "T": T, "W": W}, "tgv_duplex_car")
coach("tgv_orange_car", "OGO", "TTT", {"O": OC, "G": G, "T": T})
coach("eurostar_car", "CGY", "TTT", {"C": WC, "G": G, "Y": YC, "T": T})
coach("ice_car", "CGR", "TTT", {"C": WC, "G": G, "R": RC, "T": T})
coach("shinkansen_car", "CGC", "TTT", {"C": WC, "G": G, "T": T})
coach("shinkansen_green_car", "CGE", "TTT", {"C": WC, "G": G, "E": "minecraft:green_dye", "T": T})
# Marchandises
coach("tank_wagon", "SBS", "SBS", {"S": S, "B": "minecraft:bucket"})
coach("hopper_wagon", "S S", "SHS", {"S": S, "H": "minecraft:hopper"})
coach("container_wagon", "XXX", "SSS", {"X": "minecraft:barrel", "S": S})
coach("log_wagon", "L L", "LLL", {"L": "#minecraft:logs"})
coach("livestock_wagon", "FPF", "PHP", {"F": "#minecraft:wooden_fences", "P": P, "H": "minecraft:hay_block"})
coach("caboose", "RGR", "PFP", {"R": RC, "G": G, "P": P, "F": "minecraft:furnace"})
print("données générées")
