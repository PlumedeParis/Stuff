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
RAIL_SHAPES = {
    "north_south": ("flat", 0), "east_west": ("flat", 90),
    "ascending_north": ("raised_ne", 0), "ascending_east": ("raised_ne", 90),
    "ascending_south": ("raised_sw", 0), "ascending_west": ("raised_sw", 90),
    "south_east": ("corner", 0), "south_west": ("corner", 90),
    "north_west": ("corner", 180), "north_east": ("corner", 270),
}
TEMPLATES = {"flat": "minecraft:block/rail_flat", "corner": "minecraft:block/rail_curved",
             "raised_ne": "minecraft:block/template_rail_raised_ne", "raised_sw": "minecraft:block/template_rail_raised_sw"}


def rail_models(name, straight_tex, corner_tex):
    for kind, parent in TEMPLATES.items():
        tex = corner_tex if kind == "corner" else straight_tex
        if tex is None:
            continue
        block_model(f"{name}_{kind}", {"parent": parent, "textures": {"rail": f"{NS}:block/{tex}"}})


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


for name, curves in (("track", True), ("high_speed_track", True)):
    rail_models(name, name, name + "_corner")
    blockstate(name, {"variants": {f"shape={k}": v for k, v in rail_variants(name).items()}})
    generated_item(name, f"{NS}:block/{name}")

rail_models("electrified_track", "electrified_track", "electrified_track_corner")
rail_models("electrified_track_on", "electrified_track_on", "electrified_track_corner_on")
variants = {}
for powered, suffix in (("false", ""), ("true", "_on")):
    for shape, v in rail_variants("electrified_track", True, suffix).items():
        variants[f"powered={powered},shape={shape}"] = v
blockstate("electrified_track", {"variants": variants})
generated_item("electrified_track", f"{NS}:block/electrified_track")

rail_models("station_track", "station_track", None)
rail_models("station_track_on", "station_track_on", None)
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
for name in ("steel_ingot", "wheel_set", "boiler", "electric_motor", "pantograph", "train_seat", "coupler",
             "steam_locomotive", "tender", "passenger_coach", "freight_wagon",
             "tgv_power_car", "tgv_car", "shinkansen_head", "shinkansen_car"):
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
shaped("tgv_power_car", ["GPB", "MSM", "W W"], {"G": "minecraft:glass_pane", "P": f"{NS}:pantograph", "B": "minecraft:blue_concrete", "M": f"{NS}:electric_motor", "S": S, "W": W}, "tgv_power_car")
shaped("tgv_car", ["BGB", "TTT", "W W"], {"B": "minecraft:blue_concrete", "G": "minecraft:glass_pane", "T": f"{NS}:train_seat", "W": W}, "tgv_car")
shaped("shinkansen_head", ["GPC", "MSM", "W W"], {"G": "minecraft:glass_pane", "P": f"{NS}:pantograph", "C": "minecraft:white_concrete", "M": f"{NS}:electric_motor", "S": S, "W": W}, "shinkansen_head")
shaped("shinkansen_car", ["CGC", "TTT", "W W"], {"C": "minecraft:white_concrete", "G": "minecraft:glass_pane", "T": f"{NS}:train_seat", "W": W}, "shinkansen_car")
print("données générées")
