import math
import re
import sys
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

SRC = Path(__file__).resolve().parent.parent / "src/client/kotlin/opal/dev/overwatch/client/WeaponAnimations.kt"

PITCH, ACROSS, ABDUCT, OFF_PITCH, OFF_ACROSS, OFF_ABDUCT, TURN, REACH, HEAD_A, HEAD_Y, TWIST, GRIP, LEAN, STEP = range(14)
TURN_BOOST = 1.4
HIP_Y = 12.0
CHANNEL_NAMES = ["pitch", "across", "abduct", "offPitch", "offAcross", "offAbduct", "turn", "reach", "headA", "headY", "twist", "grip", "lean", "step"]
REST_BASE = np.array([-0.3, 0, 0.05, 0, 0, 0.05, 0, 0, -0.3, 0, 0, 0, 0, 0], dtype=float)
GRIP_Y, GRIP_Z = 10.0, -2.0
BODY_HALF_WIDTH, BODY_FRONT, BODY_BOTTOM = 4.5, -4.2, 14.0
GUARD_POINTS = (0.6, 0.85, 1.0)
TWIST_SCALE = 1.0
BLADE_SIDE = -1.0
NAN = float("nan")
SPELL_STANCE = {}
for _k in ("BASH", "CHARGE", "UPPERCUT", "WAR_SCREAM"):
    SPELL_STANCE["SPELL:" + _k] = "SPEAR"
for _k in ("HEAL", "TELEPORT", "METEOR", "ICE_SNAKE", "ARCANE_TRANSFER", "OPHANIM", "CAST"):
    SPELL_STANCE["SPELL:" + _k] = "WAND"
for _k in ("ARROW_STORM", "ESCAPE", "ARROW_BOMB", "ARROW_SHIELD", "PHANTOM_RAY", "GRAPPLING_HOOK", "GUARDIAN_ANGELS"):
    SPELL_STANCE["SPELL:" + _k] = "BOW"
for _k in ("SPIN_ATTACK", "DASH", "MULTIHIT", "SMOKE_BOMB", "LACERATE", "BACKSTAB", "BAMBOOZLE"):
    SPELL_STANCE["SPELL:" + _k] = "DAGGER"
for _k in ("TOTEM", "HAUL", "AURA", "UPROOT", "SWITCH_MASKS"):
    SPELL_STANCE["SPELL:" + _k] = "RELIK"


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def rot_zyx(z, y, x):
    return rot_z(z) @ rot_y(y) @ rot_x(x)


def euler_zyx(m):
    y = -math.asin(max(-1.0, min(1.0, m[2, 0])))
    if abs(m[2, 0]) < 0.999999:
        x = math.atan2(m[2, 1], m[2, 2])
        z = math.atan2(m[1, 0], m[0, 0])
    else:
        x = math.atan2(-m[1, 2], m[1, 1])
        z = 0.0
    return x, y, z


def rotation_to(a, b):
    a = a / np.linalg.norm(a)
    b = b / np.linalg.norm(b)
    v = np.cross(a, b)
    c = float(np.dot(a, b))
    if c < -0.999999:
        axis = np.cross(a, [1, 0, 0])
        if np.linalg.norm(axis) < 1e-6:
            axis = np.cross(a, [0, 1, 0])
        axis /= np.linalg.norm(axis)
        return axis_angle(axis, math.pi)
    k = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + k + k @ k / (1 + c)


def axis_angle(axis, angle):
    x, y, z = axis
    c, s = math.cos(angle), math.sin(angle)
    t = 1 - c
    return np.array([
        [t * x * x + c, t * x * y - s * z, t * x * z + s * y],
        [t * x * y + s * z, t * y * y + c, t * y * z - s * x],
        [t * x * z - s * y, t * y * z + s * x, t * z * z + c],
    ])


class Part:
    def __init__(self, x, y, z):
        self.x, self.y, self.z = x, y, z
        self.xr = self.yr = self.zr = 0.0

    def matrix(self):
        return rot_zyx(self.zr, self.yr, self.xr)


def apply_grip(main, off, side, yaw, head_a, head_y, weight, offset, lean=0.0):
    if weight <= 0.001:
        return
    grip_point = main.matrix() @ np.array([0, GRIP_Y, GRIP_Z]) + np.array([main.x, main.y, main.z])
    cos_a = math.cos(head_a)
    shaft = np.array([side * math.sin(head_y) * cos_a, math.sin(head_a), -math.cos(head_y) * cos_a])
    shaft = rot_y(yaw) @ shaft
    to = grip_point - np.array([off.x, off.y, off.z])
    b = float(np.dot(to, shaft))
    disc = b * b - (float(np.dot(to, to)) - (GRIP_Y ** 2 + GRIP_Z ** 2))
    if disc >= 0:
        r = math.sqrt(disc)
        near, far = -b - r, -b + r
        along = near if abs(near - offset) < abs(far - offset) else far
    else:
        along = -b
    to = to + along * shaft
    grip_dir = np.array([0, GRIP_Y, GRIP_Z])
    q = rotation_to(grip_dir, to)
    ex, ey, ez = euler_zyx(q)
    off.xr += (ex - off.xr) * weight
    off.yr += (ey - off.yr) * weight
    off.zr += (ez - off.zr) * weight


def arm_inside(part, shoulder_x, yaw):
    m = rot_zyx(part.zr, part.yr - yaw, part.xr)
    for f in GUARD_POINTS:
        p = m @ np.array([0, GRIP_Y * f, GRIP_Z * f])
        x = shoulder_x + p[0]
        y = part.y + p[1]
        if abs(x) < BODY_HALF_WIDTH and p[2] > BODY_FRONT and 0 < y < BODY_BOTTOM:
            return True
    return False


def keep_out_of_body(part, shoulder_x, yaw):
    outward = -0.05 if shoulder_x > 0 else 0.05
    for _ in range(9):
        if not arm_inside(part, shoulder_x, yaw):
            return False
        part.xr -= 0.1
        part.yr += outward
    return arm_inside(part, shoulder_x, yaw)


def lean_point(p, yaw, lean):
    v = np.array([p[0], p[1] - HIP_Y, p[2]])
    v = rot_y(yaw) @ rot_x(lean) @ rot_y(-yaw) @ v
    return np.array([v[0], v[1] + HIP_Y, v[2]])


def solve(out, grip_offset=7.0, guard=True):
    s = 1.0
    yaw = -s * out[TURN]
    lean = out[LEAN]
    right = Part(-math.cos(yaw) * 5, 2, math.sin(yaw) * 5)
    left = Part(math.cos(yaw) * 5, 2, -math.sin(yaw) * 5)
    right.xr, right.yr, right.zr = out[PITCH], -out[ACROSS] + yaw, out[ABDUCT]
    left.xr, left.yr, left.zr = out[OFF_PITCH], out[OFF_ACROSS] + yaw, -out[OFF_ABDUCT]
    right.z += out[REACH]
    head = Part(0, 0, 0)
    body = Part(0, 0, 0)
    body.yr = yaw
    if abs(lean) > 0.001:
        for part in (body, head, right, left):
            part.x, part.y, part.z = lean_point((part.x, part.y, part.z), yaw, lean)
        body.xr += lean
        head.xr += lean * 0.3
        right.xr += lean
        left.xr += lean
    clipped = False
    if guard:
        clipped |= keep_out_of_body(right, -5.0, yaw)
    weight = max(0.0, min(1.0, out[GRIP]))
    ox, oy, oz = left.xr, left.yr, left.zr
    for relax in (1.0, 0.8, 0.6, 0.4, 0.2, 0.0):
        left.xr, left.yr, left.zr = ox, oy, oz
        apply_grip(right, left, s, yaw, out[HEAD_A], out[HEAD_Y], weight * relax, grip_offset)
        if not guard or not arm_inside(left, 5.0, yaw):
            break
    if guard:
        clipped |= keep_out_of_body(left, 5.0, yaw)
    front = Part(-1.9, 12, 0.1)
    back = Part(1.9, 12, 0.1)
    step = out[STEP]
    front.xr -= 0.55 * step
    front.z -= 2.2 * step
    back.xr += 0.25 * step
    back.z += 0.6 * step
    return yaw, right, left, clipped, head, body, front, back, lean


def box(x0, x1, y0, y1, z0, z1):
    return np.array([[x, y, z] for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)], dtype=float)


EDGES = [(0, 1), (2, 3), (4, 5), (6, 7), (0, 2), (1, 3), (4, 6), (5, 7), (0, 4), (1, 5), (2, 6), (3, 7)]


def draw_box(ax, pts, view, color, lw=1.0):
    proj = [project(p, view) for p in pts]
    for a, b in EDGES:
        ax.plot([proj[a][0], proj[b][0]], [proj[a][1], proj[b][1]], color=color, lw=lw)


def project(p, view):
    x, y, z = p
    if view == "front":
        return (-x, -y)
    if view == "side":
        return (-z, -y)
    return (-x, z)


BLADES = {
    "SPEAR": ("leaf", 9.0, 2.5),
    "SPEAR:SWEEP": ("leaf", 9.0, 2.5),
    "SPEAR:SLAM": ("leaf", 9.0, 2.5),
    "SPEAR:TWIRL": ("leaf", 9.0, 2.5),
    "SPEAR:SCYTHE": ("sickle", 14.0, 13.0),
    "SPEAR:RUBY": ("sickle", 14.0, 13.0),
    "SPEAR:RUBY_VAULT": ("sickle", 14.0, 13.0),
    "SPEAR:RUBY_SPIN": ("sickle", 14.0, 13.0),
    "DAGGER": ("blade", 11.0, 2.5),
    "DAGGER:STAB": ("blade", 11.0, 2.5),
    "DAGGER:WHIRL": ("blade", 11.0, 2.5),
    "WAND": ("orb", 3.0, 3.0),
    "WAND:STAFF": ("orb", 4.0, 4.0),
    "RELIK": ("flat", 8.0, 5.0),
}


def blade_axes(right, shaft_world, twist):
    m = right.matrix()
    local = m.T @ shaft_world
    wrist = rotation_to(np.array([0, 0, -1.0]), local)
    tw = axis_angle(np.array([0, 0, -1.0]), twist * TWIST_SCALE)
    frame = m @ wrist @ tw
    normal = frame @ np.array([0, -1.0, 0])
    width = frame @ np.array([BLADE_SIDE, 0, 0])
    return normal, width


def blade_points(kind, length, span, tip, shaft, width):
    if kind == "sickle":
        n = 10
        outer = [tip + width * (span * (i / n)) - shaft * (length * 0.55 * (i / n) ** 2) for i in range(n + 1)]
        inner = [pt + shaft * (5.5 * (1 - i / n) + 0.8) - width * 0.0 for i, pt in enumerate(outer)]
        return outer + inner[::-1]
    if kind == "blade":
        base = tip - shaft * length
        return [base + width * span, tip + width * span * 0.15, tip - width * span * 0.15, base - width * span]
    if kind == "leaf":
        base = tip - shaft * length
        mid = tip - shaft * (length * 0.55)
        return [base, mid + width * span, tip + shaft * 3, mid - width * span]
    if kind == "flat":
        base = tip - shaft * length
        return [base + width * span, tip + width * span, tip - width * span, base - width * span]
    return [tip + width * length * 0.5, tip + shaft * length * 0.5, tip - width * length * 0.5, tip - shaft * length * 0.5]


def draw_blade(ax, view, right, shaft, out, tip, blade):
    kind, length, span = blade
    normal, width = blade_axes(right, shaft, out[TWIST])
    pts = blade_points(kind, length, span, tip, shaft, width)
    proj = np.array([project(p, view) for p in pts])
    face = float(np.dot(normal, np.array([0, 0, -1.0]) if view == "side" else np.array([0, 1.0, 0]) if view == "top" else np.array([0, 0, 1.0])))
    color = "#7fd1ff" if face >= 0 else "#ff9f7f"
    ax.fill(proj[:, 0], proj[:, 1], color=color, alpha=0.8, zorder=5)
    ax.plot(np.append(proj[:, 0], proj[0, 0]), np.append(proj[:, 1], proj[0, 1]), color="#223", lw=0.8, zorder=6)


def part_points(part, pts):
    return (pts @ part.matrix().T) + np.array([part.x, part.y, part.z])


def scene(ax, out, view, grip_offset, weapon_len, title, blade=None):
    yaw, right, left, clipped, head, body, front, back, lean = solve(out, grip_offset)
    draw_box(ax, part_points(front, box(-2, 2, 0, 12, -2, 2)), view, "#666666")
    draw_box(ax, part_points(back, box(-2, 2, 0, 12, -2, 2)), view, "#666666")
    draw_box(ax, part_points(body, box(-4, 4, 0, 12, -2, 2)), view, "#888888", 1.5)
    draw_box(ax, part_points(head, box(-4, 4, -8, 0, -4, 4)), view, "#aaaaaa")
    for part, arm_box, color in ((right, box(-3, 1, -2, 10, -2, 2), "#d33"), (left, box(-1, 3, -2, 10, -2, 2), "#36d")):
        draw_box(ax, part_points(part, arm_box), view, color, 1.6)
    grip = right.matrix() @ np.array([0, GRIP_Y, GRIP_Z]) + np.array([right.x, right.y, right.z])
    cos_a = math.cos(out[HEAD_A])
    shaft = rot_y(yaw) @ np.array([math.sin(out[HEAD_Y]) * cos_a, math.sin(out[HEAD_A]), -math.cos(out[HEAD_Y]) * cos_a])
    tail = grip - shaft * weapon_len[0]
    tip = grip + shaft * weapon_len[1]
    a, b = project(tail, view), project(tip, view)
    ax.plot([a[0], b[0]], [a[1], b[1]], color="#e8a020", lw=3)
    if blade is not None:
        draw_blade(ax, view, right, shaft, out, tip, blade)
    hand_l = left.matrix() @ np.array([0, GRIP_Y, GRIP_Z]) + np.array([left.x, left.y, left.z])
    ax.plot(*project(hand_l, view), "o", color="#36d", ms=5)
    ax.plot(*project(grip, view), "o", color="#d33", ms=5)
    ax.set_aspect("equal")
    ax.set_xticks([])
    ax.set_yticks([])
    ax.set_title(title + (" CLIP" if clipped else ""), fontsize=7)
    ax.set_xlim(-32, 32)
    ax.set_ylim(-34, 14)


def parse_array(text):
    return [float(v) for v in re.findall(r"-?\d+\.?\d*f?", text.replace("f", ""))]


def load_states(src):
    states = {}
    pat = r'"([A-Z_:]+)" to stance\(\s*' + r'\s*'.join([r'floatArrayOf\(([^)]*)\),'] * 4) + r'\s*(\d+f)?'
    for m in re.finditer(pat, src):
        arrays = [np.array(parse_array(m.group(i))) for i in range(2, 6)]
        offset = float(m.group(6).replace("f", "")) if m.group(6) else 7.0
        states[m.group(1)] = (*arrays, offset)
    return states


def load_stances(src):
    return {k: (v[0], v[1], v[4]) for k, v in load_states(src).items()}


WEAPON_LEN = {"SPEAR": (14, 34), "SPEAR:SCYTHE": (14, 34), "WAND:STAFF": (14, 34), "DAGGER": (2, 12), "WAND": (2, 14), "RELIK": (2, 16), "BOW": (14, 14), "BOW:FIREARM": (10, 16)}


def render_stances(src, out_dir, only=None):
    for key, (carry, ready, idle, sprint, offset) in load_states(src).items():
        if only and key not in only:
            continue
        fig, axes = plt.subplots(4, 3, figsize=(10, 14.6))
        for j, (label, ch) in enumerate((("carry", carry), ("ready", ready), ("idle", idle), ("sprint", sprint))):
            for k, view in enumerate(("front", "side", "top")):
                scene(axes[j][k], ch, view, offset, WEAPON_LEN.get(key, (4, 14)), f"{key} {label} {view}", BLADES.get(key))
        fig.tight_layout()
        fig.savefig(out_dir / f"stance_{key.replace(':', '_')}.png", dpi=60)
        plt.close(fig)


class Curve:
    def __init__(self, keys):
        self.t = [k[0] for k in keys]
        self.v = [k[1] for k in keys]

    def val(self, i, base):
        v = self.v[i]
        return base if math.isnan(v) else v

    def slope(self, i, base):
        if i == 0 or i == len(self.t) - 1:
            return 0.0
        return (self.val(i + 1, base) - self.val(i - 1, base)) / (self.t[i + 1] - self.t[i - 1])

    def at(self, t, base):
        if t <= self.t[0]:
            return self.val(0, base)
        for i in range(1, len(self.t)):
            if t <= self.t[i]:
                h = self.t[i] - self.t[i - 1]
                u = (t - self.t[i - 1]) / h
                u2, u3 = u * u, u * u * u
                return ((2 * u3 - 3 * u2 + 1) * self.val(i - 1, base) + (u3 - 2 * u2 + u) * h * self.slope(i - 1, base)
                        + (-2 * u3 + 3 * u2) * self.val(i, base) + (u3 - u2) * h * self.slope(i, base))
        return self.val(len(self.t) - 1, base)


def parse_curve(text):
    keys = []
    for a, b in re.findall(r"(-?\d+\.?\d*)f to (B|-?\d+\.?\d*)f?", text):
        keys.append((float(a), NAN if b == "B" else float(b)))
    return Curve(keys)


def load_poses(src):
    poses = {}
    for m in re.finditer(r'"([A-Z_:]+)" to Pose\(', src):
        start = m.end()
        depth = 1
        i = start
        while depth:
            c = src[i]
            depth += c == "("
            depth -= c == ")"
            i += 1
        body = src[start:i - 1]
        curves = {}
        for cm in re.finditer(r"(\w+) = curve\(([^\n]*)\),?", body):
            curves[cm.group(1)] = parse_curve(cm.group(2))
        if not m.group(1).startswith("BOW") and "turn" in curves:
            c = curves["turn"]
            curves["turn"] = Curve([(t, v * TURN_BOOST) for t, v in zip(c.t, c.v)])
        poses[m.group(1)] = curves
    body_re = r'"([A-Z_:]+)" to \((curve\([^\n]*?\)|KEEP) to (curve\([^\n]*?\)|KEEP)\)'
    for m in re.finditer(body_re, src):
        if m.group(1) in poses:
            if m.group(2) != "KEEP":
                poses[m.group(1)]["lean"] = parse_curve(m.group(2))
            if m.group(3) != "KEEP":
                poses[m.group(1)]["step"] = parse_curve(m.group(3))
    return poses



PARAMS = ["pitch", "across", "abduct", "offPitch", "offAcross", "offAbduct", "turn", "reach", "headA", "headY", "twist", "grip", "lean", "step"]


SPIN_KEYS = {"SPEAR:TWIRL", "SPEAR:RUBY_SPIN"}


def sample_pose(curves, t, base, key=None):
    out = np.array(base, dtype=float)
    for i, name in enumerate(PARAMS):
        if name in curves:
            if i in (HEAD_Y, TWIST) and key in SPIN_KEYS:
                out[i] = base[i] + curves[name].at(t, 0.0)
            else:
                out[i] = curves[name].at(t, base[i])
    return out


def render_swing(src, key, path, frames=8):
    stances = load_stances(src)
    poses = load_poses(src)
    stance_key = SPELL_STANCE.get(key) or (key if key in stances else key.split(":")[0])
    carry, ready, offset = stances[stance_key]
    base = ready.copy()
    fig, axes = plt.subplots(3, frames, figsize=(2.2 * frames, 6.6))
    for f in range(frames):
        t = f / (frames - 1)
        out = sample_pose(poses[key], t, base, key)
        for r, view in enumerate(("front", "side", "top")):
            scene(axes[r][f], out, view, offset, WEAPON_LEN.get(stance_key, (4, 14)), f"{key} t={t:.2f} {view}", BLADES.get(key))
    fig.tight_layout()
    fig.savefig(path, dpi=80)


def audit(src):
    stances = load_stances(src)
    poses = load_poses(src)
    total_pre = total_post = 0
    for key, curves in poses.items():
        stance_key = SPELL_STANCE.get(key) or (key if key in stances else key.split(":")[0])
        if stance_key not in stances:
            continue
        base = stances[stance_key][1]
        offset = stances[stance_key][2]
        pre = post = 0
        for i in range(61):
            out = sample_pose(curves, i / 60, base, key)
            raw = solve(out, offset, guard=False)
            if arm_inside(raw[1], -5.0, raw[0]) or arm_inside(raw[2], 5.0, raw[0]):
                pre += 1
            fixed = solve(out, offset, guard=True)
            if arm_inside(fixed[1], -5.0, fixed[0]) or arm_inside(fixed[2], 5.0, fixed[0]):
                post += 1
        total_pre += pre
        total_post += post
        print(f"{key:14s} clipped frames before guard: {pre:2d}/61   after: {post:2d}/61")
    print("total", total_pre, total_post)


FP_UNIT = 1 / 16
FP_EYE_Y = -1.9
FP_HAND_GAIN = 1.0
FP_LIMIT_OUT = 0.18
FP_LIMIT_IN = 0.7
FP_LIMIT_UP = 0.55
FP_LIMIT_DOWN = 0.06
FP_LIMIT_BACK = 0.1
FP_LIMIT_FORWARD = 0.5
FP_MIN_FORWARD = 0.4
FP_CONE = 1.2
FP_ROLL = 1.2
FP_SCALE = 0.82
FP_SHRINK = 0.1
FP_RAMP_IN = 0.08
FP_RAMP_OUT = 0.16
FP_AXIS = np.array([0, 0.8, -0.5]) / np.linalg.norm([0, 0.8, -0.5])
FP_HAND = (0.56, -0.52, -0.72)


def fp_grip(ch, s):
    yaw = -s * ch[TURN]
    arm = rot_zyx(s * ch[ABDUCT], -s * ch[ACROSS] + yaw, ch[PITCH])
    g = arm @ np.array([0, GRIP_Y, GRIP_Z]) + np.array([-s * math.cos(yaw) * 5, 2, s * math.sin(yaw) * 5])
    g[2] += ch[REACH]
    return g


def fp_rig(ch, s):
    pos = fp_grip(ch, s)
    cos_a = math.cos(ch[HEAD_A])
    d = rot_y(-s * ch[TURN]) @ np.array([s * math.sin(ch[HEAD_Y]) * cos_a, math.sin(ch[HEAD_A]), -math.cos(ch[HEAD_Y]) * cos_a])
    lean = ch[LEAN]
    if lean != 0:
        pos = pos - np.array([0, HIP_Y, 0])
        pos = rot_x(lean) @ pos
        pos = pos + np.array([0, HIP_Y, 0])
        d = rot_x(lean) @ d
    pos = np.array([-pos[0] * FP_UNIT, -(pos[1] - FP_EYE_Y) * FP_UNIT, pos[2] * FP_UNIT])
    d = np.array([-d[0], -d[1], d[2]])
    if d[2] > -FP_MIN_FORWARD:
        d[2] = -FP_MIN_FORWARD
    return pos, d / np.linalg.norm(d)


def soft_limit(v, pos, neg):
    return pos * math.tanh(v / pos) if v >= 0 else -neg * math.tanh(-v / neg)


def fp_transform(ch, start, t, s=1.0):
    p1, d1 = fp_rig(ch, s)
    p0, d0 = fp_rig(start, s)
    dx = soft_limit((p1[0] - p0[0]) * s * FP_HAND_GAIN, FP_LIMIT_OUT, FP_LIMIT_IN) * s
    dy = soft_limit((p1[1] - p0[1]) * FP_HAND_GAIN, FP_LIMIT_UP, FP_LIMIT_DOWN)
    dz = soft_limit((p1[2] - p0[2]) * FP_HAND_GAIN, FP_LIMIT_BACK, FP_LIMIT_FORWARD)
    arc = rotation_to(d0, d1)
    ang = math.acos(max(-1.0, min(1.0, (np.trace(arc) - 1) / 2)))
    if ang > 1e-6:
        w = np.array([arc[2, 1] - arc[1, 2], arc[0, 2] - arc[2, 0], arc[1, 0] - arc[0, 1]])
        n = np.linalg.norm(w)
        arc = axis_angle(w / n, FP_CONE * math.tanh(ang / FP_CONE)) if n > 1e-9 else np.eye(3)
    roll = FP_ROLL * math.tanh(-s * (ch[TWIST] - start[TWIST]) / FP_ROLL)
    m = axis_angle(d1, roll) @ arc
    ramp = max(0.0, min(t / FP_RAMP_IN, (1 - t) / FP_RAMP_OUT, 1.0))
    ramp = ramp * ramp * (3 - 2 * ramp)
    scale = FP_SCALE * (1 - FP_SHRINK * ramp)
    return np.array([dx, dy, dz]), m, scale


def fp_project(p):
    z = min(p[2], -0.05)
    return (p[0] / -z, p[1] / -z)


def render_fp(src, key, path, frames=8):
    stances = load_stances(src)
    poses = load_poses(src)
    stance_key = SPELL_STANCE.get(key) or (key if key in stances else key.split(":")[0])
    ready = stances[stance_key][1]
    fig, axes = plt.subplots(2, frames // 2, figsize=(2.6 * frames / 2, 5.2))
    half_h = math.tan(math.radians(35))
    half_w = half_h * 16 / 9
    for f, ax in enumerate(axes.flat):
        t = f / (frames - 1)
        ch = sample_pose(poses[key], t, ready, key)
        start = sample_pose(poses[key], 0.0, ready, key)
        d, m, sc = fp_transform(ch, start, t)
        origin = np.array(FP_HAND) + d
        pts = [origin + m @ (FP_AXIS * k * sc) for k in (-0.05, 0.4, 0.95)]
        proj = [fp_project(p) for p in pts]
        ax.add_patch(plt.Rectangle((-half_w, -half_h), 2 * half_w, 2 * half_h, fill=False, ec="#999"))
        ax.plot([p[0] for p in proj], [p[1] for p in proj], color="#e8a020", lw=4)
        ax.plot(*proj[0], "o", color="#d33", ms=6)
        ax.plot(*proj[2], "o", color="#7fd1ff", ms=4)
        rest = [np.array(FP_HAND) + FP_AXIS * k * FP_SCALE for k in (-0.05, 0.95)]
        rp = [fp_project(p) for p in rest]
        ax.plot([p[0] for p in rp], [p[1] for p in rp], color="#bbb", lw=1, ls="--")
        ax.set_xlim(-half_w * 1.15, half_w * 1.15)
        ax.set_ylim(-half_h * 1.15, half_h * 1.15)
        ax.set_aspect("equal")
        ax.set_xticks([])
        ax.set_yticks([])
        ax.set_title(f"{key} t={t:.2f}", fontsize=7)
    fig.tight_layout()
    fig.savefig(path, dpi=80)
    plt.close(fig)



def kotlin_const(src, name):
    m = re.search(r"const val " + name + r" = (-?[\d.]+)f", src)
    return float(m.group(1))


def stance_block(src, key):
    start = src.index('"' + key + '" to stance(')
    nxt = re.search(r'\n        "[A-Z_:]+" to stance\(', src[start + 10:])
    end = start + 10 + nxt.start() if nxt else len(src)
    return src[start:end]


def load_gait(src, block):
    m = re.search(r"gait = Gait\(([^)]*)\)", block, re.S)
    if m:
        return {k: float(v) for k, v in re.findall(r"(\w+) = (-?[\d.]+)f", m.group(1))}
    return {
        "mainWalk": kotlin_const(src, "WALK_MAIN_SWING"),
        "offWalk": kotlin_const(src, "WALK_OFF_SWING"),
        "mainSprint": kotlin_const(src, "SPRINT_MAIN_SWING"),
        "offSprint": kotlin_const(src, "SPRINT_OFF_SWING"),
        "across": kotlin_const(src, "SPRINT_ACROSS"),
        "lean": kotlin_const(src, "SPRINT_LEAN"),
        "leanBounce": kotlin_const(src, "SPRINT_BOUNCE"),
        "tipWalk": kotlin_const(src, "WALK_TIP_BOUNCE"),
        "tipSprint": kotlin_const(src, "SPRINT_TIP_BOUNCE"),
        "yaw": 0.06,
        "armBob": 0.0,
    }


def gait_channels(src, key, mode, phase):
    block = stance_block(src, key)
    carry, ready, idle, sprint, offset = load_states(src)[key]
    m = re.search(r"walk = floatArrayOf\(([^)]*)\)", block)
    if mode == "sprint":
        base = sprint.copy()
        walk_amt, sprint_amt = 0.0, 1.0
    else:
        base = np.array(parse_array(m.group(1))) if m else carry + (sprint - carry) * 0.35
        walk_amt, sprint_amt = 1.0, 0.0
    g = load_gait(src, block)
    move = walk_amt + sprint_amt
    swing = math.cos(phase)
    bounce = math.sin(phase * 2)
    main_amp = g["mainWalk"] * walk_amt + g["mainSprint"] * sprint_amt
    off_amp = g["offWalk"] * walk_amt + g["offSprint"] * sprint_amt
    arm_bob = bounce * g["armBob"] * move
    out = base.copy()
    out[PITCH] += arm_bob - swing * main_amp
    out[OFF_PITCH] += arm_bob + swing * off_amp
    out[ACROSS] += swing * g["across"] * sprint_amt
    out[OFF_ACROSS] -= swing * g["across"] * sprint_amt
    out[LEAN] += g["lean"] * sprint_amt + bounce * g["leanBounce"] * sprint_amt
    out[HEAD_A] += bounce * (g["tipSprint"] * sprint_amt + g["tipWalk"] * walk_amt)
    out[HEAD_Y] += swing * g["yaw"] * move
    out[TWIST] += swing * g.get("twist", 0.0) * move
    return out, offset


def render_gait(src, key, mode, path, frames=8):
    fig, axes = plt.subplots(3, frames, figsize=(2.2 * frames, 6.6))
    for f in range(frames):
        phase = 2 * math.pi * f / frames
        ch, offset = gait_channels(src, key, mode, phase)
        for r, view in enumerate(("front", "side", "top")):
            scene(axes[r][f], ch, view, offset, WEAPON_LEN.get(key, (4, 14)), f"{key} {mode} {f}/{frames} {view}", BLADES.get(key))
    fig.tight_layout()
    fig.savefig(path, dpi=80)
    plt.close(fig)

if __name__ == "__main__":
    src = SRC.read_text(encoding="utf-8")
    if sys.argv[1] == "--audit":
        audit(src)
        sys.exit(0)
    out_dir = Path(sys.argv[1])
    out_dir.mkdir(parents=True, exist_ok=True)
    args = sys.argv[2:]
    swings = [a[6:] for a in args if a.startswith("swing:")]
    fps = [a[3:] for a in args if a.startswith("fp:")]
    gaits = [a[5:] for a in args if a.startswith("gait:")]
    only = [a for a in args if not a.startswith("swing:") and not a.startswith("fp:") and not a.startswith("gait:")]
    for key in gaits:
        for mode in ("walk", "sprint"):
            render_gait(src, key, mode, out_dir / f"gait_{key.replace(':', '_')}_{mode}.png")
    if gaits and not swings and not only and not fps:
        sys.exit(0)
    for key in fps:
        render_fp(src, key, out_dir / f"fp_{key.replace(':', '_')}.png")
    if fps and not swings and not only:
        sys.exit(0)
    render_stances(src, out_dir, only or None)
    for key in swings:
        render_swing(src, key, out_dir / f"swing_{key.replace(':', '_')}.png")
