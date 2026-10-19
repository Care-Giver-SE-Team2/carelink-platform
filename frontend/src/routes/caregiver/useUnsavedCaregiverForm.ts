import { useEffect } from 'react'

/** Keep drafts in memory only; warn before document unload or in-app link navigation. */
export function useUnsavedCaregiverForm(dirty: boolean) {
  useEffect(() => {
    if (!dirty) return
    const unload = (event: BeforeUnloadEvent) => event.preventDefault()
    const click = (event: MouseEvent) => {
      const anchor = event.target instanceof Element ? event.target.closest('a[href]') : null
      if (anchor && !window.confirm('Leave this page and discard unsaved changes? If a save result is unknown, check the saved record before submitting again.')) {
        event.preventDefault(); event.stopPropagation()
      }
    }
    window.addEventListener('beforeunload', unload)
    document.addEventListener('click', click, true)
    return () => { window.removeEventListener('beforeunload', unload); document.removeEventListener('click', click, true) }
  }, [dirty])
}
