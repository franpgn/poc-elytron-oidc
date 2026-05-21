# Instalando o JBoss EAP Operator no OpenShift

Voce ja tem o cluster OpenShift rodando. Instale o operator:

## Opcao A — pela Console OperatorHub
1. **OperatorHub** -> busque por "JBoss EAP".
2. Escolha **"JBoss EAP 8"** (provider Red Hat) -> Install.
3. Instale no namespace `poc` (ou `openshift-operators` se for global).

## Opcao B — via CLI

```bash
oc apply -f - <<'EOF'
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: eap
  namespace: openshift-operators
spec:
  channel: stable
  name: eap
  source: redhat-operators
  sourceNamespace: openshift-marketplace
EOF
```

Aguarde a CSV ficar `Succeeded`:
```bash
oc get csv -n openshift-operators -w
```

## Helm chart (necessario para o build S2I)

O caminho mais simples na pratica eh o **Helm chart `jboss-eap`** (que o
operator/community publica). Adicione o repo:

```bash
helm repo add jboss-eap https://jbossas.github.io/eap-charts/
helm repo update
helm search repo jboss-eap
```

Vamos usar `jboss-eap/eap8` no passo 4.
