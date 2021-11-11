#!/usr/bin/env bash

#echo <secret> | gpg -r <recipient_id> --armor --encrypt
gh_token_enc=$(cat <<EOM
-----BEGIN PGP MESSAGE-----

hQIMA5AJyFwTNT3gAQ//TxkNcElRatMWkccRyGgoylwhrNAGgwTMrBTZ80eUNmr9
gu8vU6av4uvpqAUCNx2xzft/lkfI9Jmc0jI65eQT0TqoAzLnqaUgv9oRGE9B6pb1
VHPzmuj8ErXj5UjRhy88pUClbJPdNC6q4x6xfSZNw/NA9e3dOz52qvW5WwzZGYHy
KEecCIEF0jqkWG81v5UWAT2v/yd90QyrhHoMGhrhA16JOFEM9ymw/O3ndi9EiGLZ
OHJiLQlF7fGb6e3yGP0vKCYGT+R6/pJ68MSQrgHV/G4042cQHYEks7Ak3+Nkjqkg
xuqJumJeU4CpUQkUEURVY1oc3LTxo8Qvnnj+skxmn1HMXGVUd8897i5ys4OuMBUC
vzLhP9TlJpKMAQ7ACE0TfYd169uiEKIm8d3Kpw60DLYXnp/el7yrOqMBkTwpG877
pADnA3HwEQ0wDHBfVmsfBE3Mz7SZgp9RaGxkT6Xe0mdDfifiC+COlTCkTpeGiN8w
t1YH/cVgNioQ5eat5ujot8SN0LpjIR4XvviObebN8xpn0G0F1btgg/N+mEXBgD/K
6kwLh7+mvSy4PBj7B097uHTqL/p/EWsshzD5l8PCYSiuVHdxMU3/hxW7pR5rLHy4
FOep2Po8paIrlX2cJDnlRIXnmx+1+M2PYzBhu1uJ7FIKnQFHQZOSNU2xcjevzFjS
ZAFw0yYcG0dAOZIlL6HoNXjpZRKTV7y2YLbspbiZ+ZTw5MwEp7Y9AfEwv7IWL9lV
i1OmSyGVlMMupftTHeXNsdoC/q8NJlG7Ufj9iF0P8R4URYjz8gbIpHwDw3t540Kc
Hb+EZ8Y=
=Twq+
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

