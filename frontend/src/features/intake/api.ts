import { api } from '../../shared/api/client'
import type {
  IntakeApplication,
  IntakeApplicationPage,
  IntakeListQuery,
} from './types'

/**
 * Queries the current family's applications with pagination and an optional status.
 * @param query Page number, page size and optional application status
 * @param signal Cancels an outstanding request
 * @return The matching application page
 * @author Wang Zhili
 */
export function listIntakeApplications(
  { page, size, status }: IntakeListQuery,
  signal?: AbortSignal,
): Promise<IntakeApplicationPage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) query.set('status', status)
  return api<IntakeApplicationPage>('/intake-applications?' + query, { signal })
}

/**
 * Reads an application that the current family is authorised to view.
 * @param id Application identifier
 * @param signal Cancels an outstanding request
 * @return The application and its recorded review details
 * @author Wang Zhili
 */
export function getIntakeApplication(id: string, signal?: AbortSignal): Promise<IntakeApplication> {
  return api<IntakeApplication>('/intake-applications/' + encodeURIComponent(id), { signal })
}
