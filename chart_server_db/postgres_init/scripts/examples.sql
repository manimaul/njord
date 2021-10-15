WITH tile_bounds AS (VALUES (st_transform(st_tileenvelope(?, ?, ?), 4326)))
SELECT st_asbinary(st_intersection(geom, (table tile_bounds))), props
FROM features
WHERE chart_id=?
  AND layer=?
  AND st_intersects(geom, (table tile_bounds));

---------------------

WITH tile_bounds AS (VALUES (ST_GeomFromWKB(?, 4326))),
     box AS (VALUES (ST_MakeBox2D(ST_Point(0, 0), ST_Point(4096, 4096))))
SELECT ST_AsBinary(ST_AsMVTGeom(ST_Intersection(geom, (table tile_bounds)), (table box))), props
FROM features
WHERE chart_id=?
  AND layer=?
  AND ST_Intersects(geom, (table tile_bounds));
---------------------

WITH tile_bounds AS (VALUES (st_transform(st_tileenvelope(?, ?, ?), 4326)))
SELECT st_asbinary(st_asmvtgeom(geom, (table tile_bounds))), props
FROM features
WHERE chart_id=?
  AND layer=?
  AND st_intersects(geom, (table tile_bounds));

---------------------

SELECT st_asbinary(st_asmvtgeom(f.geom, bounds)), props
FROM st_transform(st_tileenvelope(?, ?, ?), 4326) AS bounds
         INNER JOIN charts
                    ON st_intersects(covr, bounds)
    --AND zoom BETWEEN 0 AND ?
         INNER JOIN features f
                    ON charts.id = f.chart_id
                        AND st_intersects(geom, bounds)
ORDER BY scale;

---------------------
---------------------
---------------------
