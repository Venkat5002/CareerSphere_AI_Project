#!/bin/zsh
# Starts CareerSphere and opens it in your browser.   Usage: ./run.sh      Stop with: ./stop.sh
ROOT="$(cd "$(dirname "$0")" && pwd)"
/bin/zsh "$ROOT/scripts/start-servers.sh" --wait || exit 1
open http://localhost:5173
