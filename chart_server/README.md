# Chart Server

## todo:
 - layer occlusion 
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