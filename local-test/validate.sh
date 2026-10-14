#!/bin/bash
# Valida el plugin de community branch sobre un SonarQube 26.10 limpio.
# Comprueba lo que rompia en produccion: que el scanner acepte sonar.branch.name
# y cree la rama, en vez de morir con NoClassDefFoundError.
set -euo pipefail

SQ="http://cbp-sonarqube:9000"
ADMIN_AUTH="admin:admin"
NEW_AUTH="admin:Cbptest2026!Ok"
PROJECT="cbp-validation"
SCANNER_VER="8.1.0.6389"
WORK=/tmp/cbp
SCAN_OUT=/tmp/cbp-scan.log

log() { echo "[$(date +%H:%M:%S)] $*"; }

log "1/6 esperando a SonarQube"
for i in $(seq 1 60); do
  if curl -sf "$SQ/api/system/status" | grep -q '"status":"UP"'; then break; fi
  sleep 5
done
curl -sf "$SQ/api/system/status"; echo

log "2/6 cambiando la password por defecto de admin"
# OJO: este endpoint NO acepta cuerpo JSON, solo query params
# (responde 400 "The 'login' parameter is missing" si se manda en el body).
curl -s -o /dev/null -w "  change_password: %{http_code}\n" -u "$ADMIN_AUTH" \
  -X POST "$SQ/api/users/change_password?login=admin&previousPassword=admin&password=Cbptest2026%21Ok"

log "3/6 creando el proyecto $PROJECT"
curl -s -o /dev/null -w "  create project: %{http_code}\n" -u "$NEW_AUTH" -X POST "$SQ/api/projects/create?name=$PROJECT"

log "4/6 generando token de analisis"
TOKEN=$(curl -s -u "$NEW_AUTH" -X POST "$SQ/api/user_tokens/generate?name=cbp-validation" \
  -H 'Content-Type: application/json' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
if [ -z "$TOKEN" ]; then echo "  NO SE PUDO GENERAR EL TOKEN"; exit 1; fi
log "  token ok (${TOKEN:0:4}...)"

log "5/6 descargando SonarScanner CLI $SCANNER_VER"
mkdir -p "$WORK"
if [ ! -x "$WORK/scanner/bin/sonar-scanner" ]; then
  curl -fsSL -o "$WORK/scanner.zip" \
    "https://repo1.maven.org/maven2/org/sonarsource/scanner/cli/sonar-scanner-cli/${SCANNER_VER}/sonar-scanner-cli-${SCANNER_VER}-linux-x64.zip"
  unzip -qo "$WORK/scanner.zip" -d "$WORK/dist"
  SB=$(find "$WORK/dist" -type f -path '*/bin/sonar-scanner' | head -1)
  SCANNER_ROOT=$(dirname "$(dirname "$SB")")
  cp -r "$SCANNER_ROOT" "$WORK/scanner"
  chmod +x "$WORK/scanner/bin/"*
fi
SCANNER="$WORK/scanner/bin/sonar-scanner"
java -version 2>&1 | head -1
"$SCANNER" --version

scan_branch() {
  local branch="$1" label="$2"
  log "     escaneando rama '$branch' ($label)"
  # JAVA_HOME/JRE propio del scanner: forzamos el JRE de la imagen (21+)
  JAVA_HOME="$WORK/scanner/jre" "$SCANNER" \
    -D "sonar.projectKey=$PROJECT" \
    -D "sonar.host.url=$SQ" \
    -D "sonar.token=$TOKEN" \
    -D "sonar.branch.name=$branch" \
    -D "sonar.sources=/work/project" \
    -D "sonar.exclusions=**/node_modules/**" \
    > "$SCAN_OUT" 2>&1
  local rc=$?
  if grep -q "ANALYSIS SUCCESSFUL" "$SCAN_OUT"; then
    log "     OK  $branch -> ANALYSIS SUCCESSFUL"
    return 0
  fi
  log "     FALLO $branch (rc=$rc)"
  tail -25 "$SCAN_OUT" | sed 's/^/       /'
  return 1
}

log "6/6 analisis por rama"
cp -r /work/project "$WORK/project"
cd "$WORK/project"

RCS=0
scan_branch main "baseline" || RCS=1
echo '// cambio en la rama feature' >> index.js
scan_branch feature/nueva-scan "rama nueva" || RCS=1

log "ramas que reporta el servidor (endpoint del propio plugin)"
curl -s -u "$NEW_AUTH" "$SQ/api/project_branches/list?project=$PROJECT"; echo

if [ $RCS -ne 0 ]; then
  log "RESULTADO: VALIDACION FALLIDA"
  exit 1
fi
log "RESULTADO: VALIDACION CORRECTA - el scanner resolvio ramas con SQ 26.10"
