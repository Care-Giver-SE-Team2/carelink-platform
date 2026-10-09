import { cleanup, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import userEvent from '@testing-library/user-event'
import { FamilyReportContent } from './FamilyReportContent'
import { ReportCareContext, ReportCompleteness, ReportCorrections } from './ReportNotes'
import type { ReportSection } from '../../../features/reports/types'

afterEach(cleanup)

describe('Family report presentation', () => {
  it('shows only saved care context and marks missing historical fields without borrowing a visit caregiver', () => {
    const { rerender } = render(<ReportCareContext sections={[{ title: 'Overview', body:
      'Care plan version 3 · 6.5 h a week.\nMain caregiver: Dr. Mei.\nVisits: 2 of 3 carried out.',
    }]} />)
    expect(screen.getByLabelText('Care context at report generation')).toHaveTextContent('Main caregiver: Dr. Mei · Care plan v3 · 6.5 h/week')
    expect(screen.queryByText(/Visits:/)).not.toBeInTheDocument()
    rerender(<ReportCareContext sections={[{ title: 'Service completion', body: 'Mon 21 Sep · Amy · verified' }]} />)
    expect(screen.getByLabelText('Care context at report generation')).toHaveTextContent('Main caregiver: Not recorded · Care plan: Not recorded')
    expect(screen.queryByText(/Amy/)).not.toBeInTheDocument()
  })

  it('retains an explicitly absent care plan instead of inventing its version or hours', () => {
    render(<ReportCareContext sections={[{ key: 'overview', title: 'Overview', body: 'No care plan in force.' }]} />)
    expect(screen.getByLabelText('Care context at report generation')).toHaveTextContent('Main caregiver: Not recorded · No care plan in force')
  })

  it('displays saved figures, daily ranges and flags without inferring clinical advice', () => {
    const sections: ReportSection[] = [
      { key: 'overview', title: 'Overview', body: 'Care plan version 3 · 6.5 h a week.', figures: [
        { key: 'visits', label: 'Visits carried out', value: 2, outOf: 3, unit: null },
        { key: 'fulfilment', label: 'Fulfilment', value: 66.67, outOf: null, unit: '%' },
      ] },
      { key: 'vital-signs', title: 'Vital signs', body: 'Systolic 128–142 mmHg', series: [
        { key: 'systolic', label: 'Systolic', unit: 'mmHg', points: [
          { at: '2026-09-14', low: 128, high: 136, flagged: false },
          { at: '2026-09-16', low: 142, high: 142, flagged: true },
        ] },
      ] },
    ]
    render(<FamilyReportContent sections={sections} />)
    expect(screen.getByText('2 of 3')).toBeInTheDocument()
    expect(screen.getByText('66.67%')).toBeInTheDocument()
    expect(screen.queryByText('Fulfilment')).not.toBeInTheDocument()
    const chart = screen.getByRole('img', { name: /Systolic: daily range 128–142 mmHg; 2 recorded days; 1 flagged day/ })
    expect(chart.querySelectorAll('circle')).toHaveLength(2)
    expect(screen.queryByText('Systolic 128–142 mmHg', { exact: true })).not.toBeInTheDocument()
    expect(screen.getByText('1 day with readings flagged at recording')).toBeInTheDocument()
    expect(screen.getByText('Daily ranges for Systolic')).toBeInTheDocument()
    expect(screen.queryByText(/normal|healthy|diagnosis/i)).not.toBeInTheDocument()
  })

  it('keeps visit evidence and unrecognised free text, rendering markup as text', () => {
    const unstructured = '<img src=x onerror=alert(1)> · note · with · extra · delimiters · retained'
    render(<FamilyReportContent sections={[
      { title: 'Overview', body: 'No plan recorded.' },
      { title: 'Service completion', body: 'Mon 14 Sep 09:00 · Personal care · Daniel Goh · verified · no evidence\n' + unstructured },
    ]} />)
    const visits = screen.getByRole('region', { name: 'Visits' })
    expect(within(visits).getByText('Daniel Goh · no evidence')).toBeInTheDocument()
    expect(within(visits).getByText('verified')).toBeInTheDocument()
    expect(visits.textContent).toContain('note · with · extra · delimiters · retained')
    expect(visits.querySelector('img')).toBeNull()
  })

  it('gives completed visits awaiting confirmation their own status row', () => {
    render(<FamilyReportContent sections={[{ title: 'Service completion', body:
      "Mon 21 Sep 09:00 · Personal care · Mei · awaiting the elder's confirmation · no evidence",
    }]} />)
    expect(screen.getByText("awaiting the elder's confirmation")).toBeInTheDocument()
    expect(screen.getByText('Mei · no evidence')).toBeInTheDocument()
  })

  it('shows service completion bars from saved counts', () => {
    render(<FamilyReportContent sections={[{ title: 'Services', body: 'Personal care: 2 of 3 carried out', figures: [
      { key: 'personal-care', label: 'Personal care', value: 2, outOf: 3, unit: null },
    ] }]} />)
    expect(screen.getByRole('progressbar', { name: 'Personal care' })).toHaveAttribute('value', '2')
    expect(screen.getByRole('progressbar', { name: 'Personal care' })).toHaveAttribute('max', '3')
  })

  it('retains caregiver attribution and the incident resolution time in structured rows', () => {
    render(<FamilyReportContent sections={[
      { title: 'Observations', body: 'Mon 21 Sep · Mei: Comfortable after the visit.' },
      { title: 'Incidents', body: 'Tue 22 Sep 10:00 · Fall reported · No injury. · resolved Tue 22 Sep 11:02' },
    ]} />)
    expect(screen.getByText('Mon 21 Sep · Mei')).toBeInTheDocument()
    expect(screen.getByText('Comfortable after the visit.').tagName).toBe('BLOCKQUOTE')
    expect(screen.getByText('No injury.')).toBeInTheDocument()
    expect(screen.getByText('resolved Tue 22 Sep 11:02')).toBeInTheDocument()
  })

  it('keeps the completeness warning visible and reveals every missing record on demand', async () => {
    render(<ReportCompleteness report={{ dataComplete: false, missingItems: ['Visit 12 not closed', 'Visit 13 not closed'] }} />)
    expect(screen.getByText('Some care records are missing')).toBeVisible()
    expect(screen.getByText('Visit 12 not closed')).not.toBeVisible()
    await userEvent.setup().click(screen.getByText('View 2 missing records'))
    expect(screen.getByText('Visit 12 not closed')).toBeVisible()
    expect(screen.getByText('Visit 13 not closed')).toBeVisible()
  })

  it('retains legacy text exactly and renders no invented charts or metrics', () => {
    const body = 'No records.\n\n  Original spacing remains.\n'
    render(<FamilyReportContent sections={[{ title: 'Vital signs', body }]} />)
    expect(screen.getByRole('region', { name: 'Vital signs' }).querySelector('p')?.textContent).toBe(body)
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
    expect(screen.queryByRole('definition')).not.toBeInTheDocument()
  })

  it('handles a single constant point and empty series without invalid SVG coordinates', () => {
    render(<FamilyReportContent sections={[{ title: 'Vital signs', body: 'Pulse 72 bpm', series: [
      { key: 'pulse', label: 'Pulse', unit: null, points: [{ at: '2026-09-14', low: 72, high: 72, flagged: false }] },
      { key: 'temperature', label: 'Temperature', unit: '°C', points: [] },
    ] }]} />)
    const chart = screen.getByRole('img')
    expect(chart.outerHTML).not.toMatch(/NaN|Infinity/)
    expect(chart.querySelector('circle')).toHaveAttribute('cx', '120')
    expect(screen.queryByText('Temperature')).not.toBeInTheDocument()
  })

  it('labels follow-ups independently from corrections and defaults older notes to correction', () => {
    render(<ReportCorrections amendments={[
      { id: 1, note: 'Original wording corrected.', createdAt: '2026-09-21T09:00:00+08:00' },
      { id: 2, kind: 'FOLLOW_UP', note: 'Grab bar fitted.', createdAt: '2026-09-22T09:00:00+08:00' },
    ]} />)
    expect(screen.getByText('Correction')).toBeInTheDocument()
    expect(screen.getByText('Follow-up')).toBeInTheDocument()
    expect(screen.getByText('Grab bar fitted.')).toBeInTheDocument()
  })
})
