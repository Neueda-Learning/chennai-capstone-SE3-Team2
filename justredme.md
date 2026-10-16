# CloudFront in front of Yellow Trade (HTTPS, no domain or certificate needed)

An optional step after README step 8 (`frontend-lb`). CloudFront gives the app an
`https://<id>.cloudfront.net` address with AWS's certificate and forwards everything to the
**one** frontend load balancer we already have. Every command runs from the repository root.

```text
browser --HTTPS--> https://<id>.cloudfront.net --HTTP:80--> frontend ELB --> nginx (frontend pod)
                                                                              /api/, /onboarding/ -> order-service:8081
                                                                              /auth/, /activate   -> auth-service:3000
                                                                              everything else     -> the Angular app
```

## How this differs from Rahul's `cloudfront/CLOUDFRONT-SETUP.md`

| Rahul's kit | Here | Why |
| --- | --- | --- |
| 3 origins: frontend :80, auth :3000, order :8081, each a public ELB | 1 origin: the frontend ELB, port 80 | Our nginx already forwards the API paths inside the cluster (`base/frontend/default.conf.template`). auth and order stay internal. |
| `four-lb/urls.env`, ConfigMap `etp-urls`, `FRONTEND_URL`, `URL_REWRITES` | `frontend-lb/urls.env`, ConfigMap `yellow-trade-urls`: `CORS_ALLOWED_ORIGINS`, `ACTIVATION_LINK_BASE_URL`, `ACTIVATION_HOME_URL` | Our frontend image is built with the "docker" environment: it already calls its own origin, so nothing needs rewriting. |
| Patch the ConfigMap by hand and `rollout restart`; never `apply -k` | Edit `urls.env` and apply (or run JenkinsfileCD) | Our ConfigMap names carry a hash of the contents, and JenkinsfileCD writes the image tag into the kustomization. A hand-made ConfigMap would be replaced by the next apply. |
| `/socket.io/*`, `/health`, `/actuator/*` behaviors | none | Our app has no socket channel, and the health endpoints are for the probes only. |

## Before you start

- README steps 1-9 are done: `http://<LB>` works in the browser.
- The AWS CLI user may use CloudFront (`cloudfront:CreateDistribution`, `GetDistribution*`,
  `UpdateDistribution`, `CreateInvalidation`).
- Tokens travel in the `Authorization` header and never in a cookie (`common/cors.ts` in
  auth-service, `CorsConfig.java` in order-service). So CloudFront needs no cookie settings,
  but it **must** forward `Authorization`.

## Step 1. The frontend load balancer's name

```bash
aws eks update-kubeconfig --region ap-south-1 --name etp-cluster-1
LB=$(kubectl -n yellow-trade get svc frontend -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
echo "$LB"                                   # ...ap-south-1.elb.amazonaws.com
curl -s -o /dev/null -w '%{http_code}\n' "http://$LB/"      # 200
```

## Step 2. Create the distribution

The settings, whichever way you create it:

**Origin**: one, named `frontend-elb`.

| Setting | Value |
| --- | --- |
| Origin domain | `$LB`: the hostname only, no `http://`, no port |
| Protocol | **HTTP only**: the ELB has no certificate. HTTPS here gives 502. |
| HTTP port | 80 |

**Behaviors**: all on the `frontend-elb` origin.

| Path pattern | Methods | Cache policy | Origin request policy |
| --- | --- | --- | --- |
| `/api/*` | all (GET, HEAD, OPTIONS, PUT, POST, PATCH, DELETE) | **CachingDisabled** | **AllViewerExceptHostHeader** |
| `/auth/*` | all | CachingDisabled | AllViewerExceptHostHeader |
| `/onboarding/*` | all | CachingDisabled | AllViewerExceptHostHeader |
| `/activate*` | all | CachingDisabled | AllViewerExceptHostHeader |
| Default (`*`) | GET, HEAD | CachingOptimized | none |

All of them: viewer protocol policy **Redirect HTTP to HTTPS**, compression on.

- The four API paths are the same four `location`s our nginx forwards. Keep them in step:
  a path missing here falls to the default behavior, where POST is refused (403) and GET
  answers are cached.
- **AllViewerExceptHostHeader** is what forwards `Authorization`, `Content-Type`, `Origin`
  and query strings. Without it sign-in works and every call after it gets 401.
- **CachingDisabled**: never cache logins, tokens or account data.
- The default behavior may cache: the Angular bundles have hashed names, and nginx sends
  `index.html` with `Cache-Control: no-cache`, so a new frontend image shows at once.

### Console

**CloudFront > Create distribution**, origin as above, default behavior as above, then
**Behaviors > Create behavior** once per API path. Leave WAF off unless you want to pay for
it. Price class: "Use only North America, Europe, Asia, Middle East, and Africa" is enough.

### CLI (the same settings)

```bash
cat > /tmp/cf-config.json <<EOF
{
  "CallerReference": "yellow-trade-$(date +%s)",
  "Comment": "Yellow Trade: frontend-lb ELB over HTTPS",
  "Enabled": true,
  "PriceClass": "PriceClass_200",
  "HttpVersion": "http2",
  "Origins": { "Quantity": 1, "Items": [ {
    "Id": "frontend-elb",
    "DomainName": "$LB",
    "CustomOriginConfig": {
      "HTTPPort": 80, "HTTPSPort": 443, "OriginProtocolPolicy": "http-only",
      "OriginReadTimeout": 30, "OriginKeepaliveTimeout": 5
    }
  } ] },
  "DefaultCacheBehavior": {
    "TargetOriginId": "frontend-elb",
    "ViewerProtocolPolicy": "redirect-to-https",
    "Compress": true,
    "AllowedMethods": { "Quantity": 2, "Items": ["GET", "HEAD"],
      "CachedMethods": { "Quantity": 2, "Items": ["GET", "HEAD"] } },
    "CachePolicyId": "658327ea-f89d-4fab-a63d-7e88639e58f6"
  },
  "CacheBehaviors": { "Quantity": 4, "Items": [
$(for p in '/api/*' '/auth/*' '/onboarding/*' '/activate*'; do cat <<B
    { "PathPattern": "$p",
      "TargetOriginId": "frontend-elb",
      "ViewerProtocolPolicy": "redirect-to-https",
      "Compress": true,
      "AllowedMethods": { "Quantity": 7, "Items": ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"],
        "CachedMethods": { "Quantity": 2, "Items": ["GET", "HEAD"] } },
      "CachePolicyId": "4135ea2d-6df8-44a3-9df3-4b5a84be39ad",
      "OriginRequestPolicyId": "b689b0a8-53d0-40ab-baf2-68738e2966ac" },
B
done | sed '$ s/},$/}/')
  ] }
}
EOF
python3 -m json.tool /tmp/cf-config.json >/dev/null && echo "config OK"

aws cloudfront create-distribution --distribution-config file:///tmp/cf-config.json \
  --query 'Distribution.{Id:Id,Domain:DomainName,Status:Status}' --output table
```

The policy IDs are AWS's managed ones, the same in every account:
`658327ea-...` CachingOptimized, `4135ea2d-...` CachingDisabled,
`b689b0a8-...` AllViewerExceptHostHeader.

Note the `Id` and the `Domain` (`d....cloudfront.net`):

```bash
CF_ID=<Id>
CF=$(aws cloudfront get-distribution --id "$CF_ID" --query 'Distribution.DomainName' --output text)
echo "$CF"
```

## Step 3. Wait until it is deployed (5-10 minutes)

```bash
aws cloudfront wait distribution-deployed --id "$CF_ID"
aws cloudfront get-distribution --id "$CF_ID" --query 'Distribution.Status' --output text   # Deployed
```

## Step 4. Tell the services the new address

The browser now sends `Origin: https://$CF`. nginx passes the ELB's name as the host, so
order-service and auth-service see a cross-origin call and refuse it with
**403 "Invalid CORS request"** unless the CloudFront address is in `urls.env`. The activation
emails should link to it too.

```bash
sed -i -E "s#=http://[^/]+#=https://$CF#" k8s/overlays/frontend-lb/urls.env
cat k8s/overlays/frontend-lb/urls.env
# CORS_ALLOWED_ORIGINS=https://d....cloudfront.net
# ACTIVATION_LINK_BASE_URL=https://d....cloudfront.net/activate
# ACTIVATION_HOME_URL=https://d....cloudfront.net/
```

`https`, no port, no trailing slash on the first line. Then commit it and run **JenkinsfileCD**
(Build with Parameters, the current `IMAGE_TAG`), or by hand:

```bash
bash k8s/prepare.sh && kubectl apply -k k8s/overlays/frontend-lb   # auth and order restart by themselves
```

Then `http://$LB` stops working in the browser (403 CORS), which is expected: use CloudFront.
To allow both while you test, list both in the first line, comma-separated:
`CORS_ALLOWED_ORIGINS=https://$CF,http://$LB`.

JenkinsfileCD's smoke test still checks the ELB. It prints
`WARNING: urls.env does not name http://<LB>`. That is harmless here: the build passes.

## Step 5. Check

```bash
curl -s -o /dev/null -w '%{http_code}\n' "https://$CF/"                       # 200
curl -s "https://$CF/api/v1/accounts/1"                                        # {"errorCode":"AUTH-401",...}
curl -s -o /dev/null -w '%{http_code}\n' -X POST "https://$CF/onboarding/applications" \
  -H 'Content-Type: application/json' -d '{}'                                 # 422
curl -s -o /dev/null -w '%{http_code}\n' -X OPTIONS "https://$CF/api/v1/accounts/1" \
  -H "Origin: https://$CF" -H 'Access-Control-Request-Method: GET'            # 200 (CORS preflight allowed)
```

Then in the browser, with DevTools **Network** open: apply for an account, open the
activation email (its link starts with `https://$CF/activate`), set a password, sign in.
Every request goes to `https://$CF/...`, and the `/api/v1/...` calls after sign-in return 200.

## Changing things later

| Change | Do |
| --- | --- |
| New images (JenkinsfileCI + JenkinsfileCD) | Nothing. `index.html` is not cached, so the new bundles show at once. If an old page still shows: `aws cloudfront create-invalidation --distribution-id "$CF_ID" --paths '/index.html'`. |
| The `frontend` Service was deleted and recreated (new ELB name) | Point the origin at the new name (below). `urls.env` keeps the CloudFront address and does not change. |
| A new path in `default.conf.template` | Add the same path as a behavior (step 2 table). |
| Moving to the `ingress` overlay (README step 10) | Point the origin at the ALB's name instead. |

New origin name, from the CLI:

```bash
LB=$(kubectl -n yellow-trade get svc frontend -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
aws cloudfront get-distribution-config --id "$CF_ID" > /tmp/cf-current.json
python3 - "$LB" <<'PY'
import json, sys
d = json.load(open('/tmp/cf-current.json'))
d['DistributionConfig']['Origins']['Items'][0]['DomainName'] = sys.argv[1]
json.dump(d['DistributionConfig'], open('/tmp/cf-new.json', 'w'))
open('/tmp/cf-etag.txt', 'w').write(d['ETag'])
PY
aws cloudfront update-distribution --id "$CF_ID" --if-match "$(cat /tmp/cf-etag.txt)" \
  --distribution-config file:///tmp/cf-new.json --query 'Distribution.Status' --output text
aws cloudfront wait distribution-deployed --id "$CF_ID"
```

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| 403 "Invalid CORS request" | `CORS_ALLOWED_ORIGINS` is not exactly `https://$CF` (https, no slash), or it was not applied. Step 4. |
| Sign-in works, then every call 401 | The API behavior has no origin request policy, so `Authorization` is dropped. AllViewerExceptHostHeader on all four API paths. |
| 403 from CloudFront on POST/PUT/DELETE (an HTML or XML error page, not JSON) | The path fell to the default behavior (GET, HEAD only). Add it as an API behavior. |
| 502 | Origin protocol is HTTPS (must be HTTP only), or the origin name is wrong or an old ELB. `kubectl -n yellow-trade get svc frontend`. |
| 504 | The ELB does not answer: `curl http://$LB/` from your box; check the pods (README step 9). |
| Activation email links to the ELB | `ACTIVATION_LINK_BASE_URL` not updated or not applied. Step 4. |
| Old page after a deploy | Invalidate `/index.html` (above) and hard-refresh (Ctrl+Shift+R). |

The ELB itself stays public on HTTP. To force everyone through CloudFront, allow only
CloudFront's managed prefix list (`com.amazonaws.global.cloudfront.origin-facing`) on port 80
in the ELB's security group. This is a later step and not needed for the demo.

## Cleanup

A disabled distribution costs nothing. Delete it only after disabling:

```bash
aws cloudfront get-distribution-config --id "$CF_ID" > /tmp/cf-current.json
python3 -c "import json; d=json.load(open('/tmp/cf-current.json')); c=d['DistributionConfig']; c['Enabled']=False; json.dump(c, open('/tmp/cf-new.json','w')); open('/tmp/cf-etag.txt','w').write(d['ETag'])"
aws cloudfront update-distribution --id "$CF_ID" --if-match "$(cat /tmp/cf-etag.txt)" \
  --distribution-config file:///tmp/cf-new.json >/dev/null
aws cloudfront wait distribution-deployed --id "$CF_ID"
aws cloudfront delete-distribution --id "$CF_ID" \
  --if-match "$(aws cloudfront get-distribution --id "$CF_ID" --query ETag --output text)"
```

Then set `urls.env` back to `http://<LB>` (README step 8) and apply.
