# Njord

Njord is a [Netty](https://netty.io/) based [NMEA-0183](https://en.wikipedia.org/wiki/NMEA_0183) TCP server.
Njord can be used to serve GPS and other ship data over a network to other software such as [OpenCPN](https://opencpn.org/).

Build a Debian package (Raspberry Pi Compatible)
```sh
 ./gradlew :server:buildDeb
``` 

Send package to pi where (192.168.86.31) is your pi ip address
```sh
scp ./server/build/distributions/njord_1.0~SNAPSHOT-1_all.deb  pi@192.168.86.31:/home/pi/njord.deb 
```

Watch Logs
```sh
sudo journalctl -u njord -f 
```

Check Service Status
```sh
sudo systemctl status njord.service 
```
 