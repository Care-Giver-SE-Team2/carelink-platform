import { useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { SelectedElderContext } from './selectedElder'

/**
 * Holds the elder the family member is following, shared by every family page so a choice made in
 * the desktop rail, on Home or on Schedule carries across. Null means "the first linked elder".
 */
export function SelectedElderProvider({ children }: { children: ReactNode }) {
  const [elderId, setElderId] = useState<number | null>(null)
  const value = useMemo(() => ({ elderId, setElderId }), [elderId])
  return <SelectedElderContext.Provider value={value}>{children}</SelectedElderContext.Provider>
}
