import { createContext, useContext } from 'react'

type SelectedElder = { elderId: number | null; setElderId: (elderId: number | null) => void }

export const SelectedElderContext = createContext<SelectedElder>({ elderId: null, setElderId: () => {} })

/** The elder the family member is currently following; null means "the first linked elder". */
export function useSelectedElder() {
  return useContext(SelectedElderContext)
}
