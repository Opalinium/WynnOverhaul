import json
import os
import struct
import sys
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "reference" / "wynn-models"
PREFIX = "assets/minecraft/models/item/wynn/"
KEEP = ("skin/spear/", "skin/dagger/", "skin/wand/", "skin/relik/", "skin/bow/", "weapon/", "tool/", "template/")
FAMILIES = ("spear", "dagger", "wand", "relik", "bow")


def find_pack():
    arg = next((a for a in sys.argv[1:] if not a.startswith("-") and a not in ("extract", "report")), None)
    if arg:
        return Path(arg)
    env = os.environ.get("WYNN_PACK")
    if env:
        return Path(env)
    for base in (Path.home() / "AppData/Roaming/.minecraft", Path("E:/Desktop/PrismLauncher/Instances")):
        for hit in base.rglob("03ff36a54f274ee967b3f5e9fba0f3627c0611a0"):
            return hit
    raise SystemExit("pack not found: pass its path or set WYNN_PACK")


def entries(pack, prefix=PREFIX, keep=KEEP):
    z = zipfile.ZipFile(pack)
    fh = open(pack, "rb")
    for i in z.infolist():
        if not i.filename.startswith(prefix) or not i.filename.endswith(".json"):
            continue
        rel = i.filename[len(prefix):]
        if keep and not rel.startswith(keep):
            continue
        fh.seek(i.header_offset)
        h = fh.read(30)
        fn, ex = struct.unpack("<HH", h[26:30])
        fh.seek(i.header_offset + 30 + fn + ex)
        raw = fh.read(i.compress_size)
        yield rel, (raw if i.compress_type == 0 else zlib.decompress(raw, -15))


def metrics(model, template):
    elements = model.get("elements") or []
    display = dict(template.get("display") or {})
    display.update(model.get("display") or {})
    hand = display.get("thirdperson_righthand") or {}
    out = {
        "parent": model.get("parent"),
        "thirdperson_righthand": hand,
        "firstperson_righthand": display.get("firstperson_righthand"),
    }
    if not elements:
        return out
    lo = [1e9] * 3
    hi = [-1e9] * 3
    shaft = None
    head_lo = 1e9
    head_hi = -1e9
    head_x = 0.0
    head_z = 0.0
    for e in elements:
        f, t = e["from"], e["to"]
        for i in range(3):
            lo[i] = min(lo[i], f[i], t[i])
            hi[i] = max(hi[i], f[i], t[i])
        dx, dy, dz = abs(t[0] - f[0]), abs(t[1] - f[1]), abs(t[2] - f[2])
        if max(dx, dz) <= 3.5 and dy >= 8 and (shaft is None or dy > shaft[1]):
            shaft = (min(f[1], t[1]), dy, max(f[1], t[1]))
        if max(dx, dz) >= 4 and dy <= 14:
            head_lo = min(head_lo, f[1], t[1])
            head_hi = max(head_hi, f[1], t[1])
            head_x = max(head_x, dx)
            head_z = max(head_z, dz)
    scale = (hand.get("scale") or [1.0])[0]
    hand_y = -((hand.get("translation") or [0, 0, 0])[1]) / scale
    out.update(
        {
            "hand_y": round(hand_y, 1),
            "tail_rel": round(hand_y - lo[1], 1),
            "tip_rel": round(hi[1] - hand_y, 1),
            "head_rel": [round(head_lo - hand_y, 1), round(head_hi - hand_y, 1)] if head_hi > head_lo else None,
            "extent": {"x": [lo[0], hi[0]], "y": [lo[1], hi[1]], "z": [lo[2], hi[2]]},
            "length": round(hi[1] - lo[1], 1),
            "shaft_y": [round(shaft[0], 1), round(shaft[2], 1)] if shaft else None,
            "head_y": [round(head_lo, 1), round(head_hi, 1)] if head_hi > head_lo else None,
            "head_width": {"x": round(head_x, 1), "z": round(head_z, 1)},
            "head_axis": "x" if head_x > head_z else "z",
            "elements": len(elements),
        }
    )
    return out


def extract(pack):
    OUT.mkdir(parents=True, exist_ok=True)
    models = {}
    count = 0
    for rel, raw in entries(pack):
        target = OUT / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(raw)
        models[rel[:-5]] = json.loads(raw)
        count += 1
    template = models.get("template/weapon", {})
    index = {name: metrics(m, template) for name, m in sorted(models.items()) if name.startswith(("skin/", "weapon/", "tool/"))}
    (OUT / "index.json").write_text(json.dumps(index, indent=1), encoding="utf-8")
    print(f"{count} models extracted to {OUT}, {len(index)} indexed")


def report():
    index = json.loads((OUT / "index.json").read_text(encoding="utf-8"))
    for family in FAMILIES:
        rows = [(n.split("/")[-1], m) for n, m in index.items() if n.startswith(f"skin/{family}/") and m.get("length")]
        if not rows:
            continue
        print(f"== {family} ({len(rows)} skins)")
        lengths = sorted(m["length"] for _, m in rows)
        print(f"  length min {lengths[0]} median {lengths[len(lengths) // 2]} max {lengths[-1]}")
        heads = [m["head_y"] for _, m in rows if m.get("head_y")]
        if heads:
            print(f"  head y lo {min(h[0] for h in heads)}..{max(h[0] for h in heads)} hi {min(h[1] for h in heads)}..{max(h[1] for h in heads)}")
        widths = sorted(max(m["head_width"].values()) for _, m in rows)
        print(f"  head width min {widths[0]} median {widths[len(widths) // 2]} max {widths[-1]}")
        for name, m in rows:
            h = m["thirdperson_righthand"]
            print(f"  {name:28s} len {m['length']:5.1f} shaft {m['shaft_y']} head {m['head_y']} w {max(m['head_width'].values()):4.1f}{m['head_axis']} rot {h.get('rotation')} scale {h.get('scale')}")


if __name__ == "__main__":
    if "report" in sys.argv:
        report()
    else:
        extract(find_pack())
