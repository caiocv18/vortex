import { describe, it, expect } from 'vitest'
import { withoutTrailingSlash } from '@/utils/url'

describe('withoutTrailingSlash', () => {
  it.each([
    ['http://localhost:8081', 'http://localhost:8081'],
    ['http://localhost:8081/', 'http://localhost:8081'],
    ['http://localhost:8081///', 'http://localhost:8081'],
    ['http://localhost:3001/app/', 'http://localhost:3001/app'],
    ['http://localhost:3001/a//b', 'http://localhost:3001/a//b'],
    ['//a//', '//a'],
    ['http://x/?q=/', 'http://x/?q='],
    [' / ', ' / '],
    ['/', ''],
    ['///', ''],
    ['', ''],
  ])('turns %j into %j', (input, expected) => {
    expect(withoutTrailingSlash(input)).toBe(expected)
  })

  it('handles a long run of slashes without backtracking', () => {
    const input = `http://x${'/'.repeat(100_000)}`

    expect(withoutTrailingSlash(input)).toBe('http://x')
  })
})
