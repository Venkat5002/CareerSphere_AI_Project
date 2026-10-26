#!/bin/zsh
# Starts CareerSphere: MySQL, the backend (Spring Boot, port 8080) and the frontend (Vite, port 5173).
#
# The backend and frontend each open in their own Terminal window, so they keep running and you can
# read their logs. Anything already running is left alone.
#
#   ./scripts/start-servers.sh          start, don't wait
#   ./scripts/start-servers.sh --wait   start and wait until both answer (used by run.sh and Xcode)
#
# Lines starting with "error:" show up as red errors in Xcode.

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WAIT=0
[ "$1" = "--wait" ] && WAIT=1

# Xcode runs scripts with a minimal PATH, so add Homebrew, mise and the usual places.
export PATH="/opt/homebrew/bin:/opt/homebrew/sbin:/usr/local/bin:$HOME/.local/share/mise/shims:$HOME/.local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

fail() {
  echo "error: $1"
  [ -n "$2" ] && echo "error: $2"
  exit 1
}

listening() { /usr/sbin/lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }

# Files downloaded from the internet are quarantined by macOS; clear that so the
# Terminal windows are allowed to open, and make sure the scripts are executable.
/usr/bin/xattr -dr com.apple.quarantine "$ROOT/scripts" 2>/dev/null
chmod +x "$ROOT"/scripts/*.sh "$ROOT"/scripts/*.command "$ROOT"/*.sh 2>/dev/null

# ---------- check the tools
command -v mvn >/dev/null 2>&1 || fail "Maven (mvn) not found." "Install it in Terminal with: brew install maven"
command -v npm >/dev/null 2>&1 || fail "Node.js (npm) not found." "Install it in Terminal with: brew install node"
/usr/libexec/java_home -v 17+ >/dev/null 2>&1 || command -v java >/dev/null 2>&1 \
  || fail "Java 17 or newer not found." "Install it in Terminal with: brew install openjdk@21"

# ---------- MySQL
if command -v mysqladmin >/dev/null 2>&1; then
  if ! mysqladmin -u root --connect-timeout=2 ping >/dev/null 2>&1; then
    echo "Starting MySQL…"
    brew services start mysql >/dev/null 2>&1
    for i in {1..15}; do
      mysqladmin -u root --connect-timeout=2 ping >/dev/null 2>&1 && break
      sleep 1
    done
  fi
  mysqladmin -u root --connect-timeout=2 ping >/dev/null 2>&1 \
    || fail "MySQL is not running." "Start it in Terminal with: brew services start mysql"
else
  fail "MySQL not found." "Install it in Terminal with: brew install mysql && brew services start mysql"
fi

# ---------- backend and frontend, each in its own Terminal window
if listening 8080; then
  echo "Backend already running on port 8080"
else
  echo "Opening the backend in a new Terminal window…"
  open -a Terminal "$ROOT/scripts/backend.command" || fail "Could not open Terminal for the backend."
fi

if listening 5173; then
  echo "Frontend already running on port 5173"
else
  echo "Opening the frontend in a new Terminal window…"
  open -a Terminal "$ROOT/scripts/frontend.command" || fail "Could not open Terminal for the frontend."
fi

[ "$WAIT" = 1 ] || exit 0

# ---------- wait until both answer (the first start downloads libraries, so allow 6 minutes)
up() { curl -fsS -o /dev/null --max-time 2 "$1" 2>/dev/null; }
echo "Waiting for CareerSphere to start (the first start can take a few minutes)…"
for i in {1..180}; do
  backend=0; frontend=0
  up http://localhost:8080/api/health && backend=1
  up http://localhost:5173 && frontend=1
  if [ $backend = 1 ] && [ $frontend = 1 ]; then
    echo "CareerSphere is running: http://localhost:5173"
    exit 0
  fi
  (( i % 10 == 0 )) && echo "  still starting… backend: $([ $backend = 1 ] && echo ready || echo starting), frontend: $([ $frontend = 1 ] && echo ready || echo starting)"
  sleep 2
done

[ $backend = 1 ] || fail "The backend did not start within 6 minutes." "Look at the Terminal window titled 'CareerSphere backend' for the error."
fail "The frontend did not start within 6 minutes." "Look at the Terminal window titled 'CareerSphere frontend' for the error."
