#!/usr/bin/env bash

#echo <secret> | gpg -r manimaul@gmail.com --armor --encrypt
gh_token_enc=$(cat <<EOM
-----BEGIN PGP MESSAGE-----

hQIMA5AJyFwTNT3gAQ/9F9dEQftRWqHWWnlr9EYQ/9bPRuRi/exMwhlYeQrYGgax
d/z7jOKLl4WJP0a7aC7IEh7mnUXfEy+lEKypgKpvDhgCNXJSYGc7pNlOhVB2Udm6
vhRR0J3qQxzeoJwM2dCOZ4fz6w/MVgqhe5Bm8nqixxr3hkbLiUnuV7O9oVnAeOtm
z4N0oZH0tu1oIUd/0dwfOqO2JBuTaNJaGNzugM99i9PRxfDc4FF5Y2KNw1gAhPLx
L+Yb7ocWW+Q2impFHI3kx4oX3c5gbhq1l8GeqLpWGnP8urMoJPiAufrh3C5Eb9kQ
XC+e+d2fNhKI853pHc5l7au2KIjOIjkByKMJ3fGt5Gpfz6A3koc+0fU9DCRfoEiX
kfwSbWOoQI/+fGPQO6+95Md04SPkMETUK7fQwdozbQSiIY7yiXvuXfDY/M06LEmA
GtIUbVNLcRCOTEQXSjA7kKwNKApRiFb3a85pAfTxV9dC7Cp/b6/p/Aw3B3OY8V2n
P11CuLLGXHUgyyJcuCE8nYRkblJUeCU2Pz7PDVYHDRDkJXSnGvX0rM0WkF+QGut2
dgwadgSmK6aNvdESLfLU0TQm2Q8PmmHAOK4Ja8Dukhi6PEFkGqWu6x6jyXLWRDE8
f+8sk58K5ZXVlwsu5dvzzDeoDk8rmNFsKt+C7GiVBL4S9sYim5XEUMneyFXhxs3S
ZAFEwAS1S2vWiNFLGdZOUnDwaMfRJefSrMizfTEURwLqLg8OonSOHvuvVhknhFUp
B2zQiWEmWRceX+W+GNaCcRKKVq5CsoWVEDcyCYcKpTSeJqY5wFCVixlZaWNA3dta
M5KYGGA=
=GI91
-----END PGP MESSAGE-----
EOM
)

gh_token=$(echo "$gh_token_enc" | gpg -d 2>/dev/null)
gh_user="manimaul"
sec_encoded=$(echo -n "$gh_user:$gh_token" | base64)

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

function k8s_login() {
  echo "$yaml" | kubectl apply -f -
}

print_token() {
  echo "user = $gh_user"
  echo "token = $gh_token"
}

docker_login() {
  echo "$gh_token" | docker login ghcr.io -u manimaul --password-stdin
}

help() {
   echo "Login to the GitHub Container Registry"
   echo
   echo "arguments:"
   echo "print_token  Print the token"
   echo "docker_login Login to docker"
   echo "k8s_login    Add k8s container pull credential secret to the njord namespace"
   echo "help         Print this Help."
   echo
}

eval "$1"

