# Upgrading SonarQube Community Build with the cbp image

Operational notes from the 2026-11-11 upgrade (26.10.0.133078 -> 26.11.0.134175).
The instance runs in AKS `aks-taficore-useast`, namespace `sonarqube`, StatefulSet
`sonar-tafi-sonarqube` (helm release `sonar-tafi` exists but the live spec has been
edited outside helm - helm upgrade WILL revert the image/env, treat helm values as stale
until reconciled).

## Landmines (all measured, do not skip)

1. `/opt/sonarqube/extensions` is an **emptyDir**, not a PVC. Anything copied into a
   running pod evaporates on restart. The `install-plugins` init container regenerates
   `extensions/plugins` from the image (`/opt/cbp/*.jar`) on every start. Never bring
   the jar back with `kubectl cp`.
2. The plugin jar lives at `/opt/cbp/` inside the cbp image because mounting the
   emptyDir hides the baked `extensions/plugins` path. Keep both COPY lines in
   `Dockerfile.cbp-web`.
3. Community Build does NOT auto-run DB migrations. When status shows
   `DB_MIGRATION_NEEDED`, POST `/api/system/migrate_db` (allowed without auth while in
   maintenance). Then poll `/api/system/status` until `UP`.
4. Custom quality gates and external SSO plugins (OIDC) are not available on Community
   Build. Entra ID login = native SAML (`Administration > Authentication > SAML`).
   Security gating happens in CI (`devops-code-scan` stages fail PRs on new
   vulnerabilities).
5. `webapp` is a git submodule pinned to a SonarSource `sqcb-<version>` tag. All the
   loose untracked files inside it (`Dockerfile.cbp-web`, `.dockerignore`, jars,
   `test-instance.yaml`) are NOT versioned by any repo - the parent repo cannot track
   files inside a submodule path. They die on the next `git clean/checkout -f`.
   Keep your own backup copy (e.g. `../cbp-web-overlay/`) before touching the submodule
   and restore them right after checking out a new tag.

## Upgrade procedure

1. Backup first (validated):
   `kubectl exec postgres-pod -- pg_dump -F c` inside the pod, then `kubectl cp` out.
2. Check the new CB tag on Docker Hub (`sonarqube` library, `-community` suffix) and the
   matching `sqcb-<version>` webapp tag in SonarSource/sonarqube-webapp.
3. In this repo: bump `build.gradle` `sonarqubeVersion`, `gradle.properties` `version`.
4. In `sonarqube-webapp`: `git fetch && git checkout -f sqcb-<new-tag>`.
5. `docker build --build-arg SONARQUBE_VERSION=<new>-community --build-arg PLUGIN_VERSION=<plugin-ver> -t cbp-build:<new> .`
6. Expect webapp breakage on minor bumps (26.11 broke: Spinner moved to
   `@sonarsource/echoes-react`, `MeasurementType`/`getMeasurementMetricKey` moved to
   `~shared/helpers/overview`, `react-router-dom` collapsed into `react-router`,
   `HelpTooltip` deleted - addons shim in `sonarqube-webapp-addons/src/components`).
   Java/gradle stage usually compiles clean.
7. Extract jar + webapp from `cbp-build` (`docker create` + `docker cp`), replace
   `sonarqube-webapp/webapp` and the jar file, update `Dockerfile.cbp-web` FROM + jar
   names, `docker build -f Dockerfile.cbp-web -t nicoalca/sonarqube-cbp:<new>-vN .`, push.
8. Cluster: `kubectl set image` main container + patch all 3 initContainers to the new
   cbp image, keep `SONAR_WEB_JAVAOPTS`/`SONAR_CE_JAVAOPTS` pointing to the new jar
   filename, `kubectl delete pod` if the rollout stalls on a crashlooping revision.
9. POST `/api/system/migrate_db`, poll until `UP`.
10. Verify: `/api/system/health` GREEN, `/api/project_branches/list` (plugin works),
    SAML settings intact, Entra login works.
