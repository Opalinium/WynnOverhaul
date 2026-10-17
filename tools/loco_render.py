import math
import re
import sys
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
import yaml

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pose_render as pr
import trunk

MASTER = 0.7
GAIT_PHASE = 0.6662
BLOCK_PX = 16.0
LIMITS = dict(pitch=0.9, roll=0.3, yaw=0.45, hip_yaw=0.4, hip_tilt=0.3, lift=1.4, dip=2.6)
GAIT_DUTY_MIN = 0.3
STRIDE_MIN = 0.2
STRIDE_MAX = 1.2
TRAVEL_MAX = 20.0
SWING_ARC_SKEW = 0.8
WALK_SHARE = 0.35


def clamp(x, a, b):
    return min(max(x, a), b)


def clamp01(x):
    return clamp(x, 0.0, 1.0)


FUNCS = {
    "sin": math.sin, "cos": math.cos, "abs": abs, "sqrt": lambda a: math.sqrt(max(0.0, a)), "min": min, "max": max,
    "pow": lambda a, b: float(a) ** b if a >= 0 else 0.0, "clamp": clamp, "lerp": lambda a, b, t: a + (b - a) * t, "pi": math.pi,
}


class Loco:
    def __init__(self, src=pr.SRC):
        doc = yaml.safe_load((Path(src) / "locomotion.yml").read_text(encoding="utf-8"))
        self.defaults = doc["styleDefaults"]
        self.styles = doc["styles"]
        self.states = doc["states"]
        gait = yaml.safe_load((Path(src) / "gait.yml").read_text(encoding="utf-8"))["gait"]
        self.gait = gait

    def style(self, name):
        merged = dict(self.defaults)
        merged.update({k: v for k, v in self.styles[name].items() if k != "label"})
        return merged

    @staticmethod
    def compile(text):
        text = re.sub(r"style\.(\w+)", r"S_\1", text)
        return compile(text, "<expr>", "eval")


def swing_offset(phase, duty, travel, lift):
    tau = 2 * math.pi
    q = ((phase - 1.5 * math.pi) / tau + 0.5) % 1.0
    half = duty / 2
    if abs(q - 0.5) <= half:
        p = (q - (0.5 - half)) / duty
        return travel * 0.5 * (1 - 2 * p), 0.0
    r = ((q - 0.5 - half) if q > 0.5 else (q + 0.5 - half)) / (1 - duty)
    slope = -travel * (1 - duty) / duty
    r2 = r * r
    r3 = r2 * r
    x = travel * 0.5 * (-(2 * r3 - 3 * r2 + 1) + (-2 * r3 + 3 * r2)) + slope * ((r3 - 2 * r2 + r) + (r3 - r2))
    return x, lift * math.sin(math.pi * r ** SWING_ARC_SKEW)


def scenario(mode):
    if mode == "walk":
        return dict(speed=0.86, h=0.216, sprint=0.0, moving=1.0)
    if mode == "sprint":
        return dict(speed=1.0, h=0.28, sprint=1.0, moving=1.0)
    return dict(speed=0.0, h=0.0, sprint=0.0, moving=0.0)


def run_states(loco, style_name, mode, phase, claimed, stroke=0.0, age=40.0):
    sc = scenario(mode)
    style = loco.style(style_name)
    pace = sc["h"] / 0.22
    vanilla = clamp01(sc["speed"])
    sp = clamp((vanilla + clamp01(pace * 0.9 + max(0.0, pace - 1.25) * 0.5)) * 0.5, 0.0, 1.3)
    boost = max(0.0, sp - vanilla)
    w_move = sc["moving"]
    w_sprint = sc["sprint"]
    env = dict(
        k=MASTER, wAir=0.0, wRise=0.0, wFall=0.0, wLand=0.0, wMove=w_move, wSprint=w_sprint, wClimb=0.0, climbMove=0.0,
        wSwim=0.0, wTread=0.0, wCrawl=0.0, wRide=0.0, wFly=0.0, wUse=0.0, wBlock=0.0, wHurt=0.0, wDead=0.0, wCrouch=0.0,
        pace=pace, sp=sp, boost=boost, vanillaSp=vanilla, phase=phase, gait=math.cos(phase), gaitOpp=math.cos(phase + math.pi),
        age=age, grounded=1.0, turnRate=0.0, launchAge=9.0, leadRight=1.0, launch=0.0, moveFwd=1.0, moveSide=0.0,
        claimed=1.0 if claimed else 0.0, stroke=stroke,
    )
    for name, value in style.items():
        env["S_" + name] = value
    env.update(FUNCS)
    T = {}

    def get(target):
        return T.get(target, 0.0)

    for state in loco.states:
        if state.get("gate") and env.get(state["gate"], 0.0) <= 0.001:
            continue
        for var, text in (state.get("vars") or {}).items():
            env[var] = eval(Loco.compile(str(text)), {"__builtins__": {}}, env)
        for fx in state["effects"]:
            if fx.get("freeArms") and claimed:
                continue
            target = fx["to"]
            if "add" in fx:
                v = eval(Loco.compile(str(fx["add"])), {"__builtins__": {}}, env)
                T[target] = get(target) + v
            elif "sub" in fx:
                v = eval(Loco.compile("-(" + str(fx["sub"]) + ")"), {"__builtins__": {}}, env)
                T[target] = get(target) + v
            else:
                v = eval(Loco.compile(str(fx["approach"])), {"__builtins__": {}}, env)
                rate = eval(Loco.compile(str(fx["rate"])), {"__builtins__": {}}, env)
                T[target] = get(target) + (v - get(target)) * rate
    return T, env, style, sc


def gait_feet(loco, style, env, sc, phase, crouch=0.0):
    sprint = env["wSprint"]
    spec = loco.gait
    duty = max(GAIT_DUTY_MIN, spec["walk"]["duty"] + (spec["sprint"]["duty"] - spec["walk"]["duty"]) * sprint)
    lift = spec["walk"]["lift"] + (spec["sprint"]["lift"] - spec["walk"]["lift"]) * sprint
    stride = clamp(style["walkLeg"] + (style["sprintLeg"] - style["walkLeg"]) * sprint, STRIDE_MIN, STRIDE_MAX)
    rate = GAIT_PHASE * sc["speed"]
    ground = sc["h"] * BLOCK_PX
    k = ground / rate if rate > 0.02 else 6.0
    k = clamp(k, 2.0, 14.0)
    travel = min(k * 2 * math.pi * duty * stride, TRAVEL_MAX)
    right = swing_offset(phase, duty, travel, lift)
    left = swing_offset(phase + math.pi, duty, travel, lift)
    return left, right, travel, duty


def frame(loco, style_name, mode, phase, stance_key, stances, claimed=True):
    T, env, style, sc = run_states(loco, style_name, mode, phase, claimed)
    carry, ready, off = stances[stance_key]
    lib_state = STATE_CACHE[stance_key]
    sprint = env["wSprint"]
    walk = 1.0 - sprint if mode != "idle" else 0.0
    if mode == "idle":
        base = lib_state["carry"].copy()
    elif mode == "walk":
        base = lib_state["walk"].copy()
    else:
        base = lib_state["sprint"].copy()
    out = base.copy()
    g = lib_state["gait"]
    sp = clamp01(sc["speed"])
    swing = math.cos(phase * 1.0)
    bounce = math.sin(phase * 2.0)
    move = walk + sprint
    if move > 0:
        main_amp = (g["mainWalk"] * walk + g["mainSprint"] * sprint) * sp
        off_amp = (g["offWalk"] * walk + g["offSprint"] * sprint) * sp
        arm_bob = bounce * g["armBob"] * move * sp
        out[pr.PITCH] += arm_bob - swing * main_amp
        out[pr.OFF_PITCH] += arm_bob + swing * off_amp
        out[pr.ACROSS] += swing * g["across"] * sprint * sp
        out[pr.OFF_ACROSS] -= swing * g["across"] * sprint * sp
        out[pr.LEAN] += g["lean"] * sprint + bounce * g["leanBounce"] * sprint * sp
        out[pr.HEAD_A] += bounce * (g["tipSprint"] * sprint + g["tipWalk"] * walk) * sp
        out[pr.HEAD_Y] += swing * g["yaw"] * move * sp
        out[pr.TWIST] += swing * g["twist"] * move * sp
    pitch = clamp(T.get("torso.pitch", 0.0), -LIMITS["pitch"], LIMITS["pitch"])
    roll = clamp(T.get("torso.roll", 0.0), -LIMITS["roll"], LIMITS["roll"])
    yaw = clamp(T.get("torso.yaw", 0.0), -LIMITS["yaw"], LIMITS["yaw"])
    hip_yaw = clamp(T.get("torso.hipYaw", 0.0), -LIMITS["hip_yaw"], LIMITS["hip_yaw"])
    hip_pitch = clamp(T.get("torso.hipPitch", 0.0), -LIMITS["hip_tilt"], LIMITS["hip_tilt"])
    shift = clamp(T.get("torso.shift", 0.0), -LIMITS["lift"], LIMITS["dip"])
    out[pr.LEAN] += pitch + hip_pitch / trunk.PELVIS_SHARE + T.get("torso.curl", 0.0) / (1 - trunk.PELVIS_SHARE)
    out[pr.TURN] = -yaw
    out[pr.HIPS_Y] += -hip_yaw
    out[pr.BODY_R] += roll
    left, right, travel, duty = gait_feet(loco, style, env, sc, phase)
    rig_drop = T.get("rig.drop", 0.0)
    out[pr.ROOT_H] = rig_drop + shift
    out[pr.FOOT_LF], out[pr.FOOT_LU] = left
    out[pr.FOOT_RF], out[pr.FOOT_RU] = right
    out[pr.FOOT_LS] = 0.0
    out[pr.FOOT_RS] = 0.0
    return out, dict(travel=travel, duty=duty, T=T)


STATE_CACHE = {}


def prepare(src=pr.SRC):
    lib = pr.load_library(src)
    stances = pr.load_stances(lib)
    for key, st in lib["stances"].items():
        gait = dict(mainWalk=0.2, offWalk=0.5, mainSprint=0.55, offSprint=1.05, across=0.18, lean=0.14, leanBounce=0.05, tipWalk=0.05, tipSprint=0.14, yaw=0.06, armBob=0.0, twist=0.0)
        gait.update(st.get("gait") or {})
        walk = st.get("walk")
        sprint = st["sprint"]
        carry = st["carry"]
        STATE_CACHE[key] = dict(
            carry=pr.state_array(carry), sprint=pr.state_array(sprint),
            walk=pr.state_array(walk) if walk else pr.state_array(carry) + (pr.state_array(sprint) - pr.state_array(carry)) * WALK_SHARE,
            gait=gait,
        )
    return lib, stances


def render(style_name, stance_key, mode, path, frames=8, src=pr.SRC):
    loco = Loco(src)
    lib, stances = prepare(src)
    carry, ready, offset = stances[stance_key]
    wl = pr.WEAPON_LEN.get(stance_key) or pr.WEAPON_LEN.get(stance_key.split(":")[0], (4, 14))
    fig, axes = plt.subplots(2, frames, figsize=(2.6 * frames, 6.2))
    for c in range(frames):
        phase = c / frames * 2 * math.pi
        out, info = frame(loco, style_name, mode, phase, stance_key, stances)
        for r, view in enumerate(("side", "front")):
            pr.scene(axes[r][c], out, view, offset, wl, f"{mode} {c}/{frames} {view}", pr.blade_for(stance_key))
            axes[r][c].set_xlim(-20, 20)
            axes[r][c].set_ylim(-28, 12)
    fig.suptitle(f"{style_name} {stance_key} {mode} travel {info['travel']:.1f} duty {info['duty']:.2f}")
    fig.tight_layout()
    fig.savefig(path, dpi=90)
    plt.close(fig)


if __name__ == "__main__":
    style, stance, mode, out = sys.argv[1:5]
    render(style, stance, mode, out)
