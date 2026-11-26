"""Convert the community data file (bc-en.yaml) into the compact JSON the app bundles.

Usage: python tools/convert_data.py refdata/bc-en.yaml app/src/main/assets/gamedata.json [YYYY-MM-DD]

The app can also read bc-en.yaml directly at runtime (see YamlLite.kt); this script
produces the snapshot shipped inside the APK and is the reference for the Kotlin parser test.
"""
import datetime
import json
import sys

import yaml

KEEP_PAST_DAYS = 120


def convert(src, today):
    with open(src, encoding="utf-8") as f:
        doc = yaml.safe_load(f)
    cutoff = today - datetime.timedelta(days=KEEP_PAST_DAYS)
    events = []
    used_gacha = set()
    for key, ev in doc["events"].items():
        end = ev["end_on"]
        if end < cutoff:
            continue
        item = {
            "k": str(key),
            "s": ev["start_on"].isoformat(),
            "e": end.isoformat(),
            "n": str(ev.get("name", "")).strip(),
            "id": int(ev["id"]),
            "ra": int(ev["rare"]),
            "su": int(ev["supa"]),
            "ub": int(ev["uber"]),
        }
        if ev.get("legend"):
            item["le"] = int(ev["legend"])
        if ev.get("guaranteed"):
            item["g"] = True
        if ev.get("step_up"):
            item["st"] = True
        if ev.get("platinum"):
            item["p"] = str(ev["platinum"])
        events.append(item)
        used_gacha.add(item["id"])
    gacha = {}
    used_cats = set()
    for gid in sorted(used_gacha):
        g = doc["gacha"].get(gid)
        if not g or not g.get("cats"):
            continue
        ids = [int(c) for c in g["cats"]]
        gacha[str(gid)] = ids
        used_cats.update(ids)
    cats = {}
    for cid in sorted(used_cats):
        c = doc["cats"].get(cid)
        if not c:
            continue
        cats[str(cid)] = {"n": [str(n) for n in c["name"]], "r": int(c["rarity"])}
    return {"fetched": today.isoformat(), "cats": cats, "gacha": gacha, "events": events}


if __name__ == "__main__":
    src, dst = sys.argv[1], sys.argv[2]
    day = datetime.date.fromisoformat(sys.argv[3]) if len(sys.argv) > 3 else datetime.date.today()
    out = convert(src, day)
    with open(dst, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    print(f"cats={len(out['cats'])} gacha={len(out['gacha'])} events={len(out['events'])}")
