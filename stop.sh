#!/bin/zsh
# Stops the CareerSphere backend (8080) and frontend (5173). MySQL keeps running.
for port in 8080 5173; do
  pids="$(/usr/sbin/lsof -nP -t -iTCP:$port -sTCP:LISTEN 2>/dev/null)"
  if [ -n "$pids" ]; then
    echo "Stopping the server on port $port"
    kill $pids
  else
    echo "Nothing running on port $port"
  fi
done
