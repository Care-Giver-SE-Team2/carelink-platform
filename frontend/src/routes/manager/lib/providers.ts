/** 109 → "PRV-0109", the reference a provider is quoted by. */
export function providerRef(providerId: number): string {
  return `PRV-${String(providerId).padStart(4, '0')}`
}

/** ["AMK", "BSH"] → "AMK, BSH"; null when the list is empty. */
export function listLabel(items: string[]): string | null {
  return items.length ? items.join(', ') : null
}

/** The provider's state at a glance: suspended, failing webhooks, retrying ones, or fine. */
export function healthTag(provider: {
  suspended: boolean
  health: { failed: number; retrying: number }
}): { label: string; tone: 'danger' | 'accent' | 'muted' | 'ink' } {
  if (provider.suspended) return { label: 'SUSPENDED', tone: 'ink' }
  if (provider.health.failed > 0) return { label: `${provider.health.failed} FAILED`, tone: 'danger' }
  if (provider.health.retrying > 0) return { label: `${provider.health.retrying} RETRYING`, tone: 'muted' }
  return { label: 'HEALTHY', tone: 'accent' }
}
