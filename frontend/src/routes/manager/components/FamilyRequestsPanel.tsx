import { Button, Eyebrow, KeyValueList, MetaText, Tag } from '../../../shared/components/ui'
import type { TagTone } from '../../../shared/components/ui'
import type { ElderCareRequest } from '../../../shared/api/profile'
import { formatDate } from '../lib/nextVisit'
import styles from './FamilyRequestsPanel.module.css'

const OUTCOME_TAG: Record<ElderCareRequest['outcome'], { label: string; tone: TagTone }> = {
  SUBMITTED: { label: 'Open', tone: 'ink' },
  PLANNED: { label: 'Planned', tone: 'accent' },
  DECLINED: { label: 'Declined', tone: 'danger' },
}

/**
 * The care plan rail's "Requested by family" section: each application the family made for the
 * elder, newest first, with whether a published version has answered it, each activity tagged
 * against the plan being edited, the family's notes, and Decline for an open service application.
 */
export function FamilyRequestsPanel({
  requests,
  label,
  needTag,
  onDecline,
}: {
  requests: ElderCareRequest[]
  /** A stored care need as the manager reads it: the catalog label, or the family's own words. */
  label: (need: string) => string
  /** How the plan being edited answers one need. */
  needTag: (need: string) => { label: string; tone: TagTone }
  onDecline: (request: ElderCareRequest) => void
}) {
  return (
    <div className={styles.panel}>
      <Eyebrow>Requested by family</Eyebrow>
      {requests.map((request) => {
        const outcome = OUTCOME_TAG[request.outcome]
        const name = `${request.source === 'INTAKE' ? 'Intake application' : 'Service application'} #${request.applicationId}`
        return (
          <section key={`${request.source}-${request.applicationId}`} aria-label={name} className={styles.request}>
            <MetaText>
              {name} · {formatDate(request.submittedAt)}{' '}
              <Tag tone={outcome.tone} compact>
                {outcome.label}
              </Tag>
            </MetaText>
            <KeyValueList
              variant="ruled"
              items={request.careNeeds.map((need) => {
                const tag = needTag(need)
                return {
                  label: label(need),
                  value: (
                    <Tag tone={tag.tone} compact>
                      {tag.label}
                    </Tag>
                  ),
                }
              })}
            />
            {request.notes && <MetaText>“{request.notes}”</MetaText>}
            {request.outcome === 'DECLINED' && request.declineReason && (
              <MetaText tone="faint">Declined: {request.declineReason}</MetaText>
            )}
            {request.source === 'SERVICE_APPLICATION' && request.outcome === 'SUBMITTED' && (
              <div>
                <Button variant="ghost" onClick={() => onDecline(request)}>
                  Decline
                </Button>
              </div>
            )}
          </section>
        )
      })}
    </div>
  )
}
