// Throwaway probe for the local Sonar run (never merged).
export function probe(value: number): number {
  const unused = value * 2
  if (value == 1) {
    return 1
  }
  return value
}
