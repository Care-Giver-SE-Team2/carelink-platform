import { useId } from 'react'
import type { ReportFigure, ReportSection, ReportSeries } from '../../../features/reports/types'
import { figureText, reportDay, reportNumber, sparkline } from '../../../features/reports/presentation'
import styles from './FamilyReportContent.module.css'

function Figures({ figures, overview = false, services = false }: { figures: ReportFigure[]; overview?: boolean; services?: boolean }) {
  const fulfilment = overview && figures.some((figure) => figure.key === 'visits')
    ? figures.find((figure) => figure.key === 'fulfilment') : undefined
  return <dl className={styles.figures} data-overview={overview} data-services={services}>
    {figures.filter((figure) => figure !== fulfilment).map((figure) => <div key={figure.key}>
      <dt>{figure.label}</dt>
      <dd>{figureText(figure)}</dd>
      {figure.key === 'visits' && fulfilment && <dd className={styles.figureNote}><span>{figureText(fulfilment)}</span> as planned</dd>}
      {services && figure.outOf !== null && figure.outOf > 0 && <dd className={styles.progress}>
        <progress aria-label={figure.label} value={figure.value} max={figure.outOf} />
      </dd>}
    </div>)}
  </dl>
}

/** Keep every saved word. Delimiters only separate presentation; unfamiliar lines stay intact. */
function RecordLines({ body, sectionKey }: { body: string; sectionKey: string }) {
  return <div className={styles.records}>
    {body.split('\n').filter((line) => line.trim()).map((line, index) => {
      const parts = line.split(' · ')
      const visit = sectionKey === 'service-completion' && parts.length === 5
        && /^(scheduled|arrived|caregiver arrived|in progress|completed|awaiting the elder's confirmation|verified|auto.closed|closed without the elder's confirmation|cancelled|cancelled at the family's request|exception|ended in an exception)$/.test(parts[3])
      if (visit) return <div className={styles.visit} key={index}>
        <div><span className={styles.date}>{parts[0]}</span><strong>{parts[1]}</strong>
          <p>{parts[2]} · {parts[4]}</p></div>
        <span className={styles.status} data-status={parts[3]}>{parts[3]}</span>
      </div>
      if (sectionKey === 'observations' && parts.length === 2 && parts[1].includes(': ')) {
        const separator = parts[1].indexOf(': ')
        return <div className={styles.note} key={index}>
          <p className={styles.date}>{parts[0]} · {parts[1].slice(0, separator)}</p>
          <blockquote>{parts[1].slice(separator + 2)}</blockquote>
        </div>
      }
      if (sectionKey === 'incidents' && parts.length >= 3
        && /^(resolved |still being followed up)/.test(parts.at(-1)!)) return <div className={styles.visit} key={index}>
        <div><span className={styles.date}>{parts[0]}</span><strong>{parts[1]}</strong>
          <p>{parts.slice(2, -1).join(' · ')}</p></div>
        <span className={styles.status} data-status={parts.at(-1)!.startsWith('resolved ') ? 'resolved' : 'follow-up'}>{parts.at(-1)}</span>
      </div>
      return <p className={styles.record} key={index} data-nested={line.startsWith('  ')}>
        {parts.length > 1 ? <><span className={styles.date}>{parts[0]}</span><span>{parts.slice(1).join(' · ')}</span></> : line}
      </p>
    })}
  </div>
}

function VitalTrend({ series }: { series: ReportSeries }) {
  const shape = sparkline(series.points, 240, 64, 8)
  if (!shape) return null
  const range = shape.min === shape.max ? reportNumber(shape.min) : `${reportNumber(shape.min)}–${reportNumber(shape.max)}`
  const flagged = series.points.filter((point) => point.flagged).length
  const caption = `${series.label}: daily range ${range}${series.unit ? ` ${series.unit}` : ''}; ${series.points.length} recorded ${series.points.length === 1 ? 'day' : 'days'}; ${flagged} flagged ${flagged === 1 ? 'day' : 'days'}`
  return <div className={styles.vital}>
    <div className={styles.vitalHeading}><h3>{series.label}</h3><p>{range} <span>{series.unit}</span></p></div>
    <svg viewBox="0 0 240 64" role="img" aria-label={caption}>
      <title>{caption}</title>
      <path d={shape.line} className={styles.trend} />
      {shape.points.map((point, index) => <g key={index} className={styles.point} data-flagged={point.flagged}>
        <title>{`${reportDay(series.points[index].at)}: ${series.points[index].low}–${series.points[index].high} ${series.unit ?? ''}${point.flagged ? ' · flagged at recording' : ''}`}</title>
        <line x1={point.x} x2={point.x} y1={point.low} y2={point.high} />
        <circle cx={point.x} cy={point.mid} r="3" />
      </g>)}
    </svg>
    <div className={styles.dates}><span>{reportDay(series.points[0].at)}</span>
      {series.points.length > 1 && <span>{reportDay(series.points.at(-1)!.at)}</span>}</div>
    {flagged > 0 && <p className={styles.flagged}>{flagged} {flagged === 1 ? 'day' : 'days'} with readings flagged at recording</p>}
    <details className={styles.readings}><summary>Daily ranges for {series.label}</summary>
      <ul>{series.points.map((point, index) => <li key={index}>
        <time dateTime={point.at}>{reportDay(point.at)}</time>: {reportNumber(point.low)}{point.low !== point.high && `–${reportNumber(point.high)}`} {series.unit}
        {point.flagged && ' · flagged at recording'}
      </li>)}</ul>
    </details>
  </div>
}

/** Shared by FM04 detail and weekly reading; all values come from the same authorized saved report. */
export function FamilyReportContent({ sections }: { sections: ReportSection[] }) {
  const id = useId()
  const overview = sections.find((section) => section.key === 'overview' || section.title === 'Overview')
  const readingOrder = ['Overview', 'Vital signs', 'Services', 'Service completion', 'Observations', 'Incidents', 'Ratings and spot checks']
  const ordered = overview ? [...sections].sort((a, b) => readingOrder.indexOf(a.title) - readingOrder.indexOf(b.title)) : sections
  return <div className={styles.content}>
    {!!overview?.figures?.length && <Figures figures={overview.figures} overview />}
    {sections.length === 0 && <p className={styles.section}>No report sections were recorded.</p>}
    {ordered.filter((section) => section !== overview).map((section, index) => {
      const key = section.key ?? section.title.toLowerCase().replaceAll(' ', '-')
      const title = { 'service-completion': 'Visits', observations: 'Caregiver notes' }[key] ?? section.title
      const hasCharts = section.series?.some((series) => series.points.length > 0)
      return <section className={styles.section} key={index} aria-labelledby={`${id}-${index}`}>
        <div className={styles.sectionHeading}><h2 id={`${id}-${index}`}>{title}</h2>
          {!!section.series?.length && <span>Daily ranges</span>}</div>
        {key !== 'overview' && !!section.figures?.length && <Figures figures={section.figures} services={key === 'services'} />}
        {!!section.series?.length && <div className={styles.vitals}>
          {section.series.map((series) => <VitalTrend key={series.key} series={series} />)}
        </div>}
        {!(key === 'vital-signs' && hasCharts) && (section.body.includes(' · ')
          ? <RecordLines body={section.body} sectionKey={key} />
          : <p className={styles.legacyBody}>{section.body}</p>)}
      </section>
    })}
  </div>
}
