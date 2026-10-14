import math
import sys
from pathlib import Path

import numpy as np
import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pose_render as pr

OUT = pr.SRC / "spell_moves.yml"
SAMPLES = 32
TOLERANCE = 0.035
TWO_PI = 2 * math.pi
ZERO_DEFAULT = {pr.TURN, pr.REACH, pr.TWIST}
CHANNEL_NAMES = pr.CHANNEL_NAMES

lib = pr.load_library(pr.SRC)
curves = pr.load_poses(lib)
stances = pr.load_stances(lib)
raw_poses = lib["poses"]
raw_docs = [yaml.safe_load(p.read_text(encoding="utf-8")) or {} for p in sorted(pr.SRC.glob("*.yml")) if p.name != OUT.name]
trails = {}
for doc in raw_docs:
    trails.update(doc.get("trails") or {})
movesets = lib["movesets"]
mirrored = {}


def smooth(x):
    c = min(1.0, max(0.0, x))
    return c * c * (3 - 2 * c)


def bump(u, centre, width):
    return math.exp(-(((u - centre) / width) ** 2))


def info(moveset):
    entry = movesets[moveset]
    stance_key = entry.get("stance") or (moveset if moveset in stances else moveset.split(":")[0])
    ready = stances[stance_key][1]
    chain = [(s["pose"], bool(s.get("mirror")), bool(s.get("finisher"))) for s in entry["light"]]
    return stance_key, ready, chain


def stroke_curves(key, mirror):
    if not mirror:
        return curves[key]
    if key not in mirrored:
        mirrored[key] = pr.mirror_curves(curves[key])
    return mirrored[key]


def sample(key, mirror, t, ready):
    return pr.sample_pose(stroke_curves(key, mirror), t, ready, key)


class Seg:
    def __init__(self, u0, u1, key, s0, s1, mirror=False):
        self.u0, self.u1, self.key, self.s0, self.s1, self.mirror = u0, u1, key, s0, s1, mirror

    def source_time(self, u):
        if self.u1 <= self.u0:
            return self.s1
        local = min(1.0, max(0.0, (u - self.u0) / (self.u1 - self.u0)))
        return self.s0 + (self.s1 - self.s0) * local


def render(segs, ready, mods, window=0.035):
    rows = []
    for k in range(SAMPLES + 1):
        u = k / SAMPLES
        total = np.zeros(18)
        weight = 0.0
        for i, seg in enumerate(segs):
            rise = 1.0 if i == 0 else smooth((u - (seg.u0 - window)) / (2 * window))
            fall = 1.0 if i == len(segs) - 1 else 1.0 - smooth((u - (seg.u1 - window)) / (2 * window))
            w = rise * fall
            if w <= 0:
                continue
            total += w * sample(seg.key, seg.mirror, seg.source_time(u), ready)
            weight += w
        v = total / weight if weight > 0 else np.array(ready, dtype=float)
        for mod in mods:
            mod(u, v)
        rows.append(v)
    return rows


def remap_sounds(base_key, hit_u, extra=()):
    base = raw_poses.get(base_key, {})
    base_hit = float(base.get("hit", 0.5))
    out = []
    for s in base.get("sounds") or []:
        at = float(s["at"])
        new = at / base_hit * hit_u if at <= base_hit else hit_u + (at - base_hit) / (1 - base_hit) * (1 - hit_u)
        out.append(dict(s, at=round(min(0.95, max(0.02, new)), 3)))
    for s in extra:
        out.append(dict(s, at=round(min(0.95, hit_u + 0.01), 3)))
    return out


def cast_sounds(moveset):
    return (movesets[moveset].get("cast") or {}).get("addSounds") or []


def simplify(points, tolerance):
    if len(points) <= 2:
        return points
    (t0, v0), (t1, v1) = points[0], points[-1]
    worst, index = 0.0, 0
    for i in range(1, len(points) - 1):
        t, v = points[i]
        line = v0 + (v1 - v0) * (t - t0) / (t1 - t0)
        if abs(v - line) > worst:
            worst, index = abs(v - line), i
    if worst <= tolerance:
        return [points[0], points[-1]]
    return simplify(points[: index + 1], tolerance)[:-1] + simplify(points[index:], tolerance)


def emit(key, rows, seconds, hit, sounds, source, blend=0.12):
    channels = {}
    for i, name in enumerate(CHANNEL_NAMES):
        column = [r[i] for r in rows]
        if i not in ZERO_DEFAULT and max(column) - min(column) < 1e-4:
            continue
        inner = simplify([(k / SAMPLES, float(column[k])) for k in range(1, SAMPLES)], TOLERANCE)
        if i not in ZERO_DEFAULT and max(abs(v - rows[0][i]) for _, v in inner) < TOLERANCE:
            continue
        channels[name] = [[0, None]] + [[round(t, 3), round(v, 2)] for t, v in inner] + [[1, None]]
    base = raw_poses[source]
    pose = {"seconds": round(seconds, 3), "hit": round(hit, 3), "blend": blend, "gripOffset": base.get("gripOffset", 7)}
    for field in ("handShift", "handSprintExtra"):
        if field in base:
            pose[field] = base[field]
    pose["sounds"] = sounds
    pose["channels"] = channels
    return pose


def finisher(chain):
    for key, mirror, fin in reversed(chain):
        if fin:
            return key, mirror
    return chain[-1][0], chain[-1][1]


SHARED = yaml.safe_load((pr.SRC / "shared.yml").read_text(encoding="utf-8"))
SPELL_KEYS = SHARED["spellNames"]


def base_pose_key(name):
    return SPELL_KEYS[name]


def charged(moveset, name, p):
    stance, ready, chain = info(moveset)
    fkey, fm = finisher(chain)
    hit = float(raw_poses[fkey]["hit"])
    apex = hit * p.get("apex", 0.6)
    end_c = min(1.0, hit + (1 - hit) * 0.35)
    rise = p.get("rise", 0.08)
    hold = p.get("hold", 0.02)
    strike = p.get("strike", 0.16)
    u2 = rise + hold
    u3 = u2 + strike
    segs = [
        Seg(0.0, rise, fkey, apex * 0.45, apex, fm),
        Seg(rise, u2, fkey, apex, apex, fm),
        Seg(u2, u3, fkey, apex, end_c, fm),
        Seg(u3, 1.0, fkey, end_c, 1.0, fm),
    ]
    hit_u = u2 + strike * (hit - apex) / (end_c - apex)
    lift = p.get("lift", 0.3)
    impact = p.get("impact", 1.0)
    shake = p.get("shake", 1.0)

    def charge_up(u, v):
        w = smooth(u / rise) * (1 - smooth((u - u2) / 0.06))
        v[pr.PITCH] -= lift * w
        v[pr.OFF_PITCH] -= lift * w
        v[pr.LEAN] -= 0.7 * lift * w
        if u >= hit_u:
            decay = math.exp(-(u - hit_u) * 9)
            tremor = math.sin((u - hit_u) * TWO_PI * 5) * decay
            v[pr.PITCH] += 0.07 * shake * tremor
            v[pr.TWIST] += 0.12 * shake * tremor
            v[pr.BODY_R] += 0.04 * shake * tremor

    def land(u, v):
        b = bump(u, hit_u, 0.07)
        v[pr.LEAN] += 0.2 * impact * b
        v[pr.STEP] += 0.55 * impact * b
        v[pr.REACH] += 0.6 * impact * b

    seconds = max(0.7, min(1.2, raw_poses[fkey]["seconds"] * p.get("tempo", 1.5)))
    return emit_spell(moveset, name, render(segs, ready, [charge_up, land]), seconds, hit_u, fkey)


def dash(moveset, name, p):
    stance, ready, chain = info(moveset)
    (k1, m1, _), (k2, m2, _) = chain[0], chain[1]
    h1 = float(raw_poses[k1]["hit"])
    h2 = float(raw_poses[k2]["hit"])
    run = p.get("run", 1.0)
    direction = p.get("direction", 1.0)
    run_end = p.get("run_end", 0.45)
    segs = [
        Seg(0.0, 0.06, k1, h1 * 0.3, h1 * 0.7, m1),
        Seg(0.06, run_end, k1, h1, h1, m1),
        Seg(run_end, run_end + 0.22, k2, h2 * 0.45, h2, m2),
        Seg(run_end + 0.22, 1.0, k2, h2, 1.0, m2),
    ]
    hit_u = run_end + 0.22

    def sprint(u, v):
        env = smooth(u / 0.06) * (1 - smooth((u - run_end + 0.02) / 0.08))
        v[pr.LEAN] += 0.5 * direction * env
        v[pr.STEP] += 1.0 * direction * env
        phase = math.sin((u - 0.06) * TWO_PI * 3.5)
        v[pr.LEG_L] += 0.95 * run * phase * env
        v[pr.LEG_R] -= 0.95 * run * phase * env
        v[pr.BODY_R] += 0.04 * run * phase * env

    def smash(u, v):
        b = bump(u, hit_u, 0.06)
        v[pr.LEAN] += 0.15 * b
        v[pr.STEP] += 0.4 * b

    seconds = max(0.6, min(1.1, (raw_poses[k1]["seconds"] + raw_poses[k2]["seconds"]) * p.get("tempo", 1.6)))
    return emit_spell(moveset, name, render(segs, ready, [sprint, smash]), seconds, 0.1, k1)


def raise_up(moveset, name, p):
    stance, ready, chain = info(moveset)
    fkey, fm = finisher(chain)
    hit = float(raw_poses[fkey]["hit"])
    apex = hit * p.get("apex", 0.6)
    rise = p.get("rise", 0.1)
    hold_end = p.get("hold_end", 0.7)
    segs = [
        Seg(0.0, rise, fkey, apex * 0.4, apex, fm),
        Seg(rise, hold_end, fkey, apex, apex, fm),
        Seg(hold_end, hold_end + 0.14, fkey, apex, hit, fm),
        Seg(hold_end + 0.14, 1.0, fkey, hit, 1.0, fm),
    ]
    lift = p.get("lift", 0.55)
    arch = p.get("arch", 0.5)
    pulses = p.get("pulses", 3.0)
    slam_at = hold_end + 0.12
    slam = p.get("slam", 1.0)

    def lift_arms(u, v):
        up = smooth(u / rise) * (1 - smooth((u - hold_end) / 0.1))
        v[pr.PITCH] -= lift * up
        v[pr.OFF_PITCH] -= lift * up
        v[pr.LEAN] -= arch * up
        v[pr.HEAD_A] += 0.25 * up
        if rise <= u <= hold_end + 0.02:
            pulse = math.sin((u - rise) * TWO_PI * pulses / (hold_end - rise) * 0.42)
            v[pr.LEAN] += 0.07 * pulse
            v[pr.PITCH] += 0.1 * pulse
            v[pr.OFF_PITCH] += 0.1 * pulse
            v[pr.TWIST] += 0.15 * pulse
            v[pr.BODY_R] += 0.05 * pulse
        b = bump(u, slam_at, 0.05)
        v[pr.STEP] += 0.5 * slam * b
        v[pr.LEAN] += 0.25 * slam * b

    return emit_spell(moveset, name, render(segs, ready, [lift_arms]), p.get("seconds", 1.05), rise, fkey)


def barrage(moveset, name, p):
    stance, ready, chain = info(moveset)
    strokes = chain[: min(len(chain), p.get("count", 3))]
    if chain[-1][2] and chain[-1] not in strokes:
        strokes = strokes + [chain[-1]]
    n = len(strokes)
    each = 0.75 / n
    segs = []
    hit_us = []
    for j, (key, mirror, _) in enumerate(strokes):
        h = float(raw_poses[key]["hit"])
        s0 = h * 0.4
        u0 = j * each
        segs.append(Seg(u0, u0 + each, key, s0, 0.92, mirror))
        hit_us.append(u0 + each * (h - s0) / (0.92 - s0))
    segs.append(Seg(n * each, 1.0, strokes[-1][0], 0.92, 1.0, strokes[-1][1]))
    lunge = p.get("lunge", 0.5)

    def drive(u, v):
        for k, hu in enumerate(hit_us):
            b = bump(u, hu, 0.045)
            v[pr.STEP] += lunge * (0.6 + 0.2 * k) * b
            v[pr.LEAN] += 0.12 * (1 + 0.3 * k) * b

    seconds = max(0.7, min(1.3, sum(raw_poses[k]["seconds"] for k, _, _ in strokes) * p.get("tempo", 1.4)))
    return emit_spell(moveset, name, render(segs, ready, [drive]), seconds, hit_us[0], strokes[0][0])


def leap(moveset, name, p):
    stance, ready, chain = info(moveset)
    k1, m1, _ = chain[0]
    fkey, fm = finisher(chain)
    h1 = float(raw_poses[k1]["hit"])
    hf = float(raw_poses[fkey]["hit"])
    apex = hf * 0.6
    direction = p.get("direction", 1.0)
    coil_end, air_end, land_end = 0.07, 0.62, 0.8
    segs = [
        Seg(0.0, coil_end, k1, h1 * 0.3, h1 * 0.7, m1),
        Seg(coil_end, air_end, k1, h1, h1, m1),
        Seg(air_end, land_end, fkey, apex, hf, fm),
        Seg(land_end, 1.0, fkey, hf, 1.0, fm),
    ]

    def flight(u, v):
        coil = smooth(u / coil_end) * (1 - smooth((u - coil_end) / 0.05))
        air = smooth((u - coil_end + 0.03) / 0.05) * (1 - smooth((u - air_end + 0.03) / 0.07))
        v[pr.LEAN] += direction * (0.35 * coil + 0.9 * air)
        v[pr.STEP] -= 0.4 * coil * direction
        v[pr.LEG_L] += 0.8 * air
        v[pr.LEG_R] += 0.35 * air
        v[pr.REACH] += 0.8 * air
        v[pr.PITCH] -= 0.2 * air
        v[pr.OFF_PITCH] -= 0.2 * air
        drift = math.sin((u - coil_end) * TWO_PI * 1.2)
        v[pr.BODY_R] += 0.05 * drift * air
        v[pr.LEG_R] += 0.12 * drift * air
        drop = bump(u, air_end + 0.05, 0.05)
        v[pr.LEAN] += 0.2 * direction * drop
        b = bump(u, land_end, 0.05)
        v[pr.STEP] += 0.8 * b
        v[pr.LEAN] += 0.3 * b
        v[pr.REACH] += 0.7 * b

    return emit_spell(moveset, name, render(segs, ready, [flight]), p.get("seconds", 1.15), 0.1, fkey)


def spin(moveset, name, p):
    stance, ready, chain = info(moveset)
    strokes = chain[: min(len(chain), 3)]
    each = 0.75 / len(strokes)
    segs = []
    hit_us = []
    for j, (key, mirror, _) in enumerate(strokes):
        h = float(raw_poses[key]["hit"])
        s0 = h * 0.4
        u0 = j * each
        segs.append(Seg(u0, u0 + each, key, s0, 0.95, mirror))
        hit_us.append(u0 + each * (h - s0) / (0.95 - s0))
    segs.append(Seg(len(strokes) * each, 1.0, strokes[-1][0], 0.95, 1.0, strokes[-1][1]))
    turn_amp = p.get("turn", 1.1)

    def whirl(u, v):
        for k, hu in enumerate(hit_us):
            b = bump(u, hu, 0.06)
            sign = 1 if k % 2 == 0 else -1
            v[pr.TURN] += sign * turn_amp * b
            v[pr.BODY_Y] += sign * 0.5 * turn_amp * b
            v[pr.LEAN] += 0.2 * b

    seconds = max(0.8, min(1.3, sum(raw_poses[k]["seconds"] for k, _, _ in strokes) * p.get("tempo", 1.6)))
    return emit_spell(moveset, name, render(segs, ready, [whirl]), seconds, hit_us[0], strokes[0][0])


def emit_spell(moveset, name, rows, seconds, hit_u, source):
    key = f"{base_pose_key(name)}@{moveset}"
    sounds = remap_sounds(base_pose_key(name), hit_u, cast_sounds(moveset))
    return key, emit(key, rows, seconds, hit_u, sounds, source), source


RECIPES = {"charged": charged, "dash": dash, "leap": leap, "raise": raise_up, "barrage": barrage, "spin": spin}

SPELLS = {
    "SPEAR": {
        "bash": ("charged", {"hold": 0.03, "lift": 0.3, "impact": 1.0}),
        "charge": ("leap", {}),
        "warscream": ("raise", {}),
    },
    "DAGGER": {
        "spinattack": ("spin", {}),
        "dash": ("dash", {"tempo": 1.3}),
        "multihit": ("barrage", {"count": 3}),
        "smokebomb": ("charged", {"hold": 0.03, "lift": 0.45, "impact": 0.5, "tempo": 1.6}),
        "lacerate": ("barrage", {"count": 2, "lunge": 0.7, "tempo": 1.6}),
        "backstab": ("dash", {"run": 0.4, "tempo": 1.2, "run_end": 0.4}),
        "bamboozle": ("charged", {"hold": 0.03, "lift": 0.2, "impact": 0.4, "tempo": 1.6, "shake": 2.0}),
    },
    "WAND": {
        "heal": ("raise", {"lift": 0.5, "arch": 0.3, "pulses": 2.0, "slam": 0.3, "seconds": 1.0}),
        "teleport": ("dash", {"run": 0.0, "tempo": 1.0, "run_end": 0.4}),
        "meteor": ("charged", {"hold": 0.03, "lift": 0.5, "impact": 0.6, "tempo": 1.6}),
        "icesnake": ("barrage", {"count": 3, "lunge": 0.3, "tempo": 1.6}),
        "arcanetransfer": ("raise", {"lift": 0.4, "arch": 0.2, "pulses": 4.0, "slam": 0.2, "seconds": 1.1}),
        "ophanim": ("raise", {"lift": 0.6, "arch": 0.4, "pulses": 3.0, "slam": 0.5, "seconds": 1.2}),
    },
    "RELIK": {
        "totem": ("charged", {"hold": 0.03, "lift": 0.35, "impact": 1.2}),
        "haul": ("dash", {"run": 0.3, "tempo": 1.5}),
        "aura": ("raise", {"lift": 0.4, "arch": 0.3, "pulses": 3.0, "slam": 0.4}),
        "uproot": ("charged", {"hold": 0.03, "lift": 0.2, "impact": 1.1, "shake": 2.0}),
        "switchmasks": ("raise", {"lift": 0.3, "arch": 0.2, "pulses": 5.0, "slam": 0.2, "seconds": 0.9}),
    },
    "BOW": {
        "arrowstorm": ("barrage", {"count": 3, "lunge": 0.15, "tempo": 1.7}),
        "escape": ("leap", {"direction": -1.0, "seconds": 1.0}),
        "arrowbomb": ("charged", {"hold": 0.03, "lift": 0.15, "impact": 0.5, "tempo": 1.6}),
        "bombarrow": ("charged", {"hold": 0.03, "lift": 0.15, "impact": 0.5, "tempo": 1.6}),
        "arrowshield": ("raise", {"lift": 0.35, "arch": 0.3, "pulses": 2.0, "slam": 0.4, "seconds": 1.0}),
        "phantomray": ("charged", {"hold": 0.03, "lift": 0.1, "impact": 0.3, "tempo": 1.6}),
        "grapplinghook": ("charged", {"hold": 0.03, "lift": 0.3, "impact": 0.6, "tempo": 1.6}),
        "guardianangels": ("raise", {"lift": 0.5, "arch": 0.4, "pulses": 3.0, "slam": 0.3, "seconds": 1.2}),
    },
}


def build(selected=None):
    poses = {}
    spells = {}
    new_trails = {}
    for moveset in movesets:
        family = moveset.split(":")[0]
        if family not in SPELLS or (selected and moveset not in selected):
            continue
        for name, (recipe, params) in SPELLS[family].items():
            key, pose, source = RECIPES[recipe](moveset, name, params)
            poses[key] = pose
            spells.setdefault(moveset, {})[name] = key
            span = trails.get(source) or trails.get(moveset)
            if span:
                new_trails[key] = span
    return poses, spells, new_trails


def dump(poses, spells, new_trails):
    class Flow(list):
        pass

    def flow_rep(dumper, data):
        return dumper.represent_sequence("tag:yaml.org,2002:seq", data, flow_style=True)

    class Dumper(yaml.SafeDumper):
        pass

    Dumper.add_representer(Flow, flow_rep)
    for pose in poses.values():
        pose["sounds"] = [dict(s) for s in pose["sounds"]]
        for name, points in pose["channels"].items():
            pose["channels"][name] = Flow(Flow(p) for p in points)
    for key, span in new_trails.items():
        new_trails[key] = Flow(span)
    text = yaml.dump({"poses": poses, "movesetSpells": spells, "trails": new_trails}, Dumper=Dumper, sort_keys=False, width=100000, allow_unicode=True)
    return text


if __name__ == "__main__":
    args = sys.argv[1:]
    poses, spells, new_trails = build(args or None)
    OUT.write_text(dump(poses, spells, new_trails), encoding="utf-8")
    print(len(poses), "poses")
