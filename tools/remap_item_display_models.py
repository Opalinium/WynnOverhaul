import collections
import hashlib
import json
import sys
from pathlib import Path

import wynn_models as w

TARGET = Path(__file__).resolve().parent.parent / "src/client/resources/assets/wynnoverhaul/item_display_models.json"


def load(pack):
    items = dict(w.entries(pack, "assets/minecraft/items/", None))
    tree = json.loads(items["oak_boat.json"])
    mapping = {e["threshold"]: e["model"].get("model") for e in tree["model"]["entries"]}
    models = {rel[:-5]: raw for rel, raw in w.entries(pack, "assets/minecraft/models/", None)}
    return mapping, models


def digest(mapping, models, cmd):
    name = mapping.get(cmd)
    return hashlib.md5(models[name]).hexdigest() if name in models else None


def main(old_pack, new_pack):
    old_map, old_models = load(Path(old_pack))
    new_map, new_models = load(Path(new_pack))
    by_digest = collections.defaultdict(list)
    for cmd in new_map:
        d = digest(new_map, new_models, cmd)
        if d:
            by_digest[d].append(cmd)
    groups = json.loads(TARGET.read_text(encoding="utf-8"))
    out = {}
    for group, ids in groups.items():
        kept = set()
        for cmd in ids:
            old = digest(old_map, old_models, cmd)
            if old is not None and old == digest(new_map, new_models, cmd):
                kept.add(cmd)
            else:
                found = by_digest.get(old, [])
                print(f"{group}: {int(cmd)} -> {[int(f) for f in found]}")
                kept.update(found)
        out[group] = sorted(float(c) for c in kept)
    TARGET.write_text(json.dumps(out, separators=(",", ":")), encoding="utf-8")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
