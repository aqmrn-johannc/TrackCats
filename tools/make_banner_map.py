"""Build app/src/main/assets/banner_art.json: gacha id -> banner image file name on the Battle Cats Wiki.

Only file names are stored. The app downloads the pictures itself at runtime and caches them on the
phone; no artwork is kept in this repository.

Usage: python tools/make_banner_map.py app/src/main/assets/gamedata.json app/src/main/assets/banner_art.json
"""
import json
import re
import sys
import time
import urllib.parse
import urllib.request

API = "https://battlecats.miraheze.org/w/api.php"
UA = "TrackCats-dev/1.0 (personal fan tool)"


def api(**params):
    params["format"] = "json"
    req = urllib.request.Request(API + "?" + urllib.parse.urlencode(params), headers={"User-Agent": UA})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.load(r)
        except Exception:
            time.sleep(2 + attempt * 3)
    raise SystemExit("wiki did not answer")


def paged(key, **params):
    out = []
    while True:
        d = api(**params)
        out += d["query"][key]
        if "continue" not in d:
            return out
        params.update(d["continue"])


def rank(name):
    """Lower is better: English banner first, then the plain one. Other languages are skipped."""
    low = name.lower()
    if "placeholder" in low or re.search(r"[_(](ja|jp|kr|tw)\b", low):
        return None
    if re.fullmatch(r"gatya_bnr\d+_en\.png", low):
        return 0
    if re.fullmatch(r"gatya_bnr\d+\.png", low):
        return 1
    if "_en" in low:
        return 2
    return 3


def main(data_path, out_path):
    data = json.load(open(data_path, encoding="utf-8"))
    cats = data["cats"]
    files = [x["name"] for x in paged("allimages", action="query", list="allimages", aiprefix="Gatya_bnr", ailimit=500)]
    best = {}
    for f in files:
        m = re.match(r"Gatya_bnr(\d+)", f)
        r = rank(f)
        if not m or r is None:
            continue
        gid = int(m.group(1))
        if gid not in best or r < best[gid][0]:
            best[gid] = (r, f)

    def ubers(gid):
        return {c for c in data["gacha"].get(str(gid), []) if cats.get(str(c), {}).get("r", 0) >= 4}

    wanted = sorted({e["id"] for e in data["events"]})
    result = {g: best[g][1] for g in wanted if g in best}

    # For the rest, borrow the banner of the wiki's page for the same set: the page whose
    # cat icons overlap this pool's uber and legend rares the most.
    missing = [g for g in wanted if g not in result]
    names = {}
    if True:
        pages = [p["title"] for p in paged("categorymembers", action="query", list="categorymembers",
                                           cmtitle="Category:Gacha Event", cmlimit=500) if "/" not in p["title"]]
        sets = []
        for title in pages:
            imgs = [i["title"] for i in paged("pages", action="query", titles=title, prop="images", imlimit=500, formatversion=2)[0].get("images", [])]
            banner = sorted((rank(i[5:].replace(" ", "_")), i[5:].replace(" ", "_")) for i in imgs
                            if i.startswith("File:Gatya bnr") and rank(i[5:].replace(" ", "_")) is not None)
            ids = {int(m.group(1)) + 1 for i in imgs for m in [re.match(r"File:Uni ?(\d+)", i)] if m}
            ids = {c for c in ids if cats.get(str(c), {}).get("r", 0) >= 4}
            if banner and ids:
                sets.append((title, banner[0][1], ids))
            time.sleep(0.2)
        for g in missing:
            mine = ubers(g)
            if not mine:
                continue
            score, title, f = max(((len(mine & ids) / len(mine | ids), t, f) for t, f, ids in sets), default=(0, "", ""))
            if score >= 0.5:
                result[g] = f
                print(f"  {g}: borrowed from {title} ({score:.2f})")
        # Set names (for example "THE DYNAMITES") come from the title of the best-matching wiki page.
        for g in wanted:
            mine = ubers(g)
            if not mine:
                continue
            score, title = max(((len(mine & ids) / len(mine | ids), t) for t, f, ids in sets), default=(0, ""))
            if score >= 0.5:
                names[g] = re.sub(r"\s*\((Gacha Event|Event Gacha|Removed Gacha Event)\)\s*$", "", title)
    json.dump({str(k): v for k, v in sorted(result.items())}, open(out_path, "w", encoding="utf-8"), separators=(",", ":"))
    json.dump({str(k): v for k, v in sorted(names.items())}, open(out_path.replace("banner_art", "banner_names"), "w", encoding="utf-8"),
              ensure_ascii=False, separators=(",", ":"))
    print(f"{len(names)} of {len(wanted)} gacha ids have a set name")
    left = [g for g in wanted if g not in result]
    print(f"{len(result)} of {len(wanted)} gacha ids have banner art; without: {left}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
