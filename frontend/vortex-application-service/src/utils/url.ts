/**
 * Removes every trailing '/' from a URL ('http://host:8081//' -> 'http://host:8081').
 * A plain scan instead of a regex: same result as `url.replace(/\/+$/, '')`, in linear time.
 * Module without imports, so both the app (Vite) and the Playwright support files can use it.
 */
export function withoutTrailingSlash(url: string): string {
  let end = url.length
  while (end > 0 && url[end - 1] === '/') {
    end--
  }
  return url.slice(0, end)
}
