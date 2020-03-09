#!/bin/sh -e

systemctl daemon-reload
systemctl enable njord.service
systemctl start njord.service
