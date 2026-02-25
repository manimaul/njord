#!/usr/bin/env bash

helm repo add nfs-ganesha-server-and-external-provisioner \
  https://kubernetes-sigs.github.io/nfs-ganesha-server-and-external-provisioner/

helm install nfs-server nfs-ganesha-server-and-external-provisioner/nfs-server-provisioner \
  --namespace njord \
  --set persistence.enabled=true \
  --set persistence.storageClass=linode-block-storage-retain \
  --set persistence.size=10Gi \
  --set storageClass.name=nfs