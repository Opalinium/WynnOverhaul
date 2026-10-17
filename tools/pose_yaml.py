FIELDS = ("label", "seconds", "hit", "recover", "blend", "gripOffset", "handShift", "handSprintExtra", "holdEnd", "spin")
ORDER = ["pitch", "across", "abduct", "offPitch", "offAcross", "offAbduct", "turn", "hipY", "reach", "headA", "headY", "twist", "grip", "lean", "bodyY", "bodyR", "rootF", "rootS", "rootH", "footLF", "footLS", "footLU", "footRF", "footRS", "footRU"]


def fmt(v):
    if v is None:
        return "null"
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, float):
        return f"{v:g}"
    return str(v)


def value(v):
    return f'"{v}"' if isinstance(v, str) else fmt(v)


def curve(points):
    return "[" + ", ".join("[" + fmt(t) + ", " + fmt(v) + "]" for t, v in points) + "]"


def pose_text(key, pose):
    lines = [f'  "{key}":']
    for field in FIELDS:
        if field in pose:
            lines.append(f"    {field}: {value(pose[field])}")
    extra = [k for k in pose if k not in FIELDS and k not in ("sounds", "channels")]
    if extra:
        raise ValueError(f"unhandled fields {key} {extra}")
    if pose.get("sounds"):
        lines.append("    sounds:")
        for s in pose["sounds"]:
            lines.append("      - {" + ", ".join(f"{k}: {value(v)}" for k, v in s.items()) + "}")
    lines.append("    channels:")
    channels = pose["channels"]
    names = [k for k in ORDER if k in channels] + [k for k in channels if k not in ORDER]
    for name in names:
        lines.append(f"      {name}: {curve(channels[name])}")
    return "\n".join(lines)


def document(doc):
    text = ["poses:", "\n".join(pose_text(k, p) for k, p in doc["poses"].items())]
    if doc.get("movesetSpells"):
        text.append("movesetSpells:")
        for moveset, mapping in doc["movesetSpells"].items():
            text.append(f'  "{moveset}":')
            for spell, pose in mapping.items():
                text.append(f'    {spell}: "{pose}"')
    if doc.get("trails"):
        text.append("trails:")
        for k, v in doc["trails"].items():
            text.append(f'  "{k}": [{v[0]}, {v[1]}]')
    return "\n".join(text) + "\n"
