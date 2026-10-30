#!/usr/bin/env python3
"""Build the two synthetic S-57 cells under testdata/fixtures/synthetic/.

Groundwork item for RUST_PORT_PHASE0_CHECKLIST.md §1: two tiny cells with overlapping
coverage at different compilation scales, so TE-1 / P-TE-5 can prove chart priority masking
(the larger-scale cell wins inside the overlap, the smaller-scale cell still shows outside it).

    US4ZZ01M.000  approach, 1:80000   (zoom 11 at this latitude)  the "outer" cell
    US5ZZ02M.000  harbour,  1:10000   (zoom 14)                   the "inner" cell, fully inside the outer

"ZZ" is not a US state code, so these names can never collide with a real NOAA cell.
Both lie in open water off the Washington coast, far from US5WA22M, so a database holding all
three fixture cells has no unplanned overlaps.

GDAL's S-57 writer does not build topology from feature geometry: every point needs an
isolated node (VI), every line / ring an edge (VE) between connected nodes (VC), and the
feature's FSPT pointers (NAME_RCNM/NAME_RCID/ORNT/USAG/MASK) must reference them. This script
does that bookkeeping. The DSID/DSPM header comes from dataset creation options; DSID_DSNM is
taken from the file name.

Requires the GDAL Python bindings (brew install gdal). Run from anywhere:

    python3 testdata/tools/make_synthetic_cells.py [out_dir]

Re-running must produce byte-identical files; the layout below is the fixture's definition.
"""
import os
import sys

from osgeo import gdal, ogr

gdal.UseExceptions()

RCNM_VI, RCNM_VC, RCNM_VE = 110, 120, 130
PRIM_P, PRIM_L, PRIM_A = 1, 2, 3
NOAA = 550

# OBJL codes from s57objectclasses.csv
OBJL = {
    "M_COVR": 302,
    "DEPARE": 42,
    "DEPCNT": 43,
    "LNDARE": 71,
    "BOYLAT": 17,
    "BOYSPP": 19,
    "SOUNDG": 129,
    "LIGHTS": 75,
}

# Bounding boxes, (west, south, east, north). The inner box is strictly inside the outer.
OUTER = (-123.20, 46.90, -122.80, 47.10)
INNER = (-123.05, 46.97, -122.95, 47.03)
# A point both cells place a feature at (inside the overlap). Offset slightly so the two
# features are distinguishable on a tile.
OVERLAP_PT_OUTER = (-123.000, 47.000)
OVERLAP_PT_INNER = (-122.990, 47.005)
# Points only the outer cell covers.
OUTSIDE_PT_WEST = (-123.15, 47.05)
OUTSIDE_PT_EAST = (-122.85, 46.95)


def box_ring(b):
    w, s, e, n = b
    # counter-clockwise in lon/lat, closed
    return [(w, s), (e, s), (e, n), (w, n), (w, s)]


class CellWriter:
    """Tracks primitive record ids and writes features with correct FSPT linkage."""

    def __init__(self, path, cscl, intu, counts, comment):
        self.path = path
        opts = [
            "S57_EXPP=1",
            f"S57_INTU={intu}",
            "S57_EDTN=1",
            "S57_UPDN=0",
            "S57_UADT=20260101",
            "S57_ISDT=20260101",
            "S57_STED=03.1",
            f"S57_AGEN={NOAA}",
            f"S57_COMT={comment}",
            # Lexical level 1 (ISO 8859-1) like NOAA cells; OgrS57Dataset reads DSSI_NALL
            # to pick the charset for the .TXT sidecars.
            "S57_AALL=1",
            "S57_NALL=1",
            f"S57_NOMR={counts['meta']}",
            f"S57_NOGR={counts['geo']}",
            "S57_NOLR=0",
            f"S57_NOIN={counts['vi']}",
            f"S57_NOCN={counts['vc']}",
            f"S57_NOED={counts['ve']}",
            "S57_HDAT=2",   # WGS 84
            "S57_VDAT=16",  # mean lower low water
            "S57_SDAT=12",  # mean low water springs - matches US5WA22M
            f"S57_CSCL={cscl}",
            "S57_COMF=10000000",
            "S57_SOMF=10",
        ]
        self.ds = ogr.GetDriverByName("S57").CreateDataSource(path, options=opts)
        self.vi = self.ds.GetLayerByName("IsolatedNode")
        self.vc = self.ds.GetLayerByName("ConnectedNode")
        self.ve = self.ds.GetLayerByName("Edge")
        self.next_vi = self.next_vc = self.next_ve = 1
        self.next_fe = 1
        self.next_fidn = 1000

    # -- primitives ---------------------------------------------------------------------------

    def _node(self, layer, rcnm, rcid, geom):
        f = ogr.Feature(layer.GetLayerDefn())
        f.SetField("RCNM", rcnm)
        f.SetField("RCID", rcid)
        f.SetField("RVER", 1)
        f.SetField("RUIN", 1)
        f.SetGeometry(geom)
        layer.CreateFeature(f)
        return rcid

    def isolated_node(self, geom):
        rcid = self.next_vi
        self.next_vi += 1
        return self._node(self.vi, RCNM_VI, rcid, geom)

    def connected_node(self, xy):
        rcid = self.next_vc
        self.next_vc += 1
        p = ogr.Geometry(ogr.wkbPoint)
        p.AddPoint_2D(*xy)
        return self._node(self.vc, RCNM_VC, rcid, p)

    def edge(self, coords, start_vc, end_vc):
        """coords includes the start and end node positions. The writer stores every vertex
        it is given as SG2D and the reader prepends/appends the node positions, so only the
        interior vertices go into the edge geometry."""
        rcid = self.next_ve
        self.next_ve += 1
        f = ogr.Feature(self.ve.GetLayerDefn())
        f.SetField("RCNM", RCNM_VE)
        f.SetField("RCID", rcid)
        f.SetField("RVER", 1)
        f.SetField("RUIN", 1)
        f.SetField("NAME_RCNM_0", RCNM_VC)
        f.SetField("NAME_RCID_0", start_vc)
        f.SetField("ORNT_0", 255)
        f.SetField("USAG_0", 255)
        f.SetField("TOPI_0", 1)  # beginning node
        f.SetField("MASK_0", 255)
        f.SetField("NAME_RCNM_1", RCNM_VC)
        f.SetField("NAME_RCID_1", end_vc)
        f.SetField("ORNT_1", 255)
        f.SetField("USAG_1", 255)
        f.SetField("TOPI_1", 2)  # end node
        f.SetField("MASK_1", 255)
        ls = ogr.Geometry(ogr.wkbLineString)
        for xy in coords[1:-1]:
            ls.AddPoint_2D(*xy)
        f.SetGeometry(ls)
        self.ve.CreateFeature(f)
        return rcid

    # -- features ---------------------------------------------------------------------------

    def _feature(self, layer_name, prim, grup, attrs, fspt):
        layer = self.ds.GetLayerByName(layer_name)
        if layer is None:
            raise SystemExit(f"S57 writer has no layer {layer_name}")
        f = ogr.Feature(layer.GetLayerDefn())
        f.SetField("RCID", self.next_fe)
        self.next_fe += 1
        f.SetField("PRIM", prim)
        f.SetField("GRUP", grup)
        f.SetField("OBJL", OBJL[layer_name])
        f.SetField("RVER", 1)
        f.SetField("AGEN", NOAA)
        f.SetField("FIDN", self.next_fidn)
        self.next_fidn += 1
        f.SetField("FIDS", 1)
        for k, v in attrs.items():
            f.SetField(k, v)
        rcnm, rcid, ornt, usag, mask = (list(x) for x in zip(*fspt))
        f.SetFieldIntegerList(f.GetFieldIndex("NAME_RCNM"), rcnm)
        f.SetFieldIntegerList(f.GetFieldIndex("NAME_RCID"), rcid)
        f.SetFieldIntegerList(f.GetFieldIndex("ORNT"), ornt)
        f.SetFieldIntegerList(f.GetFieldIndex("USAG"), usag)
        f.SetFieldIntegerList(f.GetFieldIndex("MASK"), mask)
        layer.CreateFeature(f)

    def point(self, layer_name, xy, attrs, grup=2):
        p = ogr.Geometry(ogr.wkbPoint)
        p.AddPoint_2D(*xy)
        vi = self.isolated_node(p)
        self._feature(layer_name, PRIM_P, grup, attrs, [(RCNM_VI, vi, 255, 255, 255)])

    def soundings(self, xyz_list, attrs, grup=2):
        mp = ogr.Geometry(ogr.wkbMultiPoint25D)
        for x, y, z in xyz_list:
            p = ogr.Geometry(ogr.wkbPoint25D)
            p.AddPoint(x, y, z)
            mp.AddGeometry(p)
        vi = self.isolated_node(mp)
        self._feature("SOUNDG", PRIM_P, grup, attrs, [(RCNM_VI, vi, 255, 255, 255)])

    def line(self, layer_name, coords, attrs, grup=2):
        a = self.connected_node(coords[0])
        b = self.connected_node(coords[-1])
        ve = self.edge(coords, a, b)
        self._feature(layer_name, PRIM_L, grup, attrs, [(RCNM_VE, ve, 1, 255, 255)])

    def area(self, layer_name, ring, attrs, grup, usag=1):
        """ring is a closed coordinate list; one connected node at ring[0], one closed edge."""
        assert ring[0] == ring[-1]
        n = self.connected_node(ring[0])
        ve = self.edge(ring, n, n)
        self._feature(layer_name, PRIM_A, grup, attrs, [(RCNM_VE, ve, 1, usag, 255)])

    def close(self):
        self.ds = None


def build_outer(out_dir):
    counts = dict(meta=1, geo=6, vi=3, vc=3, ve=3)
    w = CellWriter(
        os.path.join(out_dir, "US4ZZ01M.000"), cscl=80000, intu=4, counts=counts,
        comment="njord synthetic fixture - outer approach cell",
    )
    w.area("M_COVR", box_ring(OUTER), {"CATCOV": 1}, grup=2)
    w.area("DEPARE", box_ring(OUTER), {"DRVAL1": 10.0, "DRVAL2": 20.0}, grup=1)
    # A contour that crosses the inner cell's coverage so line clipping at the mask shows up.
    w.line("DEPCNT", [(-123.18, 47.00), (-123.00, 46.99), (-122.82, 47.00)], {"VALDCO": 10.0})
    w.point("BOYLAT", OVERLAP_PT_OUTER,
            {"BOYSHP": 4, "CATLAM": 1, "COLOUR": "4", "OBJNAM": "Outer Buoy In Overlap"})
    w.point("BOYLAT", OUTSIDE_PT_WEST,
            {"BOYSHP": 4, "CATLAM": 2, "COLOUR": "3", "OBJNAM": "Outer Buoy West"})
    w.point("LIGHTS", OUTSIDE_PT_EAST,
            {"CATLIT": "1", "COLOUR": "1", "LITCHR": 2, "SIGPER": 4.0, "VALNMR": 6.0,
             "OBJNAM": "Outer Light East"})
    w.close()


def build_inner(out_dir):
    counts = dict(meta=1, geo=5, vi=2, vc=3, ve=3)
    w = CellWriter(
        os.path.join(out_dir, "US5ZZ02M.000"), cscl=10000, intu=5, counts=counts,
        comment="njord synthetic fixture - inner harbour cell",
    )
    w.area("M_COVR", box_ring(INNER), {"CATCOV": 1}, grup=2)
    # Shallower than the outer cell's band, so the masked overlap is visibly different.
    w.area("DEPARE", box_ring(INNER), {"DRVAL1": 0.0, "DRVAL2": 5.0}, grup=1)
    w.line("DEPCNT", [(-123.04, 46.98), (-123.00, 47.00), (-122.96, 47.02)], {"VALDCO": 2.0})
    w.point("BOYSPP", OVERLAP_PT_INNER,
            {"BOYSHP": 1, "CATSPM": "27", "COLOUR": "6", "OBJNAM": "Inner Buoy In Overlap"})
    w.soundings(
        [(-123.03, 46.99, 1.2), (-123.01, 47.01, 3.4), (-122.97, 46.98, 4.5), (-122.98, 47.02, -0.6)],
        {"QUASOU": "1"},
    )
    w.close()


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "..", "fixtures", "synthetic")
    os.makedirs(out_dir, exist_ok=True)
    build_outer(out_dir)
    build_inner(out_dir)
    for name in ("US4ZZ01M.000", "US5ZZ02M.000"):
        ds = ogr.Open(os.path.join(out_dir, name))
        dsid = ds.GetLayerByName("DSID").GetNextFeature()
        covr = ds.GetLayerByName("M_COVR").GetNextFeature()
        print(f"{name}: DSNM={dsid['DSID_DSNM']} CSCL={dsid['DSPM_CSCL']} "
              f"M_COVR={covr.GetGeometryRef().ExportToWkt()}")
        for i in range(ds.GetLayerCount()):
            lyr = ds.GetLayer(i)
            n = lyr.GetFeatureCount()
            if n and lyr.GetName() != "DSID":
                print(f"   {lyr.GetName():7s} {n}")


if __name__ == "__main__":
    main()
