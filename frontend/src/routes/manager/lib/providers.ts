/** 109 → "PRV-0109", the reference a provider is quoted by. */
export function providerRef(providerId: number): string {
  return `PRV-${String(providerId).padStart(4, '0')}`
}

/** ["AMK", "BSH"] → "AMK, BSH"; null when the list is empty. */
export function listLabel(items: string[]): string | null {
  return items.length ? items.join(', ') : null
}
