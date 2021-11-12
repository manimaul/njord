docker run -v $(pwd):/hpgl -it hpgl bash

hp2xx -c 12340567 -m png -r 180 ./achare51.hpgl
hp2xx -t -c 12340567 -m png ./bcnlat15.hpgl
hp2xx -C -w 2000 -h 2000 -m png ./bcnlat15.hpgl