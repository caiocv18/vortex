# 7. Static single-page apps on S3 and CloudFront

- **Status:** Accepted
- **Date:** 2026-11-14
- **Pull request:** #23

## Context

Both front ends are single-page apps that Vite builds into static files: the inventory app (Vue) and the login app (React). Both route in the browser with history mode, so a deep link such as `/products` or `/reset-password/<token>` has no file behind it. The API is not online yet; the demo instance of ADR 0006 will become its origin later. The AWS account runs on the Free plan, with a ceiling of US$ 10/month in gross cost, and the domain's public zone already lives in the foundation stack.

Facts that shaped the design, as of 2026-11-14:

- CloudFront only reads certificates from ACM in `us-east-1`.
- On the Free plan, CloudFront's always free tier covers 1 TB of data out, 10 million requests and 2 million CloudFront Function invocations per month. The flat-rate CloudFront plans are not available to Free plan accounts, and they require AWS WAF.
- Price classes 100 and 200 leave out the South America edges, and the audience is in Brazil.
- The whole `.dev` top-level domain is on the browsers' HSTS preload list.
- Neither `index.html` has an inline script, but Vuetify and react-hot-toast inject `<style>` elements at runtime, and the login app loads Google Fonts.
- The inventory app's generated API client has paths without `/api`, so its base URL must end in `/api`. The login app accepts `returnUrl` only on the origin it was built with.

## Decision

- **Stack:** `infra/terraform/site/`, state key `site/terraform.tfstate`, always in `us-east-1`. A local module, `modules/spa`, is used once per site with `for_each`.
- **Hosts:** the inventory app at the apex domain and the login app at `login.<domain>`. Once the API is online, each app calls it under `/api` on its own host, so neither needs CORS.
- **Certificate:** one ACM certificate for the apex and `*.<domain>`, validated by DNS. The validation records are keyed by domain name, which the first plan already knows; the apex and the wildcard share one CNAME, hence `allow_overwrite`. The distributions take the ARN from `aws_acm_certificate_validation`, so they are only created once the certificate is issued.
- **Buckets:** one private bucket per site, in the account regional namespace, with `BucketOwnerEnforced`, a full public access block, SSE-S3 and a policy that denies requests without TLS. Only its own distribution reads it, through an origin access control (SigV4 on every request) and a policy conditioned on `AWS:SourceArn`. The policy also grants `s3:ListBucket`, so a missing object answers 404 instead of 403.
- **Distributions:** IPv6, HTTP/2 and HTTP/3, `PriceClass_All`, `redirect-to-https`, `GET` and `HEAD` only, compression, the managed `CachingOptimized` cache policy, and SNI with `TLSv1.2_2021`.
- **DNS:** `A`, `AAAA` and `HTTPS` alias records for each host, in the foundation's zone. Route 53 answers alias queries to CloudFront at no charge, but only for a record type that exists, and the `HTTPS` record tells browsers about HTTP/3 before the first connection.
- **Deep links:** a CloudFront Function on the viewer-request event, `spa-rewrite.js`, shared by both distributions. Paths under `/api` pass untouched; a path whose last segment is empty or has no dot becomes `/index.html`; anything else passes. No route of either app has a dot in its last segment.
- **Headers:** one response headers policy for both: HSTS for one year with `includeSubDomains` and `preload`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, a `Permissions-Policy` that turns off camera, microphone and geolocation, and the content security policy as `Content-Security-Policy-Report-Only`. Its `style-src` allows `'unsafe-inline'` for the styles Vuetify and react-hot-toast inject, and Google Fonts for the login app's font stylesheet.
- **Publishing:** `scripts/publicar-fronts.sh` builds each app from a `git archive` export of `HEAD`, with the URLs taken from the stack's outputs. It uploads the hashed assets first, with `public, max-age=31536000, immutable`, then copies the unhashed files with `no-cache`, `index.html` last, removes what the new build no longer has with `sync --delete` (old assets stay), and invalidates `/index.html`.

## Alternatives considered

- **An origin access identity (OAI).** The legacy mechanism: it doesn't support SSE-KMS or requests other than reads, and it doesn't work in regions launched after December 2022. AWS recommends the origin access control.
- **A public bucket with the S3 website endpoint.** The bucket would have to be public, and CloudFront would reach the origin over plain HTTP.
- **Custom error responses that turn 403 and 404 into `/index.html`.** They would also answer a missing asset with the HTML page, and once the API shares the host, its own 403 and 404 responses too.
- **One distribution for both hosts.** It would need host-based routing in the function and one bucket with two prefixes; two distributions keep each site independent.
- **AWS Amplify Hosting.** Build minutes and hosting are priced on their own, and the stack would leave the Terraform pattern of the other stacks.
- **AWS WAF.** At least US$ 5/month for a web ACL, half the budget, for a site that serves static files only.
- **A runtime configuration file instead of URLs written at build time.** It would change both apps' startup code; one build per environment costs nothing today.
- **An enforced content security policy now.** Nothing has been observed under the real hosts yet; Report-Only shows what a policy would block without breaking either app.

## Consequences

- Until the API is online, both apps load. A `GET` under `/api` gets a 404 from S3, and any other method, which includes every authentication call (all of them are `POST`), gets a 403 from CloudFront, which allows only `GET` and `HEAD`. The `/api` behavior that brings the API must allow every method. The function never rewrites `/api`, so the API's own errors will reach the apps as they are.
- The content security policy only reports: violations show up in the browser console and nothing is blocked. It turns into an enforced `Content-Security-Policy` with the item that puts the API online.
- The URLs are part of the build: a new domain or environment needs a new build, not a configuration change.
- `index.html` and the other unhashed files are revalidated on every visit; the hashed assets are cached for a year, by CloudFront and by the browser.
- No WAF and no access logs: abuse would show up only as usage beyond the always free tier. On the Free plan that spends the account's credits instead of billing a card, and the account closes when they run out; AWS keeps the content for 90 days, and an upgrade to the Paid plan restores it. The budget alerts of the foundation stack only notify, so the response is to disable both distributions.
- A new account can be held for verification before its first distribution ("Your account must be verified before you can add new CloudFront resources"); the apply then fails until AWS Support clears the account.
- Cost: inside the always free tier, CloudFront and the function cost nothing; Route 53 does not charge the alias queries; ACM certificates are free; the buckets hold a few megabytes. The first 1,000 invalidation paths of each month are free.

## References

- `infra/terraform/site/`: the stack, with `spa-rewrite.js` and `modules/spa/`.
- `scripts/publicar-fronts.sh`: the build and the upload.
- `infra/terraform/README.md`, section "Site": how to apply and publish.
- [ADR 0006](0006-on-demand-demo-instance-behind-session-manager.md): the instance that will be the API's origin.
