# Yellow Trade on Kubernetes (EKS)

The `docker-compose.yml` stack of branch `feature/single-db-docker`, as Kubernetes
manifests. It is laid out like the `k8s/` folder of IM-RAHUL-RAJ/Cloud-Deployment-Setup
(Kustomize base + overlays + optional Postgres + an eksctl cluster file), with every
value changed to this project's: service names, ports, images, environment variables,
health checks, topics and the one-database/two-schema layout.

Unzip it at the **repository root**, next to `docker-compose.yml`, so the path is
`<repo>/k8s/`. Every command below runs from the repository root.

## What is in it

```
k8s/
  prepare.sh                 copies repo files into generated/, checks your settings, renders
  cluster.example.yaml       eksctl: an EKS cluster with 2 x t3.medium and the EBS driver
  base/                      everything both overlays share
    kustomization.yaml       resource list, generated ConfigMaps/Secret, ECR image names
    namespace.yaml           namespace yellow-trade (every object goes into it)
    storageclass.yaml        gp3: encrypted EBS disks for Kafka (and the optional Postgres)
    config.env               non-secret settings   -> ConfigMap yellow-trade-config
    secrets.env.example      template for secrets.env (gitignored) -> Secret yellow-trade-secrets
    kafka.yaml               single-node Kafka (KRaft), kafka:29092, 5 Gi disk
    kafka-topics-job.yaml    runs Infrastructure/Kafka/create-topics.sh: all ten topics
    auth-service.yaml        auth-service      :3000
    order-service.yaml       order-service     :8081
    executor-service.yaml    executor-service  :8082
    frontend.yaml            frontend (nginx)  :80
    frontend/default.conf.template   the frontend's nginx config, for the cluster
  overlays/
    frontend-lb/             STAGE 1: one load balancer, in front of the frontend
    ingress/                 STAGE 2 (optional): one ALB, for HTTPS / your own domain
  optional/postgres/         Postgres inside the cluster instead of RDS (a Component)
```

## How it maps to docker compose

| docker-compose.yml | Kubernetes |
| --- | --- |
| `kafka` (healthcheck, volume) | StatefulSet `kafka` + headless Service, EBS gp3 disk |
| `kafka-init` | Job `kafka-topics` (the same `create-topics.sh`) |
| `postgres` (profile `localdb`) | `optional/postgres` (the same init scripts and seed) |
| `order-service`, `executor-service`, `auth-service` | Deployments + internal (ClusterIP) Services, same names and ports |
| `frontend` on port 4200 | Deployment `frontend` + one public load balancer on port 80 |
| `.env` | `base/config.env` (settings) + `base/secrets.env` (secrets) + `overlays/*/urls.env` (public address) |
| `Config/application.yml` mounted at `/config` | the same file, as ConfigMap `yellow-trade-shared-config`, at `/config` |
| `depends_on: ... service_healthy` | an init container that waits for Kafka and the database |
| network `trading-net` | namespace `yellow-trade` |

## What differs from Rahul's kit, and why

| Rahul's kit | Here | Why |
| --- | --- | --- |
| `four-lb`: 4 public load balancers | `frontend-lb`: 1 | Our frontend image calls its own origin and nginx forwards the API paths, so the browser only ever talks to the frontend. auth/order/executor stay internal. |
| probes: `/health` (order), TCP (executor) | `/actuator/health/liveness` and `/readiness` (order and executor), `/docs/json` (auth, as Rahul's) | Spring's own probe endpoints, which both our Java services expose. Not `/actuator/health`: it also checks the SMTP server, and in the test it answered 503 when mail failed, which would take the whole API offline over an email problem. |
| `runAsUser` 1000 / 10001 | 100, group 101 | The users our Dockerfiles create (`adduser -S`). Checked in the built images. |
| every secret to every service (`envFrom`) | each service gets only the secrets it uses | As in compose: auth never receives the `trading_app` password, the Java services never receive `AUTH_DB_PASSWORD`. That is the point of the two schemas. |
| 3 topics + DLTs, inline | all 10, from `Infrastructure/Kafka/create-topics.sh` | Ours also has `kyc-events` and `account-provisioning` (the activation email), and DLTs with 1 partition. |
| the image's nginx config | `base/frontend/default.conf.template` | Our image's config asks Docker's DNS (`127.0.0.11`), which does not exist in a pod. Same locations, cluster DNS. |
| creates namespace `etp`, but its objects name no namespace (they land in the current one, usually `default`) | everything in `yellow-trade` | `namespace:` is set in base and in both overlays. |
| `optional/postgres` applied by hand | a Component: uncomment two lines in an overlay | It also switches `DB_HOST` to `postgres` by itself. The seed is gzipped: as plain SQL it breaks `kubectl apply`'s 256 KiB limit. |

## Tested

On a local kind cluster (gp3 pointed at kind's local disks), with images built from commit `3b2cd86` and the in-cluster Postgres:
- plain `kubectl apply -k`, with every pod Ready in about 40 seconds and zero restarts;
- all ten topics created, and the database with 24 tables in `public`, 5 in `auth` and the three roles;
- through the frontend: `/` 200, `/api/v1/accounts/1` 401, `/auth/me` 401, `/activate` 200, `/onboarding/applications` 422/202;
- a real application went through the whole chain: KYC VERIFIED → `kyc-events` → auth provisioned the account → `account-provisioning` → order-service minted the token at `http://auth-service:3000`. Only the email failed, because the test used fake Gmail credentials;
- switching between `frontend-lb` and `ingress` with `kubectl apply`.

Not tested here: AWS itself (EKS, ECR, EBS, the load balancers, the RDS network path). Those steps follow Rahul's runbook where it covers them, and the AWS documentation for the rest (VPC peering, the ALB controller).

---

## Step 0. Tools (on your EC2 box)

```bash
aws --version            # AWS CLI v2
docker --version
# kubectl
curl -fsSLo kubectl "https://dl.k8s.io/release/$(curl -fsSL https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -m 0755 kubectl /usr/local/bin/kubectl && rm kubectl
# eksctl
curl -fsSL "https://github.com/eksctl-io/eksctl/releases/latest/download/eksctl_Linux_amd64.tar.gz" | tar xz -C /tmp
sudo install -m 0755 /tmp/eksctl /usr/local/bin/eksctl
# helm: only for step 10 (the ALB)
curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

## Step 1. AWS context

```bash
aws configure                      # access key, secret, region
aws sts get-caller-identity
export AWS_REGION=ap-south-1       # your region: the one your RDS is in
export CLUSTER_NAME=etp-cluster-1   # the name in cluster.example.yaml
```

Edit `k8s/cluster.example.yaml` so that `metadata.name` = `$CLUSTER_NAME` and
`metadata.region` = `$AWS_REGION`.

## Step 2. Create the cluster (15-20 minutes)

```bash
eksctl create cluster -f k8s/cluster.example.yaml
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"
kubectl get nodes                  # two nodes, Ready
kubectl get pods -n kube-system | grep ebs     # ebs-csi-controller / ebs-csi-node Running
```

If eksctl stops with "stack already exists", do not run it again. See
`DEPLOYMENT.md` in IM-RAHUL-RAJ/Cloud-Deployment-Setup, section "Create EKS", for how to recover.

## Step 3. Stop the EC2 compose stack, if it uses the same RDS

```bash
docker compose down
```

Both stacks run the same KYC, payment and outbox jobs against one database. They would
take each other's work, and a small RDS runs out of connection slots.

## Step 4. The database

### 4A. Amazon RDS (the default)

**The database must already be built**: `trading_system_db` with the schemas `public`
and `auth` and the roles `trading_app`, `analytics_ro`, `auth_app`. If you followed
`RDS-CLEAN-AND-SETUP.txt` for EC2, this is already done, so use the same RDS and the same
three passwords. Otherwise run `Databases/PostgreSQL/local/setup.sql` exactly as that
file says.

**Network.** eksctl put the cluster in a **new VPC**, and your RDS is in another one
(usually the default VPC). Connect the two with VPC peering:

```bash
RDS_HOST=database-1.cpekuiuqcl99.ap-south-1.rds.amazonaws.com
RDS_ID=$(aws rds describe-db-instances --region "$AWS_REGION" \
  --query "DBInstances[?Endpoint.Address=='$RDS_HOST'].DBInstanceIdentifier | [0]" --output text)
RDS_VPC=$(aws rds describe-db-instances --region "$AWS_REGION" --db-instance-identifier "$RDS_ID" \
  --query 'DBInstances[0].DBSubnetGroup.VpcId' --output text)
RDS_SG=$(aws rds describe-db-instances --region "$AWS_REGION" --db-instance-identifier "$RDS_ID" \
  --query 'DBInstances[0].VpcSecurityGroups[0].VpcSecurityGroupId' --output text)
EKS_VPC=$(aws eks describe-cluster --region "$AWS_REGION" --name "$CLUSTER_NAME" \
  --query 'cluster.resourcesVpcConfig.vpcId' --output text)
RDS_CIDR=$(aws ec2 describe-vpcs --region "$AWS_REGION" --vpc-ids "$RDS_VPC" --query 'Vpcs[0].CidrBlock' --output text)
EKS_CIDR=$(aws ec2 describe-vpcs --region "$AWS_REGION" --vpc-ids "$EKS_VPC" --query 'Vpcs[0].CidrBlock' --output text)
echo "RDS $RDS_ID: $RDS_VPC $RDS_CIDR   EKS: $EKS_VPC $EKS_CIDR   RDS security group: $RDS_SG"
```

- **The same VPC** (`RDS_VPC` = `EKS_VPC`): skip the peering. Run only the last command
  (the security-group rule).
- **Overlapping CIDRs** (for example both are `192.168.0.0/16`): peering is impossible.
  Recreate the cluster with `vpc: { cidr: 10.42.0.0/16 }` added to `cluster.example.yaml`.

```bash
PCX=$(aws ec2 create-vpc-peering-connection --region "$AWS_REGION" --vpc-id "$EKS_VPC" --peer-vpc-id "$RDS_VPC" \
  --query 'VpcPeeringConnection.VpcPeeringConnectionId' --output text)
aws ec2 accept-vpc-peering-connection --region "$AWS_REGION" --vpc-peering-connection-id "$PCX"
until [ "$(aws ec2 describe-vpc-peering-connections --region "$AWS_REGION" --vpc-peering-connection-ids "$PCX" \
  --query 'VpcPeeringConnections[0].Status.Code' --output text)" = active ]; do sleep 3; done
# A public RDS endpoint then resolves to its private address from the cluster:
aws ec2 modify-vpc-peering-connection-options --region "$AWS_REGION" --vpc-peering-connection-id "$PCX" \
  --requester-peering-connection-options AllowDnsResolutionFromRemoteVpc=true \
  --accepter-peering-connection-options AllowDnsResolutionFromRemoteVpc=true
# Routes both ways, in every route table of each VPC:
for rt in $(aws ec2 describe-route-tables --region "$AWS_REGION" --filters Name=vpc-id,Values="$EKS_VPC" \
    --query 'RouteTables[].RouteTableId' --output text); do
  aws ec2 create-route --region "$AWS_REGION" --route-table-id "$rt" \
    --destination-cidr-block "$RDS_CIDR" --vpc-peering-connection-id "$PCX" >/dev/null && echo "route added: $rt"
done
for rt in $(aws ec2 describe-route-tables --region "$AWS_REGION" --filters Name=vpc-id,Values="$RDS_VPC" \
    --query 'RouteTables[].RouteTableId' --output text); do
  aws ec2 create-route --region "$AWS_REGION" --route-table-id "$rt" \
    --destination-cidr-block "$EKS_CIDR" --vpc-peering-connection-id "$PCX" >/dev/null && echo "route added: $rt"
done
# Let the cluster's addresses reach PostgreSQL:
aws ec2 authorize-security-group-ingress --region "$AWS_REGION" --group-id "$RDS_SG" \
  --protocol tcp --port 5432 --cidr "$EKS_CIDR"
```

Check from inside the cluster. You should see a private address and "accepting connections":

```bash
kubectl run rds-test --rm -it --restart=Never --image=postgres:16-alpine -- \
  sh -c "nslookup $RDS_HOST; pg_isready -h $RDS_HOST -p 5432"
```

### 4B. Postgres inside the cluster (no RDS, no peering)

Uncomment the two `components:` lines in the overlay you will apply
(`k8s/overlays/frontend-lb/kustomization.yaml`), and in step 6 also fill in
`POSTGRES_SUPERUSER_PASSWORD` and `ANALYTICS_DB_PASSWORD`. On its first start it builds
the database exactly as compose does: roles, both schemas, all tables and the seed data.
It keeps its data on its own 5 Gi disk. Skip the rest of step 4.

## Step 5. Images in ECR

The cluster nodes are x86_64, so build on the EC2 box (or add `--platform linux/amd64`).

```bash
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGISTRY="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"
for repo in yellow-trade-auth-service yellow-trade-order-service yellow-trade-executor-service yellow-trade-frontend; do
  aws ecr describe-repositories --region "$AWS_REGION" --repository-names "$repo" >/dev/null 2>&1 || \
  aws ecr create-repository --region "$AWS_REGION" --repository-name "$repo" \
    --image-scanning-configuration scanOnPush=true --encryption-configuration encryptionType=AES256 >/dev/null
done
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY"

# From the repository root. Java builds need the root as context (the domain module).
docker build -t "$REGISTRY/yellow-trade-order-service:latest"    -f Services/order-service/Dockerfile .
docker build -t "$REGISTRY/yellow-trade-executor-service:latest" -f Services/executor-service/Dockerfile .
docker build -t "$REGISTRY/yellow-trade-auth-service:latest"     -f Services/auth-service/Dockerfile .
docker build -t "$REGISTRY/yellow-trade-frontend:latest"         ./Frontend
for repo in yellow-trade-auth-service yellow-trade-order-service yellow-trade-executor-service yellow-trade-frontend; do
  docker push "$REGISTRY/$repo:latest"
done

# Point the manifests at your registry (replaces <REGISTRY> in base/kustomization.yaml):
sed -i "s#<REGISTRY>#$REGISTRY#" k8s/base/kustomization.yaml
grep newName k8s/base/kustomization.yaml
```

## Step 6. Settings

```bash
cp k8s/base/secrets.env.example k8s/base/secrets.env
vi k8s/base/secrets.env      # the same values as your EC2 .env (and setup.sql's passwords)
vi k8s/base/config.env       # DB_HOST = your RDS endpoint (4A). Leave it for 4B.
```

`secrets.env` holds the same keys as the EC2 `.env`: `DB_PASSWORD`, `AUTH_DB_PASSWORD`,
`JWT_SECRET`, `ACTIVATION_INTERNAL_SECRET`, `SMTP_USERNAME`, `SMTP_PASSWORD`,
`ACTIVATION_MAIL_FROM`, `REDIS_URL`, `FAUXNANCE_API_KEY`, `MF_NAV_API_KEY`. Write them
without quotes, and never commit the file: `k8s/.gitignore` and `k8s/base/.gitignore`
ignore it.

## Step 7. Prepare

```bash
bash k8s/prepare.sh
```

It copies `Config/application.yml`, `create-topics.sh` and the database init files into
the `generated/` folders, then refuses to continue while anything compose would refuse
is missing: an empty secret, `<REGISTRY>`, `<RDS-ENDPOINT>`, Windows line endings. It
names keys only, never values. Run it again after every change to those files.

## Step 8. Deploy (frontend-lb)

```bash
kubectl apply -k k8s/overlays/frontend-lb
kubectl -n yellow-trade get pods -w         # Ctrl-C when all are Running/Completed
kubectl -n yellow-trade get svc frontend    # EXTERNAL-IP: a ...elb.amazonaws.com name
```

The first apply uses a placeholder address, so the browser gets **403 "Invalid CORS
request"** until you do this:

```bash
LB=$(kubectl -n yellow-trade get svc frontend -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
echo "$LB"
sed -i "s#http://frontend-address-pending.invalid#http://$LB#" k8s/overlays/frontend-lb/urls.env
cat k8s/overlays/frontend-lb/urls.env
kubectl apply -k k8s/overlays/frontend-lb   # order-service and auth-service restart with it
```

Open `http://<LB>` (http, no port). The name can take 2-3 minutes to resolve the first
time. Activation emails link to the same address.

## Step 9. Check

```bash
kubectl -n yellow-trade get pods            # all Running, kafka-topics Completed, 0 restarts
curl -s -o /dev/null -w '%{http_code}\n' "http://$LB/"                     # 200
curl -s "http://$LB/api/v1/accounts/1"                                      # {"errorCode":"AUTH-401",...}
curl -s -o /dev/null -w '%{http_code}\n' -X POST "http://$LB/onboarding/applications" \
  -H 'Content-Type: application/json' -d '{}'                              # 422
kubectl -n yellow-trade logs job/kafka-topics | tail -1                     # all ten topics ensured
```

Then use it as on EC2: apply for an account, wait for the email (about 40 s), set a
password, sign in, add cash, place an order.

## Step 10 (optional). One ALB: HTTPS and your own domain

Install the AWS Load Balancer Controller once per cluster:

```bash
curl -fsSLo iam_policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/main/docs/install/iam_policy.json
aws iam create-policy --policy-name AWSLoadBalancerControllerIAMPolicy --policy-document file://iam_policy.json \
  || echo "The policy may already exist; that is fine."
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
eksctl create iamserviceaccount --cluster "$CLUSTER_NAME" --region "$AWS_REGION" \
  --namespace kube-system --name aws-load-balancer-controller \
  --role-name "AmazonEKSLoadBalancerControllerRole-$CLUSTER_NAME" \
  --attach-policy-arn "arn:aws:iam::$AWS_ACCOUNT_ID:policy/AWSLoadBalancerControllerIAMPolicy" --approve
VPC_ID=$(aws eks describe-cluster --region "$AWS_REGION" --name "$CLUSTER_NAME" \
  --query 'cluster.resourcesVpcConfig.vpcId' --output text)
helm repo add eks https://aws.github.io/eks-charts && helm repo update eks
helm install aws-load-balancer-controller eks/aws-load-balancer-controller -n kube-system \
  --set clusterName="$CLUSTER_NAME" --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller \
  --set region="$AWS_REGION" --set vpcId="$VPC_ID"
kubectl -n kube-system rollout status deploy/aws-load-balancer-controller
```

Then:

```bash
# For 4B, uncomment components: in overlays/ingress/kustomization.yaml too.
kubectl apply -k k8s/overlays/ingress      # the frontend Service turns back into ClusterIP: the ELB goes
kubectl -n yellow-trade get ingress yellow-trade      # ADDRESS: ...elb.amazonaws.com (1-3 minutes)
ALB=$(kubectl -n yellow-trade get ingress yellow-trade -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
sed -i "s#http://alb-address-pending.invalid#http://$ALB#" k8s/overlays/ingress/urls.env
kubectl apply -k k8s/overlays/ingress
```

For HTTPS, add an ACM certificate (the commented lines in `overlays/ingress/ingress.yaml`)
and write `https://` in `urls.env`. To go back to frontend-lb, apply it, then
`kubectl -n yellow-trade delete ingress yellow-trade` (apply never deletes objects).

## Changing things later

| Change | Do |
| --- | --- |
| `config.env`, `secrets.env`, `urls.env`, `Config/application.yml` | `bash k8s/prepare.sh && kubectl apply -k k8s/overlays/frontend-lb`. The names carry a hash of the contents, so the pods that use them restart by themselves. |
| new code | rebuild and push the image (step 5), then `kubectl -n yellow-trade rollout restart deploy/order-service` (or auth-service, executor-service, frontend). The tag stays `latest`, so the manifests do not change and nothing restarts on its own. |
| `Infrastructure/Kafka/create-topics.sh` | `kubectl -n yellow-trade delete job kafka-topics --ignore-not-found`, then prepare and apply. |
| the in-cluster database (4B), from scratch | `kubectl -n yellow-trade delete statefulset postgres && kubectl -n yellow-trade delete pvc data-postgres-0`, then apply. It is rebuilt with the seed. |

## Troubleshooting

```bash
kubectl -n yellow-trade get pods
kubectl -n yellow-trade describe pod <pod>              # Events at the bottom
kubectl -n yellow-trade logs deploy/order-service --tail=100     # or auth-service, executor-service, frontend
kubectl -n yellow-trade logs deploy/order-service -c wait-for-kafka-and-db
kubectl -n yellow-trade get events --sort-by=.lastTimestamp | tail -20
```

| Symptom | Cause, fix |
| --- | --- |
| `Init:0/1`, and the init log repeats "waiting for the database at ..." | The cluster cannot reach RDS: step 4A (peering, routes, security group). Test with the `rds-test` pod. |
| kafka-0 or postgres-0 `Pending`, PVC `Pending` | The EBS CSI driver is missing: `eksctl create addon --cluster "$CLUSTER_NAME" --region "$AWS_REGION" --name aws-ebs-csi-driver --force` (`DEPLOYMENT.md` in IM-RAHUL-RAJ/Cloud-Deployment-Setup, "Storage Addon"). |
| `CreateContainerConfigError` | A key is missing from `secrets.env`. Keep every line of the example, even empty ones. Run `prepare.sh`. |
| `ImagePullBackOff` / `InvalidImageName` | `<REGISTRY>` was not replaced, the image was not pushed, or it was built for ARM. Step 5. |
| Browser: 403 "Invalid CORS request" | `urls.env` does not match the address in the browser exactly (http/https, no port, no slash). Step 8. |
| "password authentication failed" in a service log | `secrets.env` differs from the passwords given to `setup.sql`. |
| No activation email | `kubectl -n yellow-trade logs deploy/order-service \| grep -i activation`: `AuthenticationFailedException` means the Gmail app password is wrong. |
| `remaining connection slots are reserved` | The EC2 compose stack still runs against the same RDS: step 3. |
| "Job.batch kafka-topics is invalid: field is immutable" | `kubectl -n yellow-trade delete job kafka-topics`, then apply. |
| a repeated apply prints `configured` for kafka, postgres, the Secret | Harmless: `kubectl diff -k ...` shows nothing changed, and no pod restarts. |
| `Warning: spec.SessionAffinity is ignored for headless services` | Harmless: printed for the headless kafka (and postgres) Service. |

## Cleanup (it costs money while it runs)

```bash
kubectl delete -k k8s/overlays/frontend-lb      # deletes the namespace: Kafka's (and Postgres') data too
eksctl delete cluster --region "$AWS_REGION" --name "$CLUSTER_NAME"
aws ec2 delete-vpc-peering-connection --region "$AWS_REGION" --vpc-peering-connection-id "$PCX"   # if made in 4A
```

Then remove the security-group rule added in 4A (`5432` from the cluster's CIDR), and the
four ECR repositories if you no longer need them.
