import math

import numpy as np

LEG = 12.0
HALF = LEG / 2.0
HIP_X = 1.9
HIP_Y = 12.0
HIP_Z = 0.1
GROUND = HIP_Y + LEG
REACH_EPS = 0.002
MAX_RISE = 8.0
MAX_DROP = 10.0
FORWARD = np.array([0.0, 0.0, -1.0])


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def euler_zyx(m):
    y = -math.asin(max(-1.0, min(1.0, m[2, 0])))
    if abs(m[2, 0]) < 0.999999:
        x = math.atan2(m[2, 1], m[2, 2])
        z = math.atan2(m[1, 0], m[0, 0])
    else:
        x = math.atan2(-m[1, 2], m[1, 1])
        z = 0.0
    return x, y, z


def unit(v):
    n = np.linalg.norm(v)
    return v / n if n > 1e-9 else v


def solve_leg(hip, foot, twist):
    v = foot - hip
    d = float(np.linalg.norm(v))
    limit = LEG - REACH_EPS
    if d > limit:
        v = v * (limit / d)
        foot = hip + v
        d = limit
    d = max(d, 1.0)
    e1 = v / max(float(np.linalg.norm(v)), 1e-9)
    pole = rot_y(twist) @ FORWARD
    e2 = pole - np.dot(pole, e1) * e1
    e2 = unit(e2) if np.linalg.norm(e2) > 1e-6 else np.array([0.0, 0.0, -1.0])
    a = d / 2.0
    h = math.sqrt(max(HALF * HALF - a * a, 0.0))
    knee = hip + a * e1 + h * e2
    thigh = unit(knee - hip)
    shin = unit(foot - knee)
    cos_b = max(-1.0, min(1.0, float(np.dot(thigh, shin))))
    bend = math.acos(cos_b)
    side = shin - np.dot(shin, thigh) * thigh
    z_axis = unit(side) if np.linalg.norm(side) > 1e-6 else -e2
    x_axis = np.cross(thigh, z_axis)
    m = np.stack([x_axis, thigh, z_axis], axis=1)
    return euler_zyx(m), bend, foot


def place(hip_drop, root_f, root_s, foot_l, foot_r, twist, ground_l=0.0, ground_r=0.0):
    hips = []
    feet = []
    for sign, foot, ground in ((1.0, foot_l, ground_l), (-1.0, foot_r, ground_r)):
        hx = sign * HIP_X * math.cos(twist) + root_s
        hz = HIP_Z - sign * HIP_X * math.sin(twist) - root_f
        hips.append(np.array([hx, HIP_Y + hip_drop, hz]))
        feet.append(np.array([sign * HIP_X + foot[1], GROUND - foot[2] - ground, HIP_Z - foot[0]]))
    drop = hip_drop
    for hip, foot in zip(hips, feet):
        flat = math.hypot(foot[0] - hip[0], foot[2] - hip[2])
        vmax = math.sqrt(max(LEG * LEG - flat * flat, 0.0)) - REACH_EPS
        drop = max(drop, foot[1] - vmax - HIP_Y)
    drop = min(max(drop, -MAX_RISE), MAX_DROP + 4.0)
    for hip in hips:
        hip[1] = HIP_Y + drop
    legs = [solve_leg(hip, foot, twist) for hip, foot in zip(hips, feet)]
    return drop, hips, legs
