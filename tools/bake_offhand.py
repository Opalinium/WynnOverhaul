import math
import re
import sys
from pathlib import Path

import numpy as np
import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pose_render as pr
import pose_yaml

SAMPLES = 30
FREE_HAND = ("SPEAR:GREATSWORD",)


def grip_left(out, offset, weight):
    out = np.pad(np.array(out, dtype=float), (0, max(0, pr.N_CHANNELS - len(out))))
    yaw, right, left, clipped, head, body, front, back, lean = pr.solve(out, offset, guard=False)
    pr.keep_out_of_body(right, -5.0, yaw)
    left.xr = out[pr.OFF_PITCH] + lean
    left.yr = out[pr.OFF_ACROSS] + yaw
    left.zr = -out[pr.OFF_ABDUCT]
    grip_point = right.matrix() @ np.array([0, pr.GRIP_Y, pr.GRIP_Z]) + np.array([right.x, right.y, right.z])
    cos_a = math.cos(out[pr.HEAD_A])
    shaft = np.array([math.sin(out[pr.HEAD_Y]) * cos_a, math.sin(out[pr.HEAD_A]), -math.cos(out[pr.HEAD_Y]) * cos_a])
    shaft = pr.rot_y(yaw) @ shaft
    to = grip_point - np.array([left.x, left.y, left.z])
    b = float(np.dot(to, shaft))
    disc = b * b - (float(np.dot(to, to)) - (pr.GRIP_Y ** 2 + pr.GRIP_Z ** 2))
    if disc >= 0:
        r = math.sqrt(disc)
        near, far = -b - r, -b + r
        along = near if abs(near - offset) < abs(far - offset) else far
    else:
        along = -b
    to = to + along * shaft
    q = pr.rotation_to(np.array([0, pr.GRIP_Y, pr.GRIP_Z]), to)
    ex, ey, ez = pr.euler_zyx(q)
    left.xr += (ex - left.xr) * weight
    left.yr += (ey - left.yr) * weight
    left.zr += (ez - left.zr) * weight
    pr.keep_out_of_body(left, 5.0, yaw)
    return left.xr - lean, left.yr - yaw, -left.zr


def unwrap_to(values, ref):
    out = np.unwrap(np.array(values, dtype=float))
    shift = round((ref - out[0]) / (2 * math.pi)) * 2 * math.pi
    return out + shift


def smooth(values):
    padded = np.pad(values, 2, mode="edge")
    kernel = np.array([1, 2, 3, 2, 1], dtype=float) / 9.0
    return np.convolve(padded, kernel, mode="valid")


def bake_pose(key, pose, stances, stance_key, force=False):
    carry, ready, offset = stances[stance_key]
    offset = float(pose.get("gripOffset", offset))
    curves = pr.load_poses({"poses": {key: pose}})[key]
    times = np.linspace(0.0, 1.0, SAMPLES + 1)
    rows = []
    active = False
    for t in times:
        out = pr.sample_pose(curves, float(t), ready, key)
        weight = 1.0 if force else float(np.clip(out[pr.GRIP], 0.0, 1.0))
        active |= weight > 0.5
        if weight > 0.001:
            rows.append(grip_left(out, offset, weight))
        else:
            rows.append((out[pr.OFF_PITCH], out[pr.OFF_ACROSS], out[pr.OFF_ABDUCT]))
    if not active:
        return None
    rows = np.array(rows)
    result = {}
    for i, name in enumerate(("offPitch", "offAcross", "offAbduct")):
        column = unwrap_to(rows[:, i], rows[0, i])
        column = smooth(column)
        points = [[0, None]] + [[round(float(t), 4), round(float(v), 3)] for t, v in zip(times[1:-1], column[1:-1])] + [[1, None]]
        result[name] = points
    return result


def bake_stance_state(state, offset):
    out = pr.state_array(state)
    weight = float(np.clip(out[pr.GRIP], 0.0, 1.0))
    if weight < 0.5:
        return None
    pitch, across, abduct = grip_left(out, offset, weight)
    return round(float(pitch), 2), round(float(across), 2), round(float(abduct), 2)


STATE_LINE = re.compile(r"^(    )(carry|ready|idle|walk|sprint): (\{.*\})\s*$")


def flow(d):
    parts = []
    for k, v in d.items():
        parts.append(f"{k}: {v:g}" if isinstance(v, (int, float)) and not isinstance(v, bool) else f"{k}: {v}")
    return "{" + ", ".join(parts) + "}"


def bake_stances(path, lib_stances):
    lines = path.read_text(encoding="utf-8").split("\n")
    out = []
    in_stances = False
    current = None
    offset = 7.0
    changed = 0
    for line in lines:
        if re.match(r"^[a-zA-Z]", line):
            in_stances = line.startswith("stances:")
            current = None
        m = re.match(r'^  "([^"]+)":\s*$', line)
        if in_stances and m:
            current = m.group(1)
            offset = float(lib_stances[current].get("gripOffset", 7.0))
        s = STATE_LINE.match(line)
        if in_stances and current and s and not current.startswith(FREE_HAND):
            state = yaml.safe_load(s.group(3))
            baked = bake_stance_state(state, offset)
            if baked:
                state["offPitch"], state["offAcross"], state["offAbduct"] = baked
                state.pop("grip", None)
                line = f"{s.group(1)}{s.group(2)}: {flow(state)}"
                changed += 1
        out.append(line)
    path.write_text("\n".join(out), encoding="utf-8")
    return changed


def main(src):
    src = Path(src)
    lib = pr.load_library(src)
    stances = pr.load_stances(lib)
    for name in ("spear", "dagger", "wand", "relik", "bow", "tool"):
        n = bake_stances(src / f"{name}.yml", lib["stances"])
        print(name, "stance states baked", n)
    lib = pr.load_library(src)
    stances = pr.load_stances(lib)
    for name in ("strokes", "spell_moves"):
        doc = yaml.safe_load((src / f"{name}.yml").read_text(encoding="utf-8"))
        poses = doc["poses"]
        count = 0
        for key, pose in poses.items():
            stance_key = pr.stance_of(key, stances)
            if stance_key not in stances or stance_key.startswith(FREE_HAND):
                continue
            baked = bake_pose(key, pose, stances, stance_key)
            if baked is None:
                continue
            channels = pose["channels"]
            channels.update(baked)
            channels.pop("grip", None)
            count += 1
        print(name, "poses baked", count)
        (src / f"{name}.yml").write_text(pose_yaml.document(doc), encoding="utf-8")


if __name__ == "__main__":
    main(sys.argv[1])
