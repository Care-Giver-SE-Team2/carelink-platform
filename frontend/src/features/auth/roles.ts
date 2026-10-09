/** The roles a CareLink account can hold, without Spring's ROLE_ prefix. */
export type CareLinkRole = 'MANAGER' | 'CAREGIVER' | 'FAMILY' | 'ELDER'

/** Each role's client. Order matters: an account with several roles lands on the first match. */
const homes: [CareLinkRole, string][] = [
  ['ELDER', '/elder'],
  ['FAMILY', '/family'],
  ['CAREGIVER', '/caregiver'],
  ['MANAGER', '/manager'],
]

/** Whether the roles from GET /api/auth/me include this one (with or without the ROLE_ prefix). */
export function hasRole(roles: string[], role: CareLinkRole): boolean {
  return roles.some((value) => value.replace(/^ROLE_/, '') === role)
}

/**
 * The client a signed-in user belongs in.
 * @param roles Roles from GET /api/auth/me
 * @return Path of that role's client, or null when the account has none
 */
export function homePathFor(roles: string[]): string | null {
  return homes.find(([role]) => hasRole(roles, role))?.[1] ?? null
}
