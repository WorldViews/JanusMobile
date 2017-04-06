#!/bin/bash
watchman watch-del-all
rm -rf node_modules && npm install
rm -fr $TMPDIR/react-*
