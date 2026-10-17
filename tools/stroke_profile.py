import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pose_render as pr


def profile(lib, poses, stances, key, mirror=False, stance_key=None, times=(0.0, 0.25, 0.5, 0.75, 1.0)):
    stance_key = stance_key or pr.stance_of(key, stances)
    curves = poses[key]
    if mirror:
        curves = pr.mirror_curves(curves)
    base = stances[stance_key][1]
    hit = float(lib["poses"][key]["hit"])
    rows = []
    for t in (0.0, hit * 0.5, hit, hit + (1 - hit) * 0.4, 1.0):
        out = pr.sample_pose(curves, t, base, key)
        rows.append((t, math.degrees(out[pr.HEAD_Y]), -math.degrees(out[pr.HEAD_A]), out[pr.TURN], out[pr.LEAN], out[pr.REACH]))
    return rows


if __name__ == "__main__":
    lib = pr.load_library(pr.SRC)
    poses = pr.load_poses(lib)
    stances = pr.load_stances(lib)
    for ms in sys.argv[1:]:
        m = lib["movesets"][ms]
        print("==", ms)
        for step in ([m["opener"]] if m.get("opener") else []) + m["light"]:
            rows = profile(lib, poses, stances, step["pose"], step.get("mirror", False), ms)
            body = " | ".join(f"t{t:.2f} az{az:+4.0f} el{el:+4.0f} tn{tn:+.2f}" for t, az, el, tn, ln, rc in rows)
            print(f"  {step['pose']:24s}{'(m)' if step.get('mirror') else '   '} {body}")
