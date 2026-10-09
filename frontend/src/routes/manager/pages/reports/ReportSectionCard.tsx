import type { ReactNode } from 'react'

import {
  figureText,
  reportLine,
  reportNumber,
  sectionKey,
  sectionLines,
} from '../../../../features/reports/presentation'
import type { ReportLine } from '../../../../features/reports/presentation'
import type { ReportFigure, ReportSection } from '../../../../features/reports/types'
import { ReportSparkline } from './ReportSparkline'
import styles from './Reports.module.css'

type Line = { text: string; nested: boolean }

/** The overview's closing sentences, which state the numbers in words: "Visits: 2 of 3 carried out (66.67%)." */
const NUMBER_SENTENCE = /^(Visits|Vital signs|Incidents|Visit ratings): /

/**
 * One section of a filed report as a card: its numbers first, then what it
 * says, laid out for the kind of section it is.
 *
 * The text stays what the report filed. A line is split on the report's own
 * " · " into its time, its state and the rest, so visits and incidents read as
 * rows with a status beside them; a line that does not split is shown whole.
 * Sections filed before figures and series existed have neither and show their
 * text alone.
 *
 * @author Wang Ziyu
 */
export function ReportSectionCard({ section }: { section: ReportSection }) {
  const key = sectionKey(section)
  const lines = sectionLines(section.body)
  const figures = section.figures ?? []

  return (
    <section className={styles.card} aria-label={section.title}>
      <h2 className={styles.cardTitle}>{section.title}</h2>
      {content(key, section, lines, figures)}
    </section>
  )
}

function content(key: string, section: ReportSection, lines: Line[], figures: ReportFigure[]): ReactNode {
  switch (key) {
    case 'overview': {
      // The last sentences state the period's numbers, which the tiles already show; they stay as the text of record, quieter.
      const numbers = lines.filter((line) => NUMBER_SENTENCE.test(line.text))
      return (
        <>
          {figures.length > 0 && <Tiles figures={figures} />}
          <ul className={styles.facts}>
            {lines
              .filter((line) => !NUMBER_SENTENCE.test(line.text))
              .map((line, index) => (
                <li key={index}>{line.text}</li>
              ))}
          </ul>
          {numbers.length > 0 && <p className={styles.cardNote}>{numbers.map((line) => line.text).join(' ')}</p>}
        </>
      )
    }
    case 'services':
      return (
        <>
          {figures.length > 0 && <Meters figures={figures} />}
          <Rows lines={lines.slice(figures.length)} />
        </>
      )
    case 'service-completion': {
      const tally = lines.length > 0 && reportLine(lines[0]).time === null ? lines[0] : null
      return (
        <>
          {tally && <p className={styles.cardNote}>{tally.text}</p>}
          <Rows lines={tally ? lines.slice(1) : lines} />
        </>
      )
    }
    case 'vital-signs':
      return <Vitals section={section} lines={lines} />
    case 'observations':
      return <Quotes lines={lines} />
    case 'ratings-and-spot-checks':
      return (
        <>
          {figures.length > 0 && <RatingFigures figures={figures} />}
          <Rows lines={lines} />
        </>
      )
    default:
      return <Rows lines={lines} />
  }
}

/* --------------------------------------------------------------- figures --- */

/** The overview's numbers as tiles: visits with how far they got, readings and incidents flagged, the rating as stars. */
function Tiles({ figures }: { figures: ReportFigure[] }) {
  const fulfilment = figures.find((figure) => figure.key === 'fulfilment')
  return (
    <div className={styles.tiles}>
      {figures
        .filter((figure) => figure.key !== 'fulfilment')
        .map((figure) => {
          const alert = (figure.key === 'out-of-range' || figure.key === 'incidents') && figure.value > 0
          return (
            <div
              key={figure.key}
              className={styles.tile}
              data-alert={alert}
              role="group"
              aria-label={`${figure.label}: ${figureText(figure)}`}
            >
              <span className={styles.tileValue}>
                {reportNumber(figure.value)}
                {figure.outOf !== null && <small>/ {reportNumber(figure.outOf)}</small>}
              </span>
              <span className={styles.tileLabel}>{figure.label}</span>
              {figure.key === 'visits' && figure.outOf ? (
                <>
                  <Meter value={figure.value} outOf={figure.outOf} />
                  {fulfilment && <span className={styles.tileNote}>{figureText(fulfilment)} as planned</span>}
                </>
              ) : null}
              {figure.key === 'rating' && <Stars value={figure.value} />}
            </div>
          )
        })}
    </div>
  )
}

/** Carried out of planned, per kind of service. */
function Meters({ figures }: { figures: ReportFigure[] }) {
  return (
    <div className={styles.meters}>
      {figures.map((figure) => (
        <div key={figure.key} className={styles.meterRow} role="group" aria-label={`${figure.label}: ${figureText(figure)}`}>
          <div>
            <span>{figure.label}</span>
            <b>{figureText(figure)}</b>
          </div>
          {figure.outOf !== null && <Meter value={figure.value} outOf={figure.outOf} />}
        </div>
      ))}
    </div>
  )
}

function Meter({ value, outOf }: { value: number; outOf: number }) {
  const share = outOf > 0 ? Math.min(100, (value / outOf) * 100) : 0
  return (
    <span className={styles.meter} aria-hidden="true">
      <span style={{ width: `${share}%` }} />
    </span>
  )
}

/** The rating as stars with its number, then the counts beside it. */
function RatingFigures({ figures }: { figures: ReportFigure[] }) {
  const rating = figures.find((figure) => figure.key === 'rating')
  const counts = figures.filter((figure) => figure.key !== 'rating')
  return (
    <div className={styles.ratingHead}>
      {rating && (
        <span className={styles.ratingValue} role="group" aria-label={`${rating.label}: ${figureText(rating)}`}>
          <b>{reportNumber(rating.value)}</b>
          <small>/ {reportNumber(rating.outOf ?? 5)}</small>
          <Stars value={rating.value} />
        </span>
      )}
      <dl className={styles.chips}>
        {counts.map((figure) => (
          <div key={figure.key}>
            <dt>{figure.label}</dt>
            <dd>{figureText(figure)}</dd>
          </div>
        ))}
      </dl>
    </div>
  )
}

const STAR = 'M10 1.5l2.6 5.4 5.9.8-4.3 4.1 1 5.9L10 15l-5.2 2.7 1-5.9L1.5 7.7l5.9-.8z'
const HALF = 'M10 1.5l-2.6 5.4-5.9.8 4.3 4.1-1 5.9L10 15z'

function Stars({ value }: { value: number }) {
  return (
    <span className={styles.stars} aria-hidden="true">
      {[1, 2, 3, 4, 5].map((place) => {
        const fill = value >= place ? 'full' : value >= place - 0.5 ? 'half' : 'empty'
        return (
          <svg key={place} viewBox="0 0 20 20" className={styles.star} data-fill={fill}>
            <path d={STAR} />
            {fill === 'half' && <path d={HALF} className={styles.starHalf} />}
          </svg>
        )
      })}
    </span>
  )
}

/* ----------------------------------------------------------------- lines --- */

/** The line without its time: what a timeline step says happened. */
function withoutTime(line: ReportLine): string {
  return line.time === null ? line.text : line.text.split(' · ').filter((part) => part !== line.time).join(' · ')
}

/**
 * Lines as rows: when, what, and how it stands. A line indented under another
 * is a step of its timeline and is listed under it.
 */
function Rows({ lines }: { lines: Line[] }) {
  const groups: { head: ReportLine; steps: ReportLine[] }[] = []
  for (const line of lines.map(reportLine)) {
    const last = groups[groups.length - 1]
    if (line.nested && last) last.steps.push(line)
    else groups.push({ head: line, steps: [] })
  }
  if (groups.length === 0) return null

  return (
    <ul className={styles.rows}>
      {groups.map(({ head, steps }, index) =>
        head.time === null && head.status === null ? (
          <li key={index} className={styles.plain}>
            {head.text}
            {steps.length > 0 && <Steps steps={steps} />}
          </li>
        ) : (
          <li key={index} className={styles.row}>
            <span className={styles.rowTime}>{head.time}</span>
            <span className={styles.rowMain}>
              {head.parts[0]}
              {head.parts.length > 1 && <small>{head.parts.slice(1).join(' · ')}</small>}
            </span>
            {head.status ? (
              <span className={styles.pill} data-tone={head.status.tone}>
                {head.status.text}
              </span>
            ) : (
              <span />
            )}
            {steps.length > 0 && <Steps steps={steps} />}
          </li>
        ),
      )}
    </ul>
  )
}

function Steps({ steps }: { steps: ReportLine[] }) {
  return (
    <ol className={styles.steps}>
      {steps.map((step, index) => (
        <li key={index}>
          {step.time && <span className={styles.stepTime}>{step.time}</span>}
          <span>{withoutTime(step)}</span>
        </li>
      ))}
    </ol>
  )
}

/** The caregivers' notes as quotes, with who wrote them and when; a sentence about them stays a sentence. */
function Quotes({ lines }: { lines: Line[] }) {
  return (
    <div className={styles.quotes}>
      {lines.map((line, index) => {
        const at = line.text.indexOf(': ')
        return at < 0 ? (
          <p key={index} className={styles.cardNote}>
            {line.text}
          </p>
        ) : (
          <blockquote key={index} className={styles.quote}>
            <cite>{line.text.slice(0, at)}</cite>
            <p>{line.text.slice(at + 2)}</p>
          </blockquote>
        )
      })}
    </div>
  )
}

/**
 * The vital signs: a card per metric with its range and chart, then every
 * reading for the versions that list them. A report filed before series
 * existed shows its text alone.
 */
function Vitals({ section, lines }: { section: ReportSection; lines: Line[] }) {
  const series = section.series ?? []
  if (series.length === 0) return <Rows lines={lines} />

  const readings = lines.map(reportLine).filter((line) => line.time !== null)
  const rangeOf = (label: string) => {
    const line = lines.find((candidate) => candidate.text.startsWith(label + ' '))
    return line && readings.length === 0 ? line.text.slice(label.length + 1) : undefined
  }

  return (
    <>
      <div className={styles.charts}>
        {series.map((metric) => (
          <ReportSparkline key={metric.key} series={metric} range={rangeOf(metric.label)} />
        ))}
      </div>
      {readings.length > 0 && (
        <details className={styles.readings}>
          <summary>Every reading ({readings.length})</summary>
          <ol>
            {readings.map((reading, index) => (
              <li key={index} data-flagged={reading.status?.tone === 'bad'}>
                {reading.text}
              </li>
            ))}
          </ol>
        </details>
      )}
    </>
  )
}
