import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Legend, Pagination, ViewToggle, VisitBlock } from './index'
import { pageSlots } from './pageSlots'

afterEach(cleanup)

describe('ViewToggle', () => {
  it('presses the current view and reports a change', async () => {
    const onChange = vi.fn()
    render(
      <ViewToggle
        label="Roster view"
        options={[
          { value: 'day', label: 'DAY' },
          { value: 'week', label: 'WEEK' },
        ]}
        value="day"
        onChange={onChange}
      />,
    )

    expect(screen.getByRole('button', { name: 'DAY' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'WEEK' })).toHaveAttribute('aria-pressed', 'false')
    await userEvent.click(screen.getByRole('button', { name: 'WEEK' }))
    expect(onChange).toHaveBeenCalledWith('week')
  })
})

describe('VisitBlock', () => {
  it('reads on two lines in one hour and on one line across two or more', () => {
    const { container } = render(
      <div>
        <VisitBlock elderShort="Lim A.K." label="bathing" state="closed" startCol={2} span={1} />
        <VisitBlock elderShort="Goh S.L." label="mobility exercise" state="assigned" startCol={4} span={2} />
      </div>,
    )

    expect(screen.getByText('Goh S.L. · mobility exercise')).toBeInTheDocument()
    expect(container.querySelectorAll('br')).toHaveLength(1)
    const cells = container.querySelectorAll<HTMLElement>('[style]')
    expect(cells[1].style.gridColumn).toBe('4 / span 2')
  })

  it('counts hours in slots on a finer grid, and cuts a block under an hour to one line each', () => {
    const { container } = render(
      <div>
        <VisitBlock elderShort="Mohd Y." label="Medication reminder" state="assigned" startCol={6} span={1} slotsPerHour={4} title="09:00 · Mohd Yusof" />
        <VisitBlock elderShort="Goh S.L." label="Meal preparation" state="assigned" startCol={2} span={4} slotsPerHour={4} />
        <VisitBlock elderShort="Tan H.S." label="Personal care" state="assigned" startCol={10} span={8} slotsPerHour={4} />
      </div>,
    )

    const short = screen.getByTitle('09:00 · Mohd Yusof')
    expect(short.children).toHaveLength(2)
    expect(short).toHaveTextContent('Mohd Y.Medication reminder')
    expect(container.querySelectorAll('br')).toHaveLength(1)
    expect(screen.getByText('Tan H.S. · Personal care')).toBeInTheDocument()
  })
})

describe('Legend', () => {
  it('lists each label', () => {
    render(
      <Legend
        items={[
          { label: 'assigned', swatch: 'assigned' },
          { label: 'needs cover', swatch: 'needs_cover' },
        ]}
      />,
    )
    expect(screen.getByRole('list', { name: 'Legend' })).toHaveTextContent('assignedneeds cover')
  })
})

describe('Pagination', () => {
  it('shows first, last and three around the current page, with gaps', () => {
    expect(pageSlots(1, 17)).toEqual([1, 2, 3, 4, 5, 'gap', 17])
    expect(pageSlots(9, 17)).toEqual([1, 'gap', 8, 9, 10, 'gap', 17])
    expect(pageSlots(17, 17)).toEqual([1, 'gap', 13, 14, 15, 16, 17])
    expect(pageSlots(1, 2)).toEqual([1, 2])
    expect(pageSlots(4, 7)).toEqual([1, 2, 3, 4, 5, 6, 7])
  })

  it('keeps the same number of slots on every page', () => {
    for (const pageCount of [8, 9, 17]) {
      for (let page = 1; page <= pageCount; page++) {
        expect(pageSlots(page, pageCount)).toHaveLength(7)
        expect(pageSlots(page, pageCount)).toContain(page)
      }
    }
  })

  it('reads the range, marks the current page and disables Prev on page 1', async () => {
    const onPageChange = vi.fn()
    render(<Pagination page={1} pageSize={6} total={102} noun="caregivers" onPageChange={onPageChange} />)

    expect(screen.getByText('1–6 of 102 caregivers')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Page 1' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('button', { name: '‹ Prev' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Next ›' }))
    await userEvent.click(screen.getByRole('button', { name: 'Page 17' }))
    expect(onPageChange.mock.calls).toEqual([[2], [17]])
  })
})
