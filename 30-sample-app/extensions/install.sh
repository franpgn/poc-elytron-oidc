#!/usr/bin/env bash
# Script executado pelo S2I/Helm do EAP 8 durante o build.
# Quem chama: o builder (galleon/cli) com $JBOSS_HOME apontando para a imagem em construcao.
set -euo pipefail

injected_dir="$1"   # diretorio com extensions/* copiado pelo builder
echo "[install.sh] injected_dir=${injected_dir} JBOSS_HOME=${JBOSS_HOME}"

# ---- 1) Copia o JAR do role mapper para o modulo correspondente ----
MODULE_DEST="${JBOSS_HOME}/modules/org/bcbsal/jboss8/jboss8-custom-properties-mapped-role-mapper/main"
mkdir -p "${MODULE_DEST}"

# Esperamos o JAR ja construido em extensions/modules-jars/. Veja a etapa de
# build do modulo no README (mvn -pl 20-custom-module).
if [ -f "${injected_dir}/modules-jars/jboss8-custom-properties-mapped-role-mapper-1.0.0.jar" ]; then
    cp "${injected_dir}/modules-jars/jboss8-custom-properties-mapped-role-mapper-1.0.0.jar" "${MODULE_DEST}/"
fi
cp "${injected_dir}/modules/org/bcbsal/jboss8/jboss8-custom-properties-mapped-role-mapper/main/module.xml" "${MODULE_DEST}/"

# ---- 2) Copia o properties de mapeamento ----
cp "${injected_dir}/configuration/rolesMapping-roles.properties" \
   "${JBOSS_HOME}/standalone/configuration/rolesMapping-roles.properties"

# ---- 3) Aplica as configuracoes do Elytron via CLI ----
"${JBOSS_HOME}/bin/jboss-cli.sh" --file="${injected_dir}/extensions.cli"

echo "[install.sh] done"
