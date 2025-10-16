import argparse, json, sys
from typing import List, Tuple

def _perp_dist(p, a, b):
    (x, y), (x1, y1), (x2, y2) = p, a, b
    if (x1 == x2) and (y1 == y2):
        return ((x - x1)**2 + (y - y1)**2) ** 0.5
    num = abs((y2 - y1)*x - (x2 - x1)*y + x2*y1 - y2*x1)
    den = ((y2 - y1)**2 + (x2 - x1)**2) ** 0.5
    return num / den

def rdp(points: List[Tuple[float, float]], epsilon: float) -> List[Tuple[float, float]]:
    if len(points) < 3: return points
    dmax, idx, end = 0.0, 0, len(points) - 1
    for i in range(1, end):
        d = _perp_dist(points[i], points[0], points[end])
        if d > dmax: idx, dmax = i, d
    if dmax > epsilon:
        a = rdp(points[: idx + 1], epsilon)
        b = rdp(points[idx:], epsilon)
        return a[:-1] + b
    return [points[0], points[end]]

def _iter_rings_from_geometry(geom, include_holes=False):
    t = geom.get("type")
    c = geom.get("coordinates")
    if t == "Polygon":
        for i, ring in enumerate(c):
            if i == 0 or include_holes: yield ring
    elif t == "MultiPolygon":
        for poly in c:
            for i, ring in enumerate(poly):
                if i == 0 or include_holes: yield ring
    elif t == "GeometryCollection":
        for g in geom.get("geometries", []):
            for ring in _iter_rings_from_geometry(g, include_holes): yield ring

def norm_lon(lon: float) -> float:
    while lon <= -180: lon += 360
    while lon > 180: lon -= 360
    return lon

def split_on_dateline(pts: List[Tuple[float, float]]) -> List[List[Tuple[float, float]]]:
    if not pts: return []
    out, cur = [], [pts[0]]
    for i in range(1, len(pts)):
        lon1, lat1 = cur[-1]
        lon2, lat2 = pts[i]
        if abs(lon2 - lon1) > 180:   # crossing the seam
            out.append(cur)
            cur = []
        cur.append((lon2, lat2))
    if cur: out.append(cur)
    return out

def main():
    ap = argparse.ArgumentParser(description="GeoJSON polygons -> countries.txt (lon,lat per line; blank line between paths).")
    ap.add_argument("input_geojson")
    ap.add_argument("output_txt")
    ap.add_argument("--every", type=int, default=1)
    ap.add_argument("--epsilon", type=float, default=0.0)
    ap.add_argument("--include-holes", action="store_true")
    args = ap.parse_args()

    try:
        with open(args.input_geojson, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception as e:
        print(f"Failed to read GeoJSON: {e}", file=sys.stderr); sys.exit(1)

    feats = data.get("features") or [{"type": "Feature", "geometry": data}]
    with open(args.output_txt, "w", encoding="utf-8") as out:
        for feat in feats:
            geom = feat.get("geometry", {})
            for ring in _iter_rings_from_geometry(geom, include_holes=args.include_holes):
                pts = []
                for p in ring:
                    if isinstance(p, (list, tuple)) and len(p) >= 2:
                        pts.append((norm_lon(float(p[0])), float(p[1])))

                if len(pts) >= 2 and pts[0] == pts[-1]: pts = pts[:-1]
                if args.epsilon > 0 and len(pts) > 2:  pts = rdp(pts, args.epsilon)
                if args.every > 1:                     pts = pts[::args.every]

                for part in split_on_dateline(pts):
                    if len(part) < 2: continue
                    for lon, lat in part: out.write(f"{lon},{lat}\n")
                    out.write("\n")

    print(f"wrote {args.output_txt}")

if __name__ == "__main__":
    main()
