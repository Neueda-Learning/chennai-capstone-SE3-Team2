# @

Binding contract for the Portfolio and P&L extension, built in Sprint 10. Mandatory for this programme, and safe to build first, because none of the other extensions depend on it.  These routes are served by the Trade REST API on 8080, as a module beside the order and account routes. They are not a separate deployable.  ## Why this is a separate set of routes  The Trade REST API already returns positions. It returns them unpriced: quantity and average cost, nothing more. Pricing a position needs a live quote, and pulling a quote inside the order write path would put a third-party HTTP call, with its own latency and its own failure modes, into the transaction that records a trade. That is the wrong trade-off for a system of record.  These routes read the same positions, price them against the Fauxnance API, compute profit and loss, and are allowed to fail without stopping anyone from trading. Keep the module\'s own failure contained: a pricing outage answers on these paths and leaves order placement alone.  ## Where the numbers come from  | Figure | Source | |---|---| | Quantity, average cost | `positions` in Postgres, or a projection maintained from `trade-events` | | Last price | Fauxnance `GET /quotes?symbols=A,B,C`, batched, maximum 25 symbols per call | | Cash balance | Trade REST API `GET /api/v1/accounts/{id}/balance` | | Realised profit and loss | Computed from the sell orders in `orders`, or accumulated from `trade-events` |  Batch the quote call. One request covering 25 symbols costs the same single unit of quota as one request covering one symbol. A per-position loop over ten holdings burns ten times the quota for the same answer, and the daily quota is 2000.  ## Definitions  Get these right; they are the assessment.  - **Cost basis** for a position is `quantity * averageCost`. Average cost is weighted: a buy   recalculates it as `(oldQty * oldAvg + newQty * fillPrice) / (oldQty + newQty)`. A sell reduces   quantity and leaves average cost unchanged. - **Market value** is `quantity * lastPrice`. - **Unrealised profit and loss** is `marketValue - costBasis`. It changes with every price tick   and is never persisted as a fact. - **Realised profit and loss** is booked at the moment of a sell:   `(fillPrice - averageCostAtSale) * quantitySold`. Once booked it never changes, so it is   accumulated, not recomputed from current prices. - **Total portfolio value** is `cashBalance + sum(marketValue)`.  A common error is to compute realised profit and loss from today\'s price. Realised profit and loss has nothing to do with today\'s price.  ## Staleness  Fauxnance serves delayed quotes and can return a stale value when its upstreams are unavailable. Every priced figure therefore carries `priceAsOf` and `stale`. Render a stale portfolio with a visible marker rather than hiding it, and never present a stale valuation as live.  If pricing is unavailable for every symbol, return `503` with `MKT-503`. If pricing is unavailable for some symbols, return `200`, omit the price on those positions, set their `stale` to `true`, and set `partial` on the summary. 

The version of the OpenAPI document: 1.0.0

## Building

To install the required dependencies and to build the typescript sources run:

```console
npm install
npm run build
```

## Publishing

First build the package then run `npm publish dist` (don't forget to specify the `dist` folder!)

## Consuming

Navigate to the folder of your consuming project and run one of next commands.

_published:_

```console
npm install @ --save
```

_without publishing (not recommended):_

```console
npm install PATH_TO_GENERATED_PACKAGE/dist.tgz --save
```

_It's important to take the tgz file, otherwise you'll get trouble with links on windows_

_using `npm link`:_

In PATH_TO_GENERATED_PACKAGE/dist:

```console
npm link
```

In your project:

```console
npm link 
```

__Note for Windows users:__ The Angular CLI has troubles to use linked npm packages.
Please refer to this issue <https://github.com/angular/angular-cli/issues/8284> for a solution / workaround.
Published packages are not effected by this issue.

### General usage

In your Angular project:

```typescript

import { ApplicationConfig } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideApi } from '';

export const appConfig: ApplicationConfig = {
    providers: [
        // ...
        provideHttpClient(),
        provideApi()
    ],
};
```

**NOTE**
If you're still using `AppModule` and haven't [migrated](https://angular.dev/reference/migrations/standalone) yet, you can still import an Angular module:
```typescript
import { ApiModule } from '';
```

If different from the generated base path, during app bootstrap, you can provide the base path to your service.

```typescript
import { ApplicationConfig } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideApi } from '';

export const appConfig: ApplicationConfig = {
    providers: [
        // ...
        provideHttpClient(),
        provideApi('http://localhost:9999')
    ],
};
```

```typescript
// with a custom configuration
import { ApplicationConfig } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideApi } from '';

export const appConfig: ApplicationConfig = {
    providers: [
        // ...
        provideHttpClient(),
        provideApi({
            withCredentials: true,
            username: 'user',
            password: 'password'
        })
    ],
};
```

```typescript
// with factory building a custom configuration
import { ApplicationConfig } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideApi, Configuration } from '';

export const appConfig: ApplicationConfig = {
    providers: [
        // ...
        provideHttpClient(),
        {
            provide: Configuration,
            useFactory: (authService: AuthService) => new Configuration({
                    basePath: 'http://localhost:9999',
                    withCredentials: true,
                    username: authService.getUsername(),
                    password: authService.getPassword(),
            }),
            deps: [AuthService],
            multi: false
        }
    ],
};
```

### Using multiple OpenAPI files / APIs

In order to use multiple APIs generated from different OpenAPI files,
you can create an alias name when importing the modules
in order to avoid naming conflicts:

```typescript
import { provideApi as provideUserApi } from 'my-user-api-path';
import { provideApi as provideAdminApi } from 'my-admin-api-path';
import { HttpClientModule } from '@angular/common/http';
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
    providers: [
        // ...
        provideHttpClient(),
        provideUserApi(environment.basePath),
        provideAdminApi(environment.basePath),
    ],
};
```

### Customizing path parameter encoding

Without further customization, only [path-parameters][parameter-locations-url] of [style][style-values-url] 'simple'
and Dates for format 'date-time' are encoded correctly.

Other styles (e.g. "matrix") are not that easy to encode
and thus are best delegated to other libraries (e.g.: [@honoluluhenk/http-param-expander]).

To implement your own parameter encoding (or call another library),
pass an arrow-function or method-reference to the `encodeParam` property of the Configuration-object
(see [General Usage](#general-usage) above).

Example value for use in your Configuration-Provider:

```typescript
new Configuration({
    encodeParam: (param: Param) => myFancyParamEncoder(param),
})
```

[parameter-locations-url]: https://github.com/OAI/OpenAPI-Specification/blob/main/versions/3.1.0.md#parameter-locations
[style-values-url]: https://github.com/OAI/OpenAPI-Specification/blob/main/versions/3.1.0.md#style-values
[@honoluluhenk/http-param-expander]: https://www.npmjs.com/package/@honoluluhenk/http-param-expander
