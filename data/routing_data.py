"""Aiuti per la pipeline dei dati di percorso (#3). Solo libreria standard.

  python routing_data.py matrix   REGIONS.json                 -> lista degli id (JSON) per la matrice
  python routing_data.py extract  REGIONS.json OUTDIR          -> configurazione per `osmium extract`
  python routing_data.py fragment ID BBOX_JSON FILE TAR OSM_DATE VALHALLA OUT.json
  python routing_data.py manifest FRAGMENTS_DIR OUT.json       -> regions.json letto dall'app
"""
import glob
import hashlib
import json
import os
import sys

FORMAT = 1  # cambia solo se l'app non può più leggere il manifest


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def matrix(regions_path):
    print(json.dumps([r["id"] for r in load(regions_path)["regions"]]))


def extract(regions_path, outdir):
    cfg = {
        "directory": outdir,
        "extracts": [
            {"output": r["id"] + ".osm.pbf", "bbox": r["bbox"]}
            for r in load(regions_path)["regions"]
        ],
    }
    print(json.dumps(cfg, indent=2))


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fragment(region_id, bbox_json, file_path, tar_path, osm_date, valhalla, out):
    data = {
        "id": region_id,
        "bbox": json.loads(bbox_json),
        "file": os.path.basename(file_path),
        "bytes": os.path.getsize(file_path),
        "sha256": sha256(file_path),
        "tarBytes": os.path.getsize(tar_path),  # spazio occupato sul telefono dopo la decompressione
        "osmDate": osm_date,
        "valhalla": valhalla,
    }
    with open(out, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)


def manifest(fragments_dir, out):
    regions = [load(p) for p in sorted(glob.glob(os.path.join(fragments_dir, "**", "*.json"), recursive=True))]
    if not regions:
        sys.exit("nessuna zona costruita")
    data = {"format": FORMAT, "regions": regions}
    with open(out, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)


if __name__ == "__main__":
    cmd, args = sys.argv[1], sys.argv[2:]
    {"matrix": matrix, "extract": extract, "fragment": fragment, "manifest": manifest}[cmd](*args)
