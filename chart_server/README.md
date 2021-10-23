# Chart Server

## todo:

 - DONE - chart occlusion 
    - geometry clipping - (eg great peninsula)
 - traditional raster theme
 - web address # location update
 - chart outlines
 - scamin / scamax test various latitudes and scales
 - findzoom optimizations (use chart latitude and/or caching)
   - remove MINZ, MAXZ props and calculate tile scale when tile is fetched
     - add query param options to adhere to SCAMIN or SCAMAX
 - move `/v1/content` serve root outside of resources
 - web ui to CRUD charts
 - containerize
 - k8s config
 - whirlyglobe client
 - divide features into layer sets (land, lights, etc) - this allows simpler configurability
 - maplibre clients https://github.com/maplibre/maplibre-gl-native


## geometry clipping issues: 
http://localhost:9000/v1/content/index.html#5.56/46.609/-128.481
DONE - http://localhost:9000/v1/content/index.html#11.03/47.1874/-122.8123 