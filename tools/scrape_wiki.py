import argparse
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://wynncraft.wiki.gg/api.php"
BUNDLE = "src/client/resources/assets/wynnoverhaul/activity_wiki.json"
UA = {"User-Agent": "WynnOverhaul-wiki-fill/1.0"}


def call(**params):
    params["format"] = "json"
    params["formatversion"] = "2"
    req = urllib.request.Request(API + "?" + urllib.parse.urlencode(params), headers=UA)
    for attempt in range(6):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                time.sleep(0.5)
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code != 429 or attempt == 5:
                raise
            time.sleep(5 * (attempt + 1))


def wikitext(title):
    data = call(action="query", prop="revisions", rvprop="content", rvslots="main", titles=title, redirects="1")
    page = data["query"]["pages"][0]
    if page.get("missing"):
        return None
    return page["revisions"][0]["slots"]["main"]["content"]


def embedded(template):
    titles = []
    cont = {}
    while True:
        data = call(action="query", list="embeddedin", eititle="Template:" + template, eilimit="500", einamespace="0", **cont)
        titles += [p["title"] for p in data["query"]["embeddedin"]]
        if "continue" not in data:
            return titles
        cont = data["continue"]


def strip_markup(text):
    text = re.sub(r"\{\{[^{}]*\}\}", "", text)
    text = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]", r"\1", text)
    text = re.sub(r"'''?", "", text)
    text = re.sub(r"<[^>]+>", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def infobox_field(text, key):
    m = re.search(r"^\|\s*" + key + r"\s*=\s*(.*)$", text, re.M)
    return m.group(1).strip() if m else None


def build(title, label, text):
    body = re.sub(r"^\{\{Infobox/.*?^\}\}\s*", "", text, count=1, flags=re.S | re.M)
    parts = re.split(r"^==\s*(.+?)\s*==\s*$", body, flags=re.M)
    intro_lines = [strip_markup(p) for p in parts[0].split("\n\n")]
    intro = [{"text": t, "italic": False} for t in intro_lines if t]
    sections = []
    for i in range(1, len(parts), 2):
        block = parts[i + 1].split("{{MobTable}}")[0]
        lines = [strip_markup(p) for p in block.split("\n\n")]
        lines = [{"text": t, "italic": False} for t in lines if t]
        if lines:
            sections.append({"title": parts[i], "lines": lines})
    entry = {"name": title, "title": title, "type": label, "intro": intro, "sections": sections}
    coords = [infobox_field(text, k) for k in ("xcoordinate", "ycoordinate", "zcoordinate")]
    if all(c is not None and re.fullmatch(r"-?\d+", c) for c in coords):
        entry["coord"] = {"x": int(coords[0]), "y": int(coords[1]), "z": int(coords[2])}
    return entry


def main():
    ap = argparse.ArgumentParser(description="Add missing activity pages to the bundled wiki data.")
    ap.add_argument("--label", default="Cave", help="bundle type label, e.g. Cave, Quest, Dungeon")
    ap.add_argument("--template", help="infobox template to enumerate, default Infobox/<label>")
    ap.add_argument("--name", action="append", default=[], help="specific page title to fetch (repeatable)")
    ap.add_argument("--missing", action="store_true", help="fetch every page of the template that the bundle lacks")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    with open(BUNDLE, encoding="utf-8") as f:
        bundle = json.load(f)

    names = list(args.name)
    if args.missing:
        template = args.template or "Infobox/" + args.label
        old_version = re.compile(r"\(\d+(?:\.\d+)+\)$")
        names += [t for t in embedded(template) if f"{args.label}::{t}" not in bundle and not old_version.search(t)]

    added = 0
    for title in names:
        key = f"{args.label}::{title}"
        if key in bundle:
            continue
        text = wikitext(title)
        if text is None:
            print(f"no wiki page: {title}", file=sys.stderr)
            continue
        bundle[key] = build(title, args.label, text)
        added += 1
        print(f"added {key}")

    if added and not args.dry_run:
        with open(BUNDLE, "w", encoding="utf-8", newline="") as f:
            json.dump(bundle, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{added} added, {len(bundle)} total")


if __name__ == "__main__":
    main()
