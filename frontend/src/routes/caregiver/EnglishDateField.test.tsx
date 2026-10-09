import { fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { useState } from 'react'
import EnglishDateField from './EnglishDateField'
import { isCalendarDate } from './calendarDate'

function Field({ initial = '2026-12-31' }: { initial?: string }) {
  const [value, setValue] = useState(initial)
  return <EnglishDateField label="Start date" value={value} onChange={setValue} />
}
const originalLanguage = document.documentElement.lang
afterEach(() => { document.documentElement.lang = originalLanguage })

describe('fixed-English leave date field', () => {
  it('keeps input, month, weekday and actions English even in a Chinese document', () => {
    document.documentElement.lang = 'zh-CN'
    render(<Field initial="" />)
    expect(screen.getByLabelText('Start date')).toHaveAttribute('type', 'text')
    expect(screen.getByLabelText('Start date')).toHaveAttribute('placeholder', 'YYYY-MM-DD')
    fireEvent.change(screen.getByLabelText('Start date'), { target: { value: '2026-12-31' } })
    fireEvent.click(screen.getByRole('button', { name: 'Choose start date' }))
    const calendar = screen.getByRole('group', { name: 'Start date calendar' })
    expect(within(calendar).getByText('December 2026')).toBeInTheDocument()
    expect(within(calendar).getByText('Mon')).toBeInTheDocument()
    expect(within(calendar).getByRole('button', { name: 'Today' })).toBeInTheDocument()
    expect(within(calendar).getByRole('button', { name: 'Thursday, 31 December 2026' })).toHaveAttribute('aria-pressed', 'true')
  })
  it('navigates across years and selects an ISO date without submitting a form', () => {
    render(<Field />)
    fireEvent.click(screen.getByRole('button', { name: 'Choose start date' }))
    fireEvent.click(screen.getByRole('button', { name: 'Start date: next month' }))
    expect(screen.getByText('January 2027')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Friday, 1 January 2027' }))
    expect(screen.getByLabelText('Start date')).toHaveValue('2027-01-01')
    expect(screen.queryByRole('group')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Start date')).toHaveFocus()
  })
  it('shows leap day and supports Escape and Clear', () => {
    render(<Field initial="2028-02-29" />)
    const toggle = screen.getByRole('button', { name: 'Choose start date' })
    fireEvent.click(toggle)
    expect(screen.getByRole('button', { name: 'Tuesday, 29 February 2028' })).toBeInTheDocument()
    fireEvent.keyDown(screen.getByRole('group'), { key: 'Escape' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    fireEvent.click(toggle)
    fireEvent.click(screen.getByRole('button', { name: 'Clear' }))
    expect(screen.getByLabelText('Start date')).toHaveValue('')
  })
  it.each(['', '2026-02-29', '2026-04-31', '2026-13-01', '2026-1-01', 'not-a-date', '0001-01-01'])('rejects non-calendar input %s', value => {
    expect(isCalendarDate(value)).toBe(false)
  })
  it.each(['2028-02-29', '2026-12-31', '1000-01-01', '9999-12-31'])('accepts an actual ISO date %s', value => {
    expect(isCalendarDate(value)).toBe(true)
  })
})
