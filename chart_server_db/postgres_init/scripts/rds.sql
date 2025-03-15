-- https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Appendix.PostgreSQL.CommonDBATasks.PostGIS.html#Appendix.PostgreSQL.CommonDBATasks.PostGIS.Connect

CREATE EXTENSION postgis;
CREATE EXTENSION postgis_raster;
CREATE EXTENSION fuzzystrmatch;
CREATE EXTENSION postgis_tiger_geocoder;
CREATE EXTENSION postgis_topology;
CREATE EXTENSION address_standardizer_data_us;

ALTER SCHEMA tiger OWNER TO admin;
ALTER SCHEMA tiger_data OWNER TO admin;
ALTER SCHEMA topology OWNER TO admin;
