#!/usr/bin/env bash

if [ -z "$DEST" ]; then
    echo "Missing DEST variable with the destination path (absolute or relative)"
    exit 1;
fi

cp build/browser-bundle.js "$DEST/build/"
cp css/* "$DEST/css/"
cp img/* "$DEST/img/"