#!/bin/zsh
# CareerSphere backend (Spring Boot on port 8080). Opened by start-servers.sh; you can also double-click it.
printf '\e]0;CareerSphere backend\a'
cd "$(dirname "$0")/../backend" || exit 1
echo "CareerSphere backend: http://localhost:8080   (press Ctrl+C or close this window to stop)"
echo
mvn spring-boot:run
