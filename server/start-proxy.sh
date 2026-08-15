#!/usr/bin/env bash
# Kimi proxy for opencode + the app. Dies with the terminal unless detached.
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec node --no-node-snapshot "$DIR/dist/index.js"
