# Jenkins CI/CD for Yellow Trade

Two Jenkins jobs, both reading this repository:

- `JenkinsfileCI` (`yellow-trade-ci`): checkout, `mvn test`, build the four images, push
  them to ECR with one new tag `1.<build number>`. Starts by itself when the branch changes.
- `JenkinsfileCD` (`yellow-trade-cd`): run by hand with that tag. Checks the cluster and the
  RDS path, then deploys the tag to the existing EKS cluster with `k8s/overlays/frontend-lb`.

They do by Jenkins what `k8s/README.md` steps 5-9 do by hand. The CD job never creates a
cluster. Read [prereq.md](prereq.md) before its first run.

| | value |
| --- | --- |
| cluster | `etp-cluster-1`, ap-south-1 (nodegroup `workers`) |
| namespace | `yellow-trade` |
| ECR repositories | `etp-auth-service`, `etp-order-service`, `etp-executor-service`, `etp-frontend` |
| RDS | `database-1.cpekuiuqcl99.ap-south-1.rds.amazonaws.com`, database `trading_system_db` |
| secret file credential | `yellow-trade-secrets-env` |

## 1. The Jenkins machine

Both jobs use `agent any`: they run on the Jenkins controller on the EC2 box, with that
box's tools and its instance role (`capstone-instance`) for ECR, EKS, RDS and EC2. No AWS
keys are stored in Jenkins. Check as the `jenkins` user:

```bash
sudo -u jenkins aws sts get-caller-identity          # .../assumed-role/capstone-instance/...
sudo -u jenkins docker info >/dev/null && echo docker ok      # needs the docker group
sudo -u jenkins kubectl version --client
sudo -u jenkins mvn -v                               # for the Test stage
```

If `docker info` fails: `sudo usermod -aG docker jenkins && sudo systemctl restart jenkins`.

Plugins (Manage Jenkins > Plugins): **Pipeline**, **Git**, **Credentials Binding**. Optional:
**GitHub** (webhook trigger instead of polling), **Timestamper**, **Pipeline: Stage View**.

## 2. Credentials (Manage Jenkins > Credentials > System > Global)

### GitHub (the repository is private)

- Kind: **Username with password**
- Username: your GitHub username; Password: a Personal Access Token (fine-grained,
  repository `Neueda-Learning/chennai-capstone-SE3-Team2`, **Contents: Read-only**).
  The organization may need to approve the token.
- ID: `github-scm`

### The application secrets

- Kind: **Secret file**
- File: your filled-in `k8s/base/secrets.env` (the same keys as `k8s/base/secrets.env.example`,
  values without quotes)
- ID: `yellow-trade-secrets-env`

CD copies it to `k8s/base/secrets.env` for the deploy and deletes it afterwards. Passwords
never go into a Jenkinsfile, a build parameter, or git. To change a secret, update the
credential's file and run CD again: the Secret's name carries a hash of its contents, so
the pods that use it restart by themselves.

## 3. Create the CI job

1. **New Item** > name `yellow-trade-ci` > **Pipeline**.
2. Pipeline > Definition: **Pipeline script from SCM** > **Git**.
3. Repository URL: `https://github.com/Neueda-Learning/chennai-capstone-SE3-Team2.git`,
   credential `github-scm`.
4. Branch: `*/feature/deployed` (or `*/main` once merged).
5. Script Path: `JenkinsfileCI`.
6. Save, then **Build Now** once. The first run registers the poll trigger and the parameters.

Stages: Checkout > Validate build agent > Test (`mvn -B test`, untick `RUN_TESTS` to skip) >
Build and push ECR. The end of the log, and the build's description, show:

```text
IMAGE_TAG=1.7
```

After that, CI polls the branch every 5 minutes and builds only when there are new commits.

## 4. Create the CD job

1. **New Item** > name `yellow-trade-cd` > **Pipeline**.
2. Same repository, credential and branch as CI.
3. Script Path: `JenkinsfileCD`.
4. No trigger: CD is a manual promotion.
5. Save, then **Build Now** once. It fails with "IMAGE_TAG is required"; this is expected
   and only makes **Build with Parameters** appear.

## 5. Run CD

**Build with Parameters**. Usually only `IMAGE_TAG` changes:

```text
IMAGE_TAG=1.3                     
AWS_REGION=ap-south-1
EKS_CLUSTER_NAME=etp-cluster-1
RDS_HOST=database-1.cpekuiuqcl99.ap-south-1.rds.amazonaws.com
RDS_PORT=5432
RDS_DATABASE=trading_system_db
RDS_USER=trading_app
K8S_OVERLAY=k8s/overlays/frontend-lb
AUTO_AUTHORIZE_RDS=true
```

To roll back, run CD again with an older tag.

## 6. What CD does, in order

1. Checks out the repository; stops if `IMAGE_TAG` is empty.
2. Writes the RDS parameters into the workspace's `k8s/base/config.env` (git is not changed).
3. Checks that `etp-cluster-1` is ACTIVE, writes a kubeconfig into the workspace, lists
   the nodes, and checks the EBS CSI driver (Kafka's disk needs it).
4. Runs `jenkins/rds-test.sh`: a short-lived `postgres:16-alpine` pod runs `pg_isready`
   against RDS from inside the cluster.
5. Only if that fails and `AUTO_AUTHORIZE_RDS=true`: allows the cluster VPC's CIDR into
   the RDS security group on 5432 (as README step 4A), and tests again.
6. Checks that all four images exist in ECR with `IMAGE_TAG`.
7. Copies the `yellow-trade-secrets-env` file to `k8s/base/secrets.env`.
8. Writes the registry and `IMAGE_TAG` into the `images:` block of the workspace's
   `k8s/base/kustomization.yaml`, so one apply rolls out the selected tag.
9. Runs `k8s/prepare.sh`: copies `Config/application.yml` and `create-topics.sh` into
   `generated/` and refuses to go on with a missing or empty secret.
10. Deletes the `kafka-topics` Job (Jobs are immutable; it runs again, and creating the
    topics is idempotent), runs `kubectl apply --dry-run=server`, then `kubectl apply -k`.
11. Waits for the four Deployments and the topics Job.
12. Smoke test: `GET /` answers 200 and `/api/v1/accounts/1` answers `AUTH-401` through
    the frontend load balancer. It also warns when `urls.env` names another address.
13. Always deletes `secrets.env` and the kubeconfig from the workspace.

## 7. When a stage fails

| Stage | Cause, fix |
| --- | --- |
| Checkout SCM: authentication failed | `github-scm` is missing on the job, or the token was not approved by the organization. |
| Validate build agent: `docker info` | The `jenkins` user is not in the `docker` group (section 1). |
| Test | A unit test fails: see the log, or untick `RUN_TESTS` to build anyway. |
| Validate existing EKS cluster | The cluster name is wrong or the cluster is gone. EBS driver missing: `k8s/README.md`, Troubleshooting. |
| Retry RDS test | The security group is fine but the VPC peering or routes are missing: `k8s/README.md` step 4A. |
| Verify selected ECR images | The tag was not pushed: use one printed by a successful CI build. |
| Inject secrets: `prepare.sh` errors | A key is missing, empty or quoted in the secret file. It names the key. |
| rollout status timed out | `kubectl -n yellow-trade get pods`, `describe pod`, `logs`: `k8s/README.md`, Troubleshooting. The failure log shows the pods and the last events. |
| Smoke test | The load balancer is still coming up (first deploy), or `urls.env` is out of date. |

## 8. Files

```text
JenkinsfileCI                    CI: test, build, push
JenkinsfileCD                    CD: check, deploy one tag
jenkins/rds-test.sh              EKS -> RDS check, used by CD (also by hand)
jenkins/prereq.md                one-time checks before the first CD run
k8s/base/config.env              non-secret settings (CD overrides the DB_ lines from its parameters)
k8s/base/secrets.env             written by CD from the Jenkins credential; never in git
k8s/base/kustomization.yaml      image names; CD sets the registry and the tag
k8s/overlays/frontend-lb/urls.env   the public address (CORS, activation links)
```
