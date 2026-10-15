# Before the first Jenkins CD run

JenkinsfileCD deploys to a cluster that already exists. It never creates the cluster,
the VPC peering to RDS, or the database. Do these once, from the EC2 box, following
`k8s/README.md`.

## 1. The cluster exists and has the EBS driver (README steps 1-2)

```bash
aws eks update-kubeconfig --region ap-south-1 --name etp-cluster-1
kubectl get nodes                                   # two nodes, Ready
kubectl get pods -n kube-system | grep ebs          # ebs-csi-controller / ebs-csi-node Running
```

## 2. The cluster reaches RDS (README step 4A)

The cluster is in its own VPC. CD can add the security-group rule for 5432 by itself
(`AUTO_AUTHORIZE_RDS`), but **not** the VPC peering and routes. Check:

```bash
bash jenkins/rds-test.sh        # ... - accepting connections
```

## 3. Stop the EC2 compose stack (README step 3)

```bash
docker compose down
```

Both stacks run the same KYC, payment and outbox jobs against one RDS, and a small RDS
runs out of connection slots.

## 4. The public address is in urls.env (README step 8)

`k8s/overlays/frontend-lb/urls.env` is committed, and CD deploys what git has. It must
name the frontend load balancer, or the browser gets 403 "Invalid CORS request":

```bash
kubectl -n yellow-trade get svc frontend -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'; echo
grep CORS_ALLOWED_ORIGINS k8s/overlays/frontend-lb/urls.env
```

On a brand-new cluster, the first CD run creates the load balancer: write its address
into `urls.env`, commit, push and run CD again.

## Workloads that were scaled down

`kubectl apply` sets every Deployment back to the `replicas: 1` in `k8s/base/`. If you
scaled them to 0 to save money, a CD run starts them again. To stop them afterwards:

```bash
kubectl -n yellow-trade scale deploy auth-service order-service executor-service frontend --replicas=0
```

This keeps the cluster, the Services, the Secret, the ConfigMaps and Kafka's disk.
