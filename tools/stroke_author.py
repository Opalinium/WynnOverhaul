import math
import sys
from pathlib import Path

import numpy as np
import yaml
from scipy.optimize import least_squares

sys.path.insert(0, str(Path(__file__).resolve().parent))
import bake_offhand
import pose_render as pr
import pose_yaml

GRIP_VEC = np.array([0.0, pr.GRIP_Y, pr.GRIP_Z])
ARM_LEN = float(np.linalg.norm(GRIP_VEC))
HIP_FOLLOW = 0.45
ROLL_SAMPLES = 48
ROLL_SPEED_FLOOR = 0.15
FEET_CHANNELS = ("footLF", "footRF")


SFX = {
    "sweep_heavy": [(0.35, "minecraft:entity.player.attack.sweep", 0.75, 0.85), (1.0, "minecraft:entity.player.attack.strong", 0.9, 0.35)],
    "sweep": [(0.3, "minecraft:entity.player.attack.sweep", 0.9, 0.6)],
    "chop": [(0.3, "minecraft:entity.player.attack.sweep", 0.8, 0.5), (1.0, "minecraft:entity.player.attack.crit", 0.9, 0.3)],
    "quick": [(0.3, "minecraft:entity.player.attack.sweep", 1.5, 0.5)],
    "stab": [(0.3, "minecraft:entity.player.attack.sweep", 1.7, 0.45)],
    "cast": [(0.4, "minecraft:entity.wind_charge.throw", 1.6, 0.35), (1.0, "minecraft:block.amethyst_block.resonate", 1.7, 0.35)],
    "relik": [(0.4, "minecraft:entity.player.attack.sweep", 0.9, 0.5), (1.0, "minecraft:block.amethyst_block.resonate", 0.9, 0.35)],
}


def rad(deg):
    return math.radians(deg)


def aim(az, el):
    a, e = rad(az), rad(el)
    return np.array([math.cos(e) * math.sin(a), -math.sin(e), -math.cos(e) * math.cos(a)])


def fit_arm(target, guess, left=False):
    shoulder_x = 5.0 if left else -5.0
    target = target / (np.linalg.norm(target) + 1e-9) * ARM_LEN

    def residual(p):
        m = pr.rot_zyx(-p[2], p[1], p[0]) if left else pr.rot_zyx(p[2], -p[1], p[0])
        g = m @ GRIP_VEC
        depth = 0.0
        for f in pr.GUARD_POINTS:
            q = m @ np.array([0.0, pr.GRIP_Y * f, pr.GRIP_Z * f]) + np.array([shoulder_x, 2.0, 0.0])
            d = min(pr.BODY_HALF_WIDTH + 0.6 - abs(q[0]), q[2] - pr.BODY_FRONT + 0.6, q[1] + 0.6, pr.BODY_BOTTOM - q[1])
            if d > 0:
                depth += d
        return np.concatenate([g - target, [0.12 * (p[2] - guess[2]), 0.45 * depth]])

    return least_squares(residual, guess, bounds=([-3.6, -2.5, -1.8], [1.2, 2.5, 2.2]), diff_step=0.02).x


def stance_values(stances, key):
    carry, ready, offset = stances[key]
    return ready, offset


class Author:
    def __init__(self, lib):
        self.lib = lib
        self.stances = pr.load_stances(lib)

    def hand_default(self, az, el):
        return 0.55 * max(-100.0, min(100.0, az)), 0.5 * el - 6.0

    def build(self, key, spec):
        base_key = spec["weapon"]
        ready, offset = stance_values(self.stances, base_key)
        offset = float(spec.get("gripOffset", offset))
        two = bool(spec.get("twoHanded", False))
        edge = bool(spec.get("edge", True))
        path = spec["path"]
        times = [0.0] + [float(p["t"]) for p in path] + [1.0]
        rows = []
        guess = np.array([ready[pr.PITCH], ready[pr.ACROSS], ready[pr.ABDUCT]])
        for p in path:
            az, el = float(p["az"]), float(p["el"])
            hand = p.get("hand") or self.hand_default(az, el)
            guess = fit_arm(aim(hand[0], hand[1]), guess)
            row = ready.copy()
            row[pr.PITCH], row[pr.ACROSS], row[pr.ABDUCT] = guess
            row[pr.HEAD_Y] = rad(az)
            row[pr.HEAD_A] = -rad(el)
            row[pr.TURN] = float(p.get("turn", 0.0))
            row[pr.LEAN] = float(p.get("lean", 0.0))
            row[pr.REACH] = float(p.get("reach", 0.0))
            row[pr.BODY_Y] = float(p.get("bodyY", 0.0))
            row[pr.HIPS_Y] = float(p.get("hip", HIP_FOLLOW * row[pr.TURN]))
            row[pr.ROOT_H] = float(p.get("down", 0.0))
            row[pr.TWIST] = rad(float(p.get("twist", 0.0)))
            lunge = float(p.get("lunge", 0.0))
            row[pr.FOOT_RF] = lunge
            row[pr.FOOT_LF] = -0.5 * lunge
            row[pr.ROOT_F] = 0.3 * lunge
            off = p.get("off")
            if off is not None:
                row[pr.OFF_PITCH], row[pr.OFF_ACROSS], row[pr.OFF_ABDUCT] = [rad(v) for v in off]
            else:
                row[pr.OFF_ACROSS] = ready[pr.OFF_ACROSS] + 0.35 * row[pr.TURN]
                row[pr.OFF_PITCH] = ready[pr.OFF_PITCH] - 0.25 * abs(row[pr.TURN])
            row[pr.GRIP] = 1.0 if two else 0.0
            rows.append(row)
        rows = np.array(rows)
        if two:
            for i, row in enumerate(rows):
                row[pr.OFF_PITCH], row[pr.OFF_ACROSS], row[pr.OFF_ABDUCT] = bake_offhand.grip_left(row, offset, 1.0)
        if edge and not any("twist" in p for p in path):
            rows[:, pr.TWIST] = self.edge_roll(rows, times[1:-1], offset, base_key, spec)
        return self.emit(key, spec, rows, times)

    def tip_path(self, rows, offset, base_key):
        wl = pr.WEAPON_LEN.get(base_key, (4, 14))
        tips = []
        solved = []
        for row in rows:
            yaw, right, left, clipped, head, body, front, back, lean = pr.solve(row, offset)
            grip = right.matrix() @ np.array([0, pr.GRIP_Y, pr.GRIP_Z]) + np.array([right.x, right.y, right.z])
            cos_a = math.cos(row[pr.HEAD_A])
            shaft = pr.rot_y(yaw) @ np.array([math.sin(row[pr.HEAD_Y]) * cos_a, math.sin(row[pr.HEAD_A]), -math.cos(row[pr.HEAD_Y]) * cos_a])
            tips.append(grip + shaft * wl[1])
            solved.append((right, shaft))
        return np.array(tips), solved

    def edge_roll(self, rows, times, offset, base_key, spec):
        grid = np.linspace(times[0], times[-1], ROLL_SAMPLES)
        dense = np.array([self.sample_rows(rows, times, t) for t in grid])
        tips, solved = self.tip_path(dense, offset, base_key)
        velocity = np.gradient(tips, axis=0)
        speed = np.linalg.norm(velocity, axis=1)
        peak = speed.max() + 1e-9
        rolls = np.zeros(len(grid))
        previous = 0.0
        for i, (right, shaft) in enumerate(solved):
            if speed[i] < ROLL_SPEED_FLOOR * peak:
                rolls[i] = previous
                continue
            v = velocity[i] / speed[i]
            best, best_cost = previous, 1e9
            for guess in np.linspace(-math.pi, math.pi, 73):
                normal, width = pr.blade_axes(right, shaft, guess)
                cost = abs(float(np.dot(normal, v))) + 0.04 * abs(self.wrap(guess - previous))
                if cost < best_cost:
                    best, best_cost = guess, cost
            previous = previous + self.wrap(best - previous)
            rolls[i] = previous
        out = np.interp(times, grid, rolls)
        limit = float(spec.get("rollLimit", 1.6))
        out = np.clip(out, -limit, limit)
        return out

    @staticmethod
    def wrap(a):
        return (a + math.pi) % (2 * math.pi) - math.pi

    @staticmethod
    def sample_rows(rows, times, t):
        out = np.zeros(rows.shape[1])
        for c in range(rows.shape[1]):
            out[c] = float(np.interp(t, times, rows[:, c]))
        return out

    def emit(self, key, spec, rows, times):
        pose = {"label": spec.get("label", key), "seconds": spec["seconds"], "hit": spec["hit"]}
        for field in ("recover", "blend", "gripOffset", "spin"):
            if field in spec:
                pose[field] = spec[field]
        if spec.get("sfx"):
            pose["sounds"] = [{"at": round(spec["hit"] * f, 3), "sound": sound, "pitch": pitch, "volume": volume} for f, sound, pitch, volume in SFX[spec["sfx"]]]
        elif spec.get("sounds"):
            pose["sounds"] = spec["sounds"]
        ready, offset = stance_values(self.stances, spec["weapon"])
        channels = {}
        names = {
            "pitch": pr.PITCH, "across": pr.ACROSS, "abduct": pr.ABDUCT, "offPitch": pr.OFF_PITCH, "offAcross": pr.OFF_ACROSS, "offAbduct": pr.OFF_ABDUCT,
            "turn": pr.TURN, "hipY": pr.HIPS_Y, "reach": pr.REACH, "headA": pr.HEAD_A, "headY": pr.HEAD_Y, "twist": pr.TWIST, "lean": pr.LEAN,
            "bodyY": pr.BODY_Y, "rootH": pr.ROOT_H, "footLF": pr.FOOT_LF, "footRF": pr.FOOT_RF, "rootF": pr.ROOT_F,
        }
        for name, index in names.items():
            column = rows[:, index]
            if name in ("reach", "bodyY", "rootH", "footLF", "footRF", "rootF") and np.allclose(column, 0.0):
                continue
            if name == "twist" and not spec.get("edge", True) and np.allclose(column, 0.0):
                continue
            pts = [[0, None]] + [[round(t, 4), round(float(v), 3)] for t, v in zip(times[1:-1], column)] + [[1, None]]
            channels[name] = pts
        pose["channels"] = channels
        return pose


def generate(spec_path, src):
    lib = pr.load_library(src)
    author = Author(lib)
    spec = yaml.safe_load(Path(spec_path).read_text(encoding="utf-8"))
    return {key: author.build(key, s) for key, s in spec["strokes"].items()}


def merge(spec_path, target, src=None):
    src = src or pr.SRC
    generated = generate(spec_path, src)
    spec = yaml.safe_load(Path(spec_path).read_text(encoding="utf-8"))["strokes"]
    doc = yaml.safe_load(Path(target).read_text(encoding="utf-8"))
    trails = doc.setdefault("trails", {})
    lib = pr.load_library(src)
    known = {}
    for path in sorted(Path(src).glob("*.yml")):
        known.update((yaml.safe_load(path.read_text(encoding="utf-8")) or {}).get("trails") or {})
    for key, pose in generated.items():
        doc["poses"][key] = pose
        base = spec[key]["weapon"]
        if base in known:
            trails[key] = list(known[base])
    Path(target).write_text(pose_yaml.document(doc), encoding="utf-8")
    return len(generated)


if __name__ == "__main__":
    if len(sys.argv) > 3 and sys.argv[3] == "merge":
        print(merge(sys.argv[1], sys.argv[2]), "strokes merged")
    else:
        out = generate(sys.argv[1], pr.SRC)
        Path(sys.argv[2]).write_text(pose_yaml.document({"poses": out}), encoding="utf-8")
        print(len(out), "strokes")
