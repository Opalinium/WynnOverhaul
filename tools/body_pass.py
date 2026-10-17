import math
import sys
from pathlib import Path

import numpy as np
import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent))
import bake_offhand
import pose_render as pr
import pose_yaml

TURN_GAIN = 1.55
TURN_CAP = 1.3
HIP_RATIO = 0.65
BASE_TURN = 0.1

HEAVY = ("SPEAR:GREATSWORD", "SPEAR:GREATAXE", "SPEAR:HAMMER", "SPEAR:AXE")
LIGHT = ("DAGGER", "TOOL")
CASTER = ("WAND", "RELIK")
BOW = ("BOW",)

PARAMS = {
    "heavy": dict(load=1.4, hit=2.2, width=1.5, step=4.5, drag=2.0, shift=1.2, lift=3.0, push=1.6),
    "medium": dict(load=1.1, hit=1.7, width=1.2, step=3.6, drag=1.6, shift=0.9, lift=2.5, push=1.2),
    "light": dict(load=0.8, hit=1.2, width=0.9, step=2.6, drag=1.2, shift=0.7, lift=2.0, push=0.8),
    "caster": dict(load=0.6, hit=0.9, width=0.8, step=1.6, drag=0.9, shift=0.5, lift=1.6, push=0.5),
    "bow": dict(load=0.6, hit=0.8, width=1.1, step=0.0, drag=0.0, shift=0.6, lift=0.0, push=0.3),
}

BEND_CAP = {"heavy": 78.0, "medium": 68.0, "light": 58.0, "caster": 50.0, "bow": 48.0}


def family(key, stance_key):
    name = key.split("@", 1)[0] if key.startswith("SPELL:") else key
    probe = stance_key if key.startswith("SPELL:") else name
    if probe.startswith(HEAVY):
        return "heavy"
    if probe.startswith(CASTER):
        return "caster"
    if probe.startswith(BOW):
        return "bow"
    if probe.startswith(LIGHT):
        return "light"
    return "medium"


def curve_keys(pose, name):
    pts = pose["channels"].get(name)
    return [] if not pts else [float(t) for t, v in pts]


def sampler(key, pose, ready):
    curves = pr.load_poses({"poses": {key: pose}})[key]

    def at(t):
        return pr.sample_pose(curves, float(t), ready, key)

    return at


def is_two_handed(key, pose, stances, stance_key):
    carry, ready, offset = stances[stance_key]
    at = sampler(key, pose, ready)
    wl = pr.WEAPON_LEN.get(stance_key) or pr.WEAPON_LEN.get(stance_key.split(":")[0], (4, 14))
    near = 0
    total = 0
    for t in np.linspace(0.05, 0.95, 10):
        out = at(t)
        yaw, right, left, clipped, head, body, front, back, lean = pr.solve(out, offset, guard=False)
        grip = right.matrix() @ np.array([0, pr.GRIP_Y, pr.GRIP_Z]) + np.array([right.x, right.y, right.z])
        cos_a = math.cos(out[pr.HEAD_A])
        shaft = pr.rot_y(yaw) @ np.array([math.sin(out[pr.HEAD_Y]) * cos_a, math.sin(out[pr.HEAD_A]), -math.cos(out[pr.HEAD_Y]) * cos_a])
        hand = left.matrix() @ np.array([0, pr.GRIP_Y, pr.GRIP_Z]) + np.array([left.x, left.y, left.z])
        along = float(np.clip(np.dot(hand - grip, shaft), -wl[0], wl[1]))
        distance = float(np.linalg.norm(hand - (grip + shaft * along)))
        total += 1
        near += distance < 1.4
    return near >= 0.7 * total


def scale_curve(pose, name, factor, origin=0.0):
    pose["channels"][name] = [[t, None if v is None else round(origin + (v - origin) * factor, 3)] for t, v in pose["channels"][name]]


def rebuild(pose, name, times, values):
    pts = [[0, None]] + [[round(float(t), 4), round(float(v), 3)] for t, v in zip(times, values)] + [[1, None]]
    pose["channels"][name] = pts


def amplify_turn(key, pose, ready, spin):
    channels = pose["channels"]
    if spin or "turn" not in channels:
        return 1.0
    at = sampler(key, pose, ready)
    times = sorted(set(curve_keys(pose, "turn") + curve_keys(pose, "headY") + curve_keys(pose, "across") + curve_keys(pose, "offAcross")))
    times = [t for t in times if 0.0 < t < 1.0]
    if not times:
        return 1.0
    grid = sorted(set(times + [(a + b) / 2 for a, b in zip([0.0] + times, times + [1.0])]))
    grid = [t for t in grid if 0.0 < t < 1.0]
    old = np.array([at(t) for t in grid])
    dev = np.abs(old[:, pr.TURN] - BASE_TURN).max()
    gain = min(TURN_GAIN, TURN_CAP / max(dev, 1e-6))
    if gain <= 1.02:
        return 1.0
    delta = (gain - 1.0) * (old[:, pr.TURN] - BASE_TURN)
    rebuild(pose, "turn", grid, old[:, pr.TURN] + delta)
    rebuild(pose, "headY", grid, old[:, pr.HEAD_Y] - delta)
    rebuild(pose, "across", grid, old[:, pr.ACROSS] - delta)
    rebuild(pose, "offAcross", grid, old[:, pr.OFF_ACROSS] + delta)
    return gain


def footwork(key, pose, ready, stance_key, spin):
    at = sampler(key, pose, ready)
    hit = float(pose["hit"])
    kind = family(key, stance_key)
    p = dict(PARAMS[kind])
    at_hit = at(hit)
    dev = float(at_hit[pr.TURN] - BASE_TURN)
    sigma = 1.0 if dev >= 0 else -1.0
    head_a = float(at_hit[pr.HEAD_A])
    reach = float(at_hit[pr.REACH])
    scale = {"heavy": 1.3, "medium": 1.0, "light": 0.8, "caster": 0.7, "bow": 0.7}[kind]
    if head_a > 0.45:
        p["hit"] += 1.0 * scale
        p["push"] *= 1.3
    if reach < -2.5 or (abs(head_a) < 0.25 and reach < -1.8):
        p["step"] *= 1.35
        p["push"] *= 1.4
        p["load"] += 0.5 * scale
    if head_a < -0.5:
        p["load"] += 0.8 * scale
        p["hit"] = 0.4
    turns = [abs(at(t)[pr.TURN] - BASE_TURN) for t in np.linspace(0, 1, 21)]
    if spin or max(turns) > 1.35:
        p["step"] = 0.0
        p["drag"] = 0.0
        p["shift"] *= 0.3
        p["width"] = max(p["width"], 1.2)
        p["load"] = max(p["load"], 1.2)
        p["hit"] = max(p["hit"], 1.4)
    spell = key.startswith("SPELL:")
    if spell:
        p["step"] *= 0.6
    right_lead = sigma > 0
    out_x = -1.0
    lead_s = -1.0 if right_lead else 1.0
    rear_s = -lead_s
    x_lead = -1.0 if right_lead else 1.0
    t_a = 0.45 * hit
    t_b = 0.8 * hit
    t_c = hit
    t_d = hit + 0.3 * (1 - hit)
    t_e = hit + 0.62 * (1 - hit)
    t_f = hit + 0.85 * (1 - hit)
    times = [t_a, t_b, t_c, t_d, t_e, t_f]
    for i in range(1, len(times)):
        times[i] = max(times[i], times[i - 1] + 0.012)
    times = [min(t, 0.985) for t in times]
    step, drag, width, shift, lift, push = p["step"], p["drag"], p["width"], p["shift"], p["lift"], p["push"]
    load, sink = p["load"], p["hit"]
    root_h = [load, load * 0.92, sink, sink * 0.9, sink * 0.55, sink * 0.2]
    root_s = [-x_lead * shift * 0.8, -x_lead * shift * 0.2, x_lead * shift, x_lead * shift * 0.8, x_lead * shift * 0.3, 0.0]
    root_f = [-0.5 * push, 0.35 * push, push, 0.85 * push, 0.35 * push, 0.0]
    lead_f = [-0.15 * step, 0.35 * step, step, step, 0.7 * step, 0.25 * step]
    lead_u = [0.0, lift, 0.0, 0.0, lift * 0.55, lift * 0.25]
    lead_w = [0.5 * width, 0.8 * width, width, width, 0.8 * width, 0.4 * width]
    rear_f = [0.2 * drag, -0.4 * drag, -drag, -drag, -0.5 * drag, -0.1 * drag]
    rear_u = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]
    rear_w = [0.6 * width, 0.9 * width, width, width, 0.8 * width, 0.4 * width]
    channels = pose["channels"]
    base = {n: float(ready[pr.CHANNEL_NAMES.index(n)]) for n in ("rootH", "rootS", "rootF", "footLF", "footRF", "footLS", "footRS", "footLU", "footRU", "hipY")}
    root_h = [v + base["rootH"] for v in root_h]
    root_s = [v + base["rootS"] for v in root_s]
    root_f = [v + base["rootF"] for v in root_f]

    def feet(prefix_lead, prefix_rear):
        lead_side = lead_s
        rear_side = rear_s
        rebuild(pose, prefix_lead + "F", times, [v + base[prefix_lead + "F"] for v in lead_f])
        rebuild(pose, prefix_lead + "U", times, [v + base[prefix_lead + "U"] for v in lead_u])
        rebuild(pose, prefix_lead + "S", times, [base[prefix_lead + "S"] + lead_side * w for w in lead_w])
        rebuild(pose, prefix_rear + "F", times, [v + base[prefix_rear + "F"] for v in rear_f])
        rebuild(pose, prefix_rear + "U", times, [v + base[prefix_rear + "U"] for v in rear_u])
        rebuild(pose, prefix_rear + "S", times, [base[prefix_rear + "S"] + rear_side * w for w in rear_w])

    if right_lead:
        feet("footR", "footL")
    else:
        feet("footL", "footR")
    rebuild(pose, "rootH", times, root_h)
    rebuild(pose, "rootS", times, root_s)
    rebuild(pose, "rootF", times, root_f)
    grid = sorted(set(curve_keys(pose, "turn")))
    grid = [t for t in grid if 0.0 < t < 1.0]
    values = [base["hipY"] + HIP_RATIO * (float(at(t)[pr.TURN]) - BASE_TURN) for t in grid]
    rebuild(pose, "hipY", grid, values)
    hold = [times[0], times[2], times[3], times[4]]
    cap = BEND_CAP[kind]
    for _ in range(8):
        worst = 0.0
        probe = sampler(key, pose, ready)
        for tt in hold:
            out = probe(tt)
            row = pr.solve(out, 7.0, guard=False)
            worst = max(worst, math.degrees(max(row[6].bend, row[7].bend)))
        if worst <= cap:
            break
        factor = max(0.4, cap / worst)
        scale_curve(pose, "rootH", factor, base["rootH"])
        if worst > cap * 1.05:
            for name in ("footLS", "footRS", "footLF", "footRF"):
                scale_curve(pose, name, 0.88, base[name])
    return kind


def process(key, pose, stances):
    stance_key = pr.stance_of(key, stances)
    if stance_key not in stances or pose.get("bodyPass"):
        return None
    carry, ready, offset = stances[stance_key]
    spin = bool(pose.get("spin"))
    two = is_two_handed(key, pose, stances, stance_key) and not stance_key.startswith(bake_offhand.FREE_HAND)
    gain = amplify_turn(key, pose, ready, spin)
    kind = footwork(key, pose, ready, stance_key, spin)
    if two:
        baked = bake_offhand.bake_pose(key, pose, stances, stance_key, force=True)
        if baked:
            pose["channels"].update(baked)
    pose["bodyPass"] = True
    return kind, gain, two


def main(src):
    src = Path(src)
    lib = pr.load_library(src)
    stances = pr.load_stances(lib)
    for name in ("strokes", "spell_moves", "spell"):
        path = src / f"{name}.yml"
        doc = yaml.safe_load(path.read_text(encoding="utf-8"))
        report = []
        for key, pose in doc["poses"].items():
            lib["poses"][key] = pose
        for key, pose in doc["poses"].items():
            info = process(key, pose, stances)
            if info:
                report.append((key,) + info)
            lib["poses"][key] = pose
        path.write_text(pose_yaml.document(doc), encoding="utf-8")
        print(name, len(report), "poses")
        for row in report:
            print("  %-34s %-7s gain %.2f two %s" % row)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else pr.SRC)
