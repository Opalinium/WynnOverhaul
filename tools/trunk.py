import math

import numpy as np

SEGMENTS = 8
HIP = 12.0
LENGTH = 12.0
PELVIS_SHARE = 0.35
SHOULDER_U = 10.0 / 12.0


def twist_profile(u):
    return min(max(u / SHOULDER_U, 0.0), 1.0)


def curl_profile(u):
    c = min(max(u / SHOULDER_U, 0.0), 1.0)
    return c * c * (3 - 2 * c)


def rotation(roll, yaw, pitch):
    cz, sz = math.cos(roll), math.sin(roll)
    cy, sy = math.cos(yaw), math.sin(yaw)
    cx, sx = math.cos(pitch), math.sin(pitch)
    return np.array([
        [cz * cy, cz * sy * sx - sz * cx, cz * sy * cx + sz * sx],
        [sz * cy, sz * sy * sx + cz * cx, sz * sy * cx - cz * sx],
        [-sy, cy * sx, cy * cx],
    ])


def at(u, twist, curl, bend):
    return rotation(bend * curl_profile(u), twist * twist_profile(u), curl * curl_profile(u))


def chain(twist, curl, bend):
    seg = LENGTH / SEGMENTS
    p = [np.array([0.0, HIP, 0.0])]
    for i in range(SEGMENTS):
        r = at((i + 0.5) / SEGMENTS, twist, curl, bend)
        p.append(p[-1] - r[:, 1] * seg)
    rs = [at(j / SEGMENTS, twist, curl, bend) for j in range(SEGMENTS + 1)]
    return p, rs


def place(point, twist, curl, bend, pelvis=None):
    p, rs = chain(twist, curl, bend)
    u = min(max((HIP - point[1]) / LENGTH, 0.0), 1.0)
    ycl = HIP - u * LENGTH
    x = u * SEGMENTS
    i = min(int(x), SEGMENTS - 1)
    f = x - i
    r_mid = at((i + 0.5) / SEGMENTS, twist, curl, bend)
    origin = p[i] - r_mid[:, 1] * (LENGTH / SEGMENTS) * f
    r = at(u, twist, curl, bend)
    out = origin + r @ np.array([point[0], point[1] - ycl, point[2]])
    if pelvis is not None:
        out = np.array([0.0, HIP, 0.0]) + pelvis @ (out - np.array([0.0, HIP, 0.0]))
    return out


def pelvis_move(point, pelvis):
    hip = np.array([0.0, HIP, 0.0])
    return hip + pelvis @ (np.asarray(point, dtype=float) - hip)


def chest_matrix(hip_roll, hip_yaw, hip_pitch, twist, curl, bend):
    return rotation(hip_roll, hip_yaw, hip_pitch) @ at(SHOULDER_U, twist, curl, bend)
