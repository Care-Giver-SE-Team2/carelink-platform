import { useState } from 'react'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import type { PlanTreeItem } from '../../../shared/components/ui'
import { DaySchedule } from './DaySchedule'
import { PlanTreeEditor } from './PlanTreeEditor'
import { VersionHistoryList } from './VersionHistoryList'
import { emptyDaySchedule } from './weekdays'
import type { DayScheduleValue } from './weekdays'

afterEach(cleanup)

describe('DaySchedule', () => {
  function Harness({ onChange }: { onChange: (v: DayScheduleValue) => void }) {
    const [value, setValue] = useState(emptyDaySchedule())
    return (
      <DaySchedule
        value={value}
        onChange={(next) => {
          setValue(next)
          onChange(next)
        }}
      />
    )
  }

  it('disables an inactive day and enables its inputs once toggled on', async () => {
    const onChange = vi.fn()
    render(<Harness onChange={onChange} />)
    expect(screen.getByLabelText('Monday start time')).toBeDisabled()
    expect(screen.getByLabelText('Monday minutes')).toBeDisabled()

    await userEvent.click(screen.getByRole('button', { name: 'Monday' }))

    expect(screen.getByRole('button', { name: 'Monday' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByLabelText('Monday start time')).toBeEnabled()
  })

  it('sets each day independently', async () => {
    const onChange = vi.fn()
    render(<Harness onChange={onChange} />)
    await userEvent.click(screen.getByRole('button', { name: 'Tuesday' }))
    await userEvent.selectOptions(screen.getByLabelText('Tuesday minutes'), '1 h 30 min')
    const last = onChange.mock.lastCall![0] as DayScheduleValue
    expect(last.tue).toEqual({ active: true, startTime: '8:00 AM', minutes: 90 })
    expect(last.mon.active).toBe(false)
  })

  it('picks a start time from the half hours', async () => {
    const onChange = vi.fn()
    render(<Harness onChange={onChange} />)
    await userEvent.click(screen.getByRole('button', { name: 'Monday' }))

    await userEvent.selectOptions(screen.getByLabelText('Monday start time'), '9:30 AM')

    expect((onChange.mock.lastCall![0] as DayScheduleValue).mon.startTime).toBe('9:30 AM')
    expect(screen.queryByRole('option', { name: '9:15 AM' })).not.toBeInTheDocument()
  })

  it('shows a time saved off the half hour until a half hour is picked', () => {
    const value = { ...emptyDaySchedule(), mon: { active: true, startTime: '8:15 AM', minutes: 30 } }
    render(<DaySchedule value={value} onChange={vi.fn()} />)

    expect(screen.getByLabelText('Monday start time')).toHaveValue('8:15 AM')
    expect(screen.getByRole('option', { name: '8:15 AM – pick :00 or :30' })).toBeInTheDocument()
  })

  it('offers only the lengths that end by midnight, and flags one left too long by a later start', () => {
    const value = { ...emptyDaySchedule(), sat: { active: true, startTime: '10:00 PM', minutes: 240 } }
    render(<DaySchedule value={value} onChange={vi.fn()} />)

    const minutes = screen.getByLabelText('Saturday minutes') as HTMLSelectElement
    expect(minutes).toHaveValue('240')
    expect(Array.from(minutes.options).map((o) => o.text)).toEqual([
      '4 h – runs past midnight', '30 min', '1 h', '1 h 30 min', '2 h',
    ])
  })

  it('offers 30-minute blocks up to 4 hours, and shows a length saved outside them until one is picked', () => {
    const value = { ...emptyDaySchedule(), mon: { active: true, startTime: '8:00 AM', minutes: 45 } }
    render(<DaySchedule value={value} onChange={vi.fn()} />)

    const minutes = screen.getByLabelText('Monday minutes')
    expect(minutes).toHaveValue('45')
    expect(screen.getByRole('option', { name: '45 min – pick 30 min steps' })).toBeInTheDocument()
    expect(Array.from((minutes as HTMLSelectElement).options).slice(1).map((o) => o.text)).toEqual([
      '30 min', '1 h', '1 h 30 min', '2 h', '2 h 30 min', '3 h', '3 h 30 min', '4 h',
    ])
  })
})

describe('PlanTreeEditor', () => {
  const items: PlanTreeItem[] = [
    {
      kind: 'subplan',
      id: 's1',
      name: 'Personal care',
      weekly: '0.50 h',
      tasks: [
        {
          kind: 'task',
          id: 't1',
          label: 'Grooming',
          schedule: ['Mon 8:30–8:45 AM · 15 m', 'Fri 9:00–9:15 AM · 15 m'],
          weekly: '0.50 h',
        },
      ],
    },
  ]

  function renderEditor(overrides: Partial<Parameters<typeof PlanTreeEditor>[0]> = {}) {
    const props = {
      items,
      collapsedIds: new Set<string>(),
      onToggle: vi.fn(),
      total: '0.50 h',
      onEditTask: vi.fn(),
      onDeleteTask: vi.fn(),
      onDeleteSubPlan: vi.fn(),
      editingTask: null,
      onSaveTask: vi.fn(),
      onCancelEdit: vi.fn(),
      adding: false,
      activityOptions: [{ group: 'Personal care', items: [{ value: 'Grooming', label: 'Grooming' }] }],
      onAdd: vi.fn(),
      onCancelAdd: vi.fn(),
      ...overrides,
    }
    render(<PlanTreeEditor {...props} />)
    return props
  }

  it('gives a sub-plan delete only, a task edit and delete, without toggling the row', async () => {
    const props = renderEditor()
    await userEvent.click(screen.getByRole('button', { name: 'Edit task' }))
    await userEvent.click(screen.getByRole('button', { name: 'Remove task from sub-plan' }))
    await userEvent.click(screen.getByRole('button', { name: 'Delete sub-plan Personal care and its tasks' }))
    expect(props.onEditTask).toHaveBeenCalledWith('t1')
    expect(props.onDeleteTask).toHaveBeenCalledWith('t1')
    expect(props.onDeleteSubPlan).toHaveBeenCalledWith('s1')
    expect(props.onToggle).not.toHaveBeenCalled()
    expect(screen.getAllByRole('button', { name: /Delete sub-plan/ })).toHaveLength(1)
  })

  it('edits a task in place, pre-filled, and saves the new name and schedule', async () => {
    const schedule = emptyDaySchedule()
    schedule.mon = { active: true, startTime: '8:00 AM', minutes: 30 }
    const props = renderEditor({ editingTask: { id: 't1', name: 'Grooming', schedule } })

    expect(screen.queryByText('Mon 8:30–8:45 AM · 15 m')).not.toBeInTheDocument()
    const name = screen.getByLabelText('Name')
    expect(name).toHaveValue('Grooming')
    await userEvent.clear(name)
    await userEvent.type(name, 'Hair and nails')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(props.onSaveTask).toHaveBeenCalledWith(
      't1',
      'Hair and nails',
      expect.objectContaining({ mon: expect.objectContaining({ active: true, minutes: 30 }) }),
    )
  })

  it('adds a sub-plan once an activity and a day are chosen', async () => {
    const props = renderEditor({ adding: true })
    const submit = screen.getByRole('button', { name: /^Add sub-plan \(/ })
    expect(submit).toBeDisabled()

    await userEvent.selectOptions(screen.getByLabelText('Step 1 · select sub-plan'), 'Grooming')
    await userEvent.click(screen.getByRole('button', { name: 'Friday' }))
    expect(submit).toHaveTextContent('Add sub-plan (1 activity)')
    await userEvent.click(submit)

    expect(props.onAdd).toHaveBeenCalledWith(
      'Grooming',
      expect.objectContaining({ fri: expect.objectContaining({ active: true }) }),
    )
  })
})

describe('VersionHistoryList', () => {
  it('badges current versions and links archived ones', async () => {
    const onOpen = vi.fn()
    render(
      <VersionHistoryList
        onOpen={onOpen}
        versions={[
          { n: 5, date: 'editing', status: 'draft' },
          { n: 4, date: '26 Aug', status: 'published' },
          { n: 3, date: '02 Jul', status: 'archived' },
        ]}
      />,
    )
    expect(screen.getByText('DRAFT')).toBeInTheDocument()
    expect(screen.getByText('PUBLISHED')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /v3 · 02 Jul/ }))
    expect(onOpen).toHaveBeenCalledWith(3)
  })
})
