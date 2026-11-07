import { useQuery } from '@tanstack/react-query'
import { listOfferableServices } from '../../features/provider-platform/api'

/**
 * The services a provider may offer: report's catalogue, which a CareLink manager adds to (GET /api/open/v1/services).
 * Read without signing in, for the application form and the services an approved provider changes.
 */
export function useOfferableServices() {
  return useQuery({ queryKey: ['provider', 'offerable-services'], queryFn: listOfferableServices, staleTime: 5 * 60_000 })
}
