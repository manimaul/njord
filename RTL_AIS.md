# RTL_AIS on Debian (Raspbian)

```bash
sudo apt install cmake libusb-dev libusb-1.0-0-dev
git clone https://github.com/osmocom/rtl-sdr.git && cd rtl-sdr && git checkout 1f0eafe60445339703903af6d8814ffab7e73784
mkdir build && cd build && cmake ..
make
sudo make install
```

```bash
 git clone https://github.com/dgiardini/rtl-ais.git && cd rtl-ais && git checkout 252f33b128f01fd1a6c3c4678a2d2ca58a0c453d
 make
 sudo mv rtl_ais /usr/local/bin
 sudo rtl_ais -P 10111 -n
```
