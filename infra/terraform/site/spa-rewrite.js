// CloudFront Function (cloudfront-js-2.0), viewer-request event, shared by both
// distributions (ADR 0007). Each single-page app has one index.html and routes in the
// browser (createWebHistory, BrowserRouter), so a deep link such as /products or
// /reset-password/abc has no object behind it in the bucket. This rewrites those
// requests to /index.html before CloudFront looks in its cache or at the origin.
//
//   /api and /api/...           untouched: the API behavior arrives with the backend,
//                               and until then a missing object answers 404, not HTML
//   last segment empty or       /index.html (/, /products, /products/, /reset-password/abc)
//   without a dot
//   anything else               untouched (/assets/index-x.js, /layers.css, /favicon.ico)
//
// No route of either app has a dot in its last segment, which is what tells a route
// from a file. A custom error response would do the same for every 403 or 404, and
// would answer the API's own 404s with the HTML page too.
function handler(event) {
  const request = event.request;
  const uri = request.uri;

  if (uri === '/api' || uri.startsWith('/api/')) {
    return request;
  }

  const lastSegment = uri.substring(uri.lastIndexOf('/') + 1);
  if (lastSegment === '' || !lastSegment.includes('.')) {
    request.uri = '/index.html';
  }
  return request;
}
