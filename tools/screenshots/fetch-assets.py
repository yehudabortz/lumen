#!/usr/bin/env python3
"""Fetch public demo artwork. Raw third-party artwork stays in ignored build output."""
import html
import hashlib
import json
from pathlib import Path
import re
import sys
import urllib.parse
import urllib.request

DEST = Path(sys.argv[1])
DEST.mkdir(parents=True, exist_ok=True)
APPS = [
    ("YouTube", "com.google.android.youtube.tv"),
    ("Netflix", "com.netflix.ninja"),
    ("Prime Video", "com.amazon.amazonvideo.livingroom"),
    ("Hulu", "com.hulu.livingroomplus"),
    ("HBO Max", "com.wbd.stream"),
    ("Disney+", "com.disney.disneyplus"),
    ("Plex", "com.plexapp.android"),
    ("Spotify", "com.spotify.tv.android"),
    ("VLC", "org.videolan.vlc"),
    ("Kodi", "org.xbmc.kodi"),
    ("Peacock", "com.peacocktv.peacockandroid"),
    ("Paramount+", "com.cbs.ott"),
]


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Lumen-Screenshot-Fixtures/1.0"})
    with urllib.request.urlopen(req, timeout=30) as response:
        data = response.read(4 * 1024 * 1024 + 1)
    if len(data) > 4 * 1024 * 1024:
        raise RuntimeError("Artwork exceeds 4 MiB: " + url)
    return data


catalog = Path(__file__).with_name("artwork.json")
if catalog.exists() and "--refresh" not in sys.argv[2:]:
    fixtures = json.loads(catalog.read_text())
    for item in fixtures["apps"] + fixtures["cards"]:
        data = fetch(item["image"])
        if hashlib.sha256(data).hexdigest() != item["sha256"]:
            raise RuntimeError("Recorded artwork changed: " + item["asset"] + "; review a fresh --refresh capture")
        (DEST / item["asset"]).write_bytes(data)
        print("Recorded artwork:", item.get("name", item.get("title")), flush=True)
    (DEST / "fixtures.json").write_text(json.dumps(fixtures, indent=2) + "\n")
    sys.exit(0)


apps = []
for index, (name, package) in enumerate(APPS):
    source = "https://play.google.com/store/apps/details?" + urllib.parse.urlencode({"id": package, "hl": "en_US"})
    page = fetch(source).decode("utf-8")
    match = re.search(r'<meta[^>]+property="og:image"[^>]+content="([^"]+)"', page)
    if not match:
        raise RuntimeError("Missing public Play Store icon for " + name)
    image = html.unescape(match.group(1))
    asset = "app-%02d.img" % index
    (DEST / asset).write_bytes(fetch(image))
    apps.append({"name": name, "package": package, "asset": asset, "source": source, "image": image})
    print("App artwork:", name, flush=True)

cards = []
queries = ["earth glacier satellite", "Pacific Ocean", "earth aurora", "earth desert satellite", "coastline"]
titles = ["Earth from Orbit", "The Living Ocean", "Chasing the Aurora", "Across the Desert", "Wild Coastlines"]
title_terms = [("glacier", "ice"), ("ocean", "sea", "pacific"), ("aurora", "lights"), ("desert", "quarter", "sahara"), ("coast", "island", "bay")]
used = set()
for index, (query, title) in enumerate(zip(queries, titles)):
    parameters = {"q": query, "media_type": "image", "page_size": 100}
    if index == 2:
        parameters["year_start"] = 2015
    source = "https://images-api.nasa.gov/search?" + urllib.parse.urlencode(parameters)
    entries = json.loads(fetch(source))["collection"]["items"]
    for entry in entries:
        meta = entry["data"][0]
        nasa_id = meta["nasa_id"]
        text = (meta.get("title", "") + " " + meta.get("description", "")).lower()
        if not any(term in meta.get("title", "").lower() for term in title_terms[index]):
            continue
        subject = meta.get("title", "").lower()
        if nasa_id in used or any(term in text for term in ("artist", "illustration", "concept", "simulation",
                "jupiter", "mars", "saturn", "comparison", "diagram")):
            continue
        if any(term in subject for term in ("portrait", "astronaut", "launch", "rocket", "rollback", "spacecraft", "kennedy",
                "ceremony", "technician", "engineer", "facility", "personnel", "map", "data product", "spartan", "freeflying",
                "srtm", "perspective", "overlay", "model", "rendering")):
            continue
        assets = json.loads(fetch(entry["href"]))
        urls = assets if isinstance(assets, list) else [item["href"] for item in assets["collection"]["items"]]
        renditions = [url for url in urls if url.endswith("~medium.jpg")]
        if not renditions:
            continue
        image = renditions[0].replace("http://", "https://")
        asset = "card-%02d.jpg" % index
        (DEST / asset).write_bytes(fetch(image))
        cards.append({"title": title, "asset": asset, "nasa_id": nasa_id, "image_title": meta["title"],
                      "credit": meta.get("photographer", meta.get("center", "NASA")), "image": image,
                      "source": "https://images.nasa.gov/details/" + urllib.parse.quote(nasa_id)})
        used.add(nasa_id)
        print("Sample card artwork:", meta["title"], flush=True)
        break
    else:
        raise RuntimeError("No suitable NASA photo for " + query)

for item in apps + cards:
    item["sha256"] = hashlib.sha256((DEST / item["asset"]).read_bytes()).hexdigest()
(DEST / "fixtures.json").write_text(json.dumps({"apps": apps, "cards": cards}, indent=2) + "\n")
