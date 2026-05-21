# POC — Elytron OIDC + custom role mapper no JBoss EAP 8 (OpenShift, S2I)

Reproduz o cenario que o cliente mandou:

- `custom-realm` OIDC (`org.wildfly.security.http.oidc.OidcSecurityRealm`)
- `custom-role-mapper` proprio
  (`org.bcbsal.jboss8.custom.rolemapper.CustomPropertiesMappedRoleMapper`)
  lendo `rolesMapping-roles.properties`
- `security-domain` que combina os dois
- `http-authentication-factory` + `application-security-domain` no Undertow
- App de teste sem `auth-method=OIDC` no `web.xml`, usando o listener
  `OidcConfigurationServletListener`
- Tudo subindo via **S2I** com a pasta `extensions/` processada pelo
  builder do EAP 8

> Tudo aqui esta isolado em `/workspace/poc-elytron-oidc`. Apaga a pasta
> quando terminar e nada disso te incomoda mais.

---

## Pre-requisitos

- OpenShift rodando (voce ja tem) + `oc` logado como cluster-admin.
- `helm` e `git` localmente.
- `mvn` + JDK 17 para compilar o modulo Java.
- Acesso ao registry da Red Hat (`registry.redhat.io`) — se for OpenShift
  Local/CRC, faca `oc create secret` com seu pull secret no namespace `poc`.

```bash
cd /workspace/poc-elytron-oidc
```

---

## Passo 1 — namespace + Keycloak (IdP)

```bash
oc apply -f 00-keycloak/01-namespace.yaml
oc apply -f 00-keycloak/03-realm-configmap.yaml
oc apply -f 00-keycloak/02-keycloak.yaml
oc -n poc rollout status deploy/keycloak
oc -n poc get route keycloak -o jsonpath='{.spec.host}{"\n"}'
```

Abra `https://<route>/admin` (admin / admin). Confirme que o realm `poc`
existe com:
- client `eap-app` (secret `eap-app-secret`)
- users `alice` (role `EXTERNAL_ADMIN`) e `bob` (role `EXTERNAL_USER`)

URL **interna** (sera usada pelo app):
`http://keycloak.poc.svc:8080/realms/poc`

URL **externa** (sera usada pelo browser p/ login): a rota acima
+ `/realms/poc`.

> Importante: o discovery do OIDC retorna URLs do issuer.
> Em POC use a rota tambem como `auth-server-url` do `oidc.json` se
> o browser e o pod conseguirem alcancar a mesma URL. Para simplificar,
> exponha a rota e use-a tambem internamente
> (configure no `oidc.json`/Helm values).

---

## Passo 2 — operator e Helm

Leia `10-operator/install-operator.md`. Em resumo:

```bash
oc apply -f - <<'EOF'
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata: { name: eap, namespace: openshift-operators }
spec:
  channel: stable
  name: eap
  source: redhat-operators
  sourceNamespace: openshift-marketplace
EOF

helm repo add jboss-eap https://jbossas.github.io/eap-charts/
helm repo update
```

---

## Passo 3 — compilar o JAR do role mapper

```bash
cd 20-custom-module
mvn -q clean package
# copia para o lugar onde o install.sh do S2I espera encontrar
mkdir -p ../30-sample-app/extensions/modules-jars
cp target/jboss8-custom-properties-mapped-role-mapper-1.0.0.jar \
   ../30-sample-app/extensions/modules-jars/
cd ..
```

> Para o S2I do EAP, o JAR PRECISA estar dentro de `30-sample-app/extensions/`
> antes do `git push` — o builder so enxerga o que esta no contextDir.

---

## Passo 4 — empurrar para o Git

O Helm chart faz build a partir de um Git. Crie um repo (interno tambem
serve) e:

```bash
git init
git add .
git commit -m "POC Elytron OIDC + custom role mapper"
git remote add origin https://github.com/YOUR-ORG/poc-elytron-oidc.git
git push -u origin main
```

Edite `40-helm/values.yaml` -> `build.uri` com seu repo real.

---

## Passo 5 — secret do client + deploy via Helm

```bash
oc apply -f 40-helm/oidc-client-secret.yaml

helm install -n poc poc-app jboss-eap/eap8 -f 40-helm/values.yaml
oc -n poc get build,bc,pods,route
```

Acompanhe o build:

```bash
oc -n poc logs -f bc/poc-app-build
```

Procure mensagens do `install.sh`:
- `injected_dir=...`
- saida dos comandos CLI do `extensions.cli`
- `[CustomPropertiesMappedRoleMapper] Loaded N role mappings`

Se aparecer **`AS-OIDC-ROLEMAPPING-DOMAIN`** registrado no Elytron sem
erro -> build ok.

---

## Passo 6 — registrar Redirect URI no Keycloak

Apos o deploy:

```bash
APP_URL=$(oc -n poc get route -l app.kubernetes.io/name=poc-app \
  -o jsonpath='{.items[0].spec.host}')
echo "https://${APP_URL}/poc/*"
```

No admin do Keycloak -> Clients -> `eap-app` ->
**Valid redirect URIs**: cole o `https://.../poc/*` (e remova o `*`
generico se quiser apertar).

---

## Passo 7 — testar

1. Acesse `https://<route>/poc/secured`
2. Keycloak pede login -> use **alice / alice**
3. Voce deve voltar para `/secured` mostrando:

```
Principal: alice
admin           = true
user            = false
EXTERNAL_ADMIN  = false      <- mapeada para 'admin' pelo role mapper
EXTERNAL_USER   = false
```

4. `/poc/logout` derruba a sessao. Faca login com **bob / bob** ->
   `user = true`, `admin = false`.

Se `admin` virar `true` para alice, **o role mapper esta funcionando** —
a role `EXTERNAL_ADMIN` do token virou `admin` via
`rolesMapping-roles.properties`.

---

## Como verificar dentro do pod

```bash
POD=$(oc -n poc get pod -l app.kubernetes.io/name=poc-app -o name | head -1)
oc -n poc rsh "$POD" \
  cat /opt/server/standalone/configuration/standalone.xml | grep -A3 OIDC-ROLEMAPPING
oc -n poc rsh "$POD" \
  cat /opt/server/standalone/configuration/rolesMapping-roles.properties
oc -n poc rsh "$POD" ls /opt/server/modules/org/bcbsal/jboss8/jboss8-custom-properties-mapped-role-mapper/main
```

---

## Troubleshooting curto

- **`module not found org.bcbsal...`**: o JAR nao chegou em `modules/.../main/`.
  Verifique `modules-jars/` e o `install.sh`.
- **`OidcConfigurationServletListener` nao encontrado**:
  `jboss-deployment-structure.xml` faltando ou layer
  `elytron-oidc-client` nao incluida no `galleon-provisioning.xml`.
- **Redirect URI invalida**: registre a rota do app no client `eap-app`.
- **`401` antes mesmo de redirecionar**: o `oidc.json` esta apontando
  para uma URL que o pod nao alcanca. Use a rota publica do Keycloak.
- **`isUserInRole("admin") == false` para alice**: o token nao trouxe
  `EXTERNAL_ADMIN` no claim `realm_access.roles`. Veja o mapper de
  protocolo no realm (ja incluido no JSON).

---

## Mapeando com o XML original do cliente

| Cliente (`standalone-Sachin.xml`)            | Aqui                                         |
| -------------------------------------------- | -------------------------------------------- |
| `custom-realm AS-OIDC-ROLEMAPPING-REALM`     | `extensions.cli` linha 1                     |
| `custom-role-mapper CustomPropertiesMapped..`| `extensions.cli` linha 2 + JAR no `modules/` |
| `security-domain AS-OIDC-ROLEMAPPING-DOMAIN` | `extensions.cli` linha 3                     |
| `service-loader-http-server-mechanism-factory`| `extensions.cli` linha 4                    |
| `http-authentication-factory`                | `extensions.cli` linha 5                     |
| `application-security-domain` (undertow)     | `extensions.cli` linha 6                     |
| `web.xml` SEM auth-method OIDC + listener    | `30-sample-app/.../web.xml`                  |
| `jboss-web.xml` -> security-domain           | `30-sample-app/.../jboss-web.xml`            |
| dep `elytron-http-oidc`                      | `jboss-deployment-structure.xml`             |

Isso responde direto a pergunta do cliente: **sim, da para mimickar a
config standalone original num build S2I do EAP 8 no OpenShift, usando
a pasta `extensions/` + `CUSTOM_INSTALL_DIRECTORIES` + `install.sh` +
`*.cli` + JAR do modulo customizado**. O segredo eh nao usar o subsistema
`elytron-oidc-client` aqui — voce configura OIDC manualmente via Elytron,
exatamente como ele tem em prod.
