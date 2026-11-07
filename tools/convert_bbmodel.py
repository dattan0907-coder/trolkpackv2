#!/usr/bin/env python3
"""ModelEngine (.bbmodel) -> GeckoLib (geo.json / animation.json / atlas png).

Bone/variant naming matches TrollModel.java / DecorModel.java of the trollpack mod.
"""
import base64, io, json, os, re, copy, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
BP = HERE + "/source/blueprints"
TEX = HERE + "/source/textures"
OUT = os.path.abspath(HERE + "/../src/main/resources/assets/trollpack")
SKIP = {"hitbox", "hp_bar", "tag_name", "tag_hp", "mount", "seat"}

def load(n):
    return json.load(open(f"{BP}/{n}.bbmodel"))

def num(v):
    try:
        return float(v)
    except Exception:
        return 0.0

# ----------------------------------------------------------------- tree helpers
class Group:
    def __init__(self, d, elems):
        self.name = d["name"]
        self.uuid = d.get("uuid")
        self.origin = [num(x) for x in d.get("origin", [0, 0, 0])]
        self.rot = [num(x) for x in (d.get("rotation") or [0, 0, 0])]
        self.cubes, self.children = [], []
        for c in d.get("children", []):
            if isinstance(c, str):
                e = elems.get(c)
                if e is not None and e.get("type", "cube") == "cube" and e.get("export", True) is not False:
                    self.cubes.append(e)
            else:
                self.children.append(Group(c, elems))

def build_tree(bb):
    elems = {e["uuid"]: e for e in bb["elements"]}
    roots = []
    for o in bb["outliner"]:
        if isinstance(o, str):
            continue
        roots.append(Group(o, elems))
    return roots

def walk(groups):
    for g in groups:
        yield g
        yield from walk(g.children)

def shift(g, d):
    """translate a whole group subtree by d (model units)"""
    g.origin = [g.origin[i] + d[i] for i in range(3)]
    for c in g.cubes:
        c["from"] = [c["from"][i] + d[i] for i in range(3)]
        c["to"] = [c["to"][i] + d[i] for i in range(3)]
        if "origin" in c:
            c["origin"] = [c["origin"][i] + d[i] for i in range(3)]
    for ch in g.children:
        shift(ch, d)

# ----------------------------------------------------------------- atlas
class Atlas:
    def __init__(self, res):
        self.res = res          # project resolution (w,h)
        self.tiles = []         # (name, PIL image)
        self.scale = 1
        self.srcs = []

    def add_source(self, bb):
        """returns mapping texture index -> tile index"""
        m = {}
        for i, t in enumerate(bb["textures"]):
            im = Image.open(io.BytesIO(base64.b64decode(t["source"].split(",", 1)[1]))).convert("RGBA")
            name = t["name"]
            for k, (n, _) in enumerate(self.tiles):
                if n == name:
                    m[i] = k
                    break
            else:
                self.tiles.append((name, im))
                m[i] = len(self.tiles) - 1
            r = im.size[0] / self.res[0]
            self.scale = max(self.scale, int(round(r)) if r >= 1 else 1)
        return m

    def tile_wh(self):
        return self.res[0] * self.scale, self.res[1] * self.scale

    def build(self, use_glow=False):
        tw, th = self.tile_wh()
        atlas = Image.new("RGBA", (tw, th * len(self.tiles)), (0, 0, 0, 0))
        any_glow = False
        for k, (name, im) in enumerate(self.tiles):
            src = im
            if use_glow:
                p = f"{TEX}/{os.path.splitext(name)[0]}_e.png"
                if not os.path.exists(p):
                    continue
                src = Image.open(p).convert("RGBA")
                any_glow = True
            atlas.paste(src.resize((tw, th), Image.NEAREST), (0, k * th))
        return atlas if (not use_glow or any_glow) else None

# ----------------------------------------------------------------- geo
FACES = {"north": "north", "south": "south", "up": "up", "down": "down", "east": "west", "west": "east"}

def cube_json(c, atlas, tmap):
    f, t = c["from"], c["to"]
    size = [t[i] - f[i] for i in range(3)]
    inf = num(c.get("inflate", 0))
    tw, th = atlas.tile_wh()
    S = atlas.scale
    uv = {}
    for face, fd in c["faces"].items():
        if fd.get("texture") is None or "uv" not in fd:
            continue
        k = tmap.get(int(fd["texture"]))
        if k is None:
            continue
        u1, v1, u2, v2 = [num(x) for x in fd["uv"]]
        # project-resolution units -> atlas pixels
        ux = lambda u: u * S * (tw / (atlas.res[0] * S))
        entry = {"uv": [round(u1 * S, 4), round(v1 * S + k * th, 4)],
                 "uv_size": [round((u2 - u1) * S, 4), round((v2 - v1) * S, 4)]}
        uv[FACES[face]] = entry
    o = {"origin": [round(-(f[0] + size[0]) - inf, 5), round(f[1] - inf, 5), round(f[2] - inf, 5)],
         "size": [round(s + inf * 2, 5) for s in size], "uv": uv}
    rot = [num(x) for x in (c.get("rotation") or [0, 0, 0])]
    if any(abs(r) > 1e-6 for r in rot):
        co = c.get("origin", [(f[i] + t[i]) / 2 for i in range(3)])
        o["pivot"] = [round(-co[0], 5), round(co[1], 5), round(co[2], 5)]
        o["rotation"] = [round(-rot[0], 5), round(-rot[1], 5), round(rot[2], 5)]
    return o

def bone_json(g, parent, atlas, tmap):
    b = {"name": g.name, "pivot": [round(-g.origin[0], 5), round(g.origin[1], 5), round(g.origin[2], 5)]}
    if parent:
        b["parent"] = parent
    if any(abs(r) > 1e-6 for r in g.rot):
        b["rotation"] = [round(-g.rot[0], 5), round(-g.rot[1], 5), round(g.rot[2], 5)]
    cubes = [cube_json(c, atlas, tmap_for(c, tmap)) for c in g.cubes]
    if cubes:
        b["cubes"] = cubes
    return b

def tmap_for(c, tmap):
    return tmap[c["_src"]]

def flatten(groups, atlas, parent=None, out=None, skip=SKIP, tm=None):
    out = [] if out is None else out
    for g in groups:
        if g.name in skip:
            continue
        out.append(bone_json(g, parent, atlas, tm))
        flatten(g.children, atlas, g.name, out, skip, tm)
    return out

# ----------------------------------------------------------------- variant alt mapping (troll)
def alt_base(name):
    m = re.fullmatch(r"([012])_(.+)", name)
    if not m:
        return None
    s, r = m.groups()
    if r in ("body", "body_fem"):
        return "body"
    if r == "shield":
        return "left_forearm"
    if r in ("spear", "spear_throw"):
        return "right_hand"
    if r == "left_arm_armor":
        return "left_arm"
    if r == "right_arm_armor":
        return "right_arm"
    if r in ("left_arm", "left_forearm", "left_hand", "right_arm", "right_forearm", "right_hand",
             "left_leg", "left_shin", "left_foot", "right_leg", "right_shin", "right_foot"):
        return r
    if re.fullmatch(r"\d+", r):
        return "h_head"
    return None

def merge_alts(base_roots, part_roots, alt_of):
    """move every alt group of part_roots under its base bone (pivot aligned, rotation made relative)."""
    base = {g.name: g for g in walk(base_roots)}
    alts = []

    def extract(groups):
        keep = []
        for g in groups:
            b = alt_of(g.name)
            # first extract nested alts out of this group
            g.children = extract(g.children)
            if b and b in base:
                alts.append((g, b))
            else:
                keep.append(g)
        return keep

    leftovers = extract(part_roots)
    for g, b in alts:
        B = base[b]
        d = [B.origin[i] - g.origin[i] for i in range(3)]
        rel = [g.rot[i] - B.rot[i] for i in range(3)]
        shift(g, d)
        g.rot = rel
        B.children.append(g)
    return leftovers

# ----------------------------------------------------------------- animations
INTERP = {"linear": "linear", "catmullrom": "catmullrom", "step": "step", "bezier": "linear"}

def conv_val(ch, axis, v):
    """Blockbench -> bedrock sign convention (x flipped for position, x/y for rotation)"""
    neg = (ch == "position" and axis == "x") or (ch == "rotation" and axis in "xy")
    try:
        f = float(v)
        return round(-f if neg else f, 5) + 0.0
    except Exception:
        s = str(v).strip()
        return f"-({s})" if neg and s else (s or 0)

def vec(ch, dp):
    return [conv_val(ch, a, dp.get(a, 0)) for a in "xyz"]

def convert_anims(bb, keep_bones, name_map=None):
    uuid_name = {}
    out = {}
    for a in bb.get("animations", []):
        bones = {}
        for _, an in a.get("animators", {}).items():
            if an.get("type") != "bone":
                continue
            bn = an["name"]
            if bn in SKIP or bn not in keep_bones:
                continue
            chans = {}
            for kf in an.get("keyframes", []):
                ch = kf["channel"]
                if ch not in ("rotation", "position", "scale"):
                    continue
                t = round(num(kf["time"]), 4)
                dps = kf["data_points"]
                if len(dps) >= 2 and ch != "scale" or len(dps) >= 2:
                    val = {"pre": vec(ch, dps[0]), "post": vec(ch, dps[1])}
                else:
                    val = vec(ch, dps[0])
                interp = INTERP.get(kf.get("interpolation", "linear"), "linear")
                if interp == "catmullrom":
                    val = {"post": val, "lerp_mode": "catmullrom"} if not isinstance(val, dict) else {**val, "lerp_mode": "catmullrom"}
                elif interp == "step":
                    val = {"post": val, "lerp_mode": "linear"} if not isinstance(val, dict) else val
                chans.setdefault(ch, {})[f"{t:.4f}".rstrip("0").rstrip(".") if t else "0"] = val
            if chans:
                bones[bn] = chans
        loop = a.get("loop", "once")
        o = {"loop": True if loop == "loop" else ("hold_on_last_frame" if loop == "hold" else False),
             "animation_length": round(num(a.get("length", 1)), 4), "bones": bones}
        out[a["name"]] = o
    return {"format_version": "1.8.0", "animations": out}

# ----------------------------------------------------------------- driver
def write(model_id, geo_roots, atlas, extra_h=6, bb_for_anim=None):
    os.makedirs(f"{OUT}/geo/entity", exist_ok=True)
    os.makedirs(f"{OUT}/textures/entity", exist_ok=True)
    os.makedirs(f"{OUT}/animations/entity", exist_ok=True)
    tw, th = atlas.tile_wh()
    H = th * len(atlas.tiles)
    tm = atlas.tm
    bones = flatten(geo_roots, atlas, None, None, SKIP, tm)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{model_id}", "texture_width": tw, "texture_height": H,
                        "visible_bounds_width": extra_h, "visible_bounds_height": extra_h, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": bones}]}
    json.dump(geo, open(f"{OUT}/geo/entity/{model_id}.geo.json", "w"), separators=(",", ":"))
    atlas.build().save(f"{OUT}/textures/entity/{model_id}.png")
    g = atlas.build(use_glow=True)
    if g is not None:
        g.save(f"{OUT}/textures/entity/{model_id}_glowmask.png")
    names = {b["name"] for b in bones}
    anim = convert_anims(bb_for_anim, names) if bb_for_anim else {"format_version": "1.8.0", "animations": {}}
    json.dump(anim, open(f"{OUT}/animations/entity/{model_id}.animation.json", "w"), separators=(",", ":"))
    print(f"{model_id}: {len(bones)} bones, {sum(len(b.get('cubes', [])) for b in bones)} cubes, atlas {tw}x{H}, anims {list(anim['animations'])}")

def prep(bbs):
    """bbs: list of bbmodel dicts -> atlas, roots per bb (cubes tagged with source)"""
    res = (bbs[0]["resolution"]["width"], bbs[0]["resolution"]["height"])
    at = Atlas(res)
    at.tm = {}
    roots = []
    for i, bb in enumerate(bbs):
        assert (bb["resolution"]["width"], bb["resolution"]["height"]) == res
        at.tm[i] = at.add_source(bb)
        r = build_tree(bb)
        for g in walk(r):
            for c in g.cubes:
                c["_src"] = i
        roots.append(r)
    return at, roots

def simple(model_id, name, parts=None, alt_of=None, h=6):
    bbs = [load(name)] + ([load(parts)] if parts else [])
    at, roots = prep(bbs)
    base = roots[0]
    if parts:
        left = merge_alts(base, roots[1], alt_of)
        base = base + left
    write(model_id, base, at, h, bbs[0])

if __name__ == "__main__":
    simple("troll", "lr_troll", "lr_troll_parts", alt_base, 6)
    simple("troll_elite", "lr_troll_elite")
    simple("troll_voodoo", "lr_troll_voodoo")
    simple("troll_chief", "lr_troll_chief")
    simple("troll_pig", "lr_troll_pig")
    simple("troll_pot", "lr_troll_pot_1", "lr_troll_pots_parts", lambda n: "pot" if n in ("pot2", "pot3") else None)
    simple("troll_totem", "lr_troll_totem")
    simple("troll_bedmat", "lr_troll_bedmat")
    simple("troll_throne", "lr_troll_throne", h=8)
