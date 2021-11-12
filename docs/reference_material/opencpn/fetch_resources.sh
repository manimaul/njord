#!/usr/bin/env bash

dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

#https://github.com/OpenCPN/OpenCPN/tree/master/data/s57data
#https://opencpn.org/wiki/dokuwiki/doku.php?id=opencpn:opencpn_user_manual:advanced_features:vector_palette

array=( 'chartsymbols.xml' 'rastersymbols-dark.png' 'rastersymbols-day.png' \
'rastersymbols-dusk.png' 's57attributes.csv' 's57expectedinput.csv' 's57objectclasses.csv')

for u in "${array[@]}"
do
    curl "https://raw.githubusercontent.com/OpenCPN/OpenCPN/master/data/s57data/$u" -o \
"${dir}/$u"

done