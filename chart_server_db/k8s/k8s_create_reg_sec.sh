#!/usr/bin/env bash

#echo <secret> | gpg -r 81983EFD28C5BC31F74813E4369716D4576FF50F --armor --encrypt

gh_token_enc=$(cat <<EOM
-----BEGIN PGP MESSAGE-----

hQIMA5AJyFwTNT3gAQ/8C+MfXevQgbLPxrMGL87RiG8QlA5UtF/+w37IObdHnPg0
NbpNOEWSMlBwgmA0NSt3R3m49sHBokInpvke161twIEaYCiUJ9dqcJ/lMEyDNJiu
XbQn99xQg9wTJmvgGeLntjb9qFx32p4XvZ7QW9q2I2sCe2Y5AE3gBkLq3k0gzwB+
L78SBKINdzoCi58LIv0VXG9a2Bv0baty7ca9/J+WZL1W11bUGS1BQ+6ggDEaBxst
5PCHMJoJ0VSlwTFs8qiclBkxWGqozvzaesWwDWI7cO7YjWIXlvybnK1SJIIw8pf3
+fyKdZDfs/+UJGJr3LwxlAGeusbWE9pefHjisK+VKODI7w5e1a5zPO3HlX36mh0p
vJonX+dCuGmqzBov7aUGOBban5rRt59MmXXNpC7SxOMUEi8PYtbJgJzHnAANZ+Da
Ww5s8nxEi7T3lNuHO/R4boz59q8bjVA09Z4olVNgmm1IOIY3pHbjfRgXPoWO0zGc
0cvf0+V4GS2/T/Udd9j7u65cWgij0MDenXmWd1vB0OZbhacyco77dZASlBmEfvGR
/6/SUM2X++bs734LHStdBvk0kma7FqLhjfngE3oE/O7UP7dZwmnn0eSRdbFEw801
52DnsBLW3z+By0NzScDzYx3Y7/WBX7fKnVc5NXZYsnOtmIJRBP1cYZkATNqxtPnS
ZAGdMDpnWJQQKNRUA+jmcFiegy8KVz+LrIZnjlwGzZCBo2y6MMtg55//XwRiD80t
ng7AAWP7M55WNYN1HnHevkYqMQb3PrbQJkAHy1EYzjCTsEjJuwrjTS2Rf66iDaMh
ZAXrVZA=
=tENP
-----END PGP MESSAGE-----
EOM
)
gh_token=$(echo "$gh_token_enc" | gpg -d 2>/dev/null)

echo "$gh_token"

gh_user="manimaul"
sec_encoded=$(echo -n "$gh_user:$gh_token" | base64)
auth_json="{\"auths\":{\"ghcr.io\":{\"auth\":\"$sec_encoded\"}}}"

echo "encoding json:"
#echo "$auth_json" | jq
encoded=$(echo -n  "{\"auths\":{\"ghcr.io\":{\"auth\":\"$sec_encoded\"}}}" | base64)

yaml=$(cat <<EOM
kind: Secret
type: kubernetes.io/dockerconfigjson
apiVersion: v1
metadata:
  name: ghreg
  namespace: njord
data:
  .dockerconfigjson: $encoded
EOM
)

#echo "$yaml"
echo "$yaml" | kubectl apply -f -
