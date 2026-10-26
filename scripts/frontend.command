#!/bin/zsh
# CareerSphere frontend (Vite on port 5173). Opened by start-servers.sh; you can also double-click it.
printf '\e]0;CareerSphere frontend\a'
cd "$(dirname "$0")/../frontend" || exit 1
if [ ! -d node_modules ]; then
  echo "Installing frontend packages (first time only)…"
  npm install || exit 1
fi
echo "CareerSphere frontend: http://localhost:5173   (press Ctrl+C or close this window to stop)"
echo
npm run dev
