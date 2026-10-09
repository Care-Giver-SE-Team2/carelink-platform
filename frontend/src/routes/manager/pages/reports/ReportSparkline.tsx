import { reportNumber, sparkline } from '../../../../features/reports/presentation'
import type { ReportSeries } from '../../../../features/reports/types'
import styles from './Reports.module.css'

const WIDTH = 240
const HEIGHT = 64
const PAD = 8

/**
 * One metric of the vital signs section as a card: its name, its range over
 * the period and a small chart of how it moved.
 *
 * Points are spaced evenly in the order they were taken, which is what a
 * reader compares - this reading against the last - rather than placed on a
 * clock. A point that is a day's range (the family's version) is drawn as a
 * bar from its lowest to its highest reading; a flagged point is marked. The
 * chart says no more than the text beside it: the family's has a point per
 * day, never the readings.
 *
 * @param series The metric's points, oldest first
 * @param range The range as the report wrote it ("128–142 mmHg"), when it did;
 *   otherwise worked out from the points
 * @author Wang Ziyu
 */
export function ReportSparkline({ series, range }: { series: ReportSeries; range?: string }) {
  const shape = sparkline(series.points, WIDTH, HEIGHT, PAD)
  if (!shape) return null

  const unit = series.unit ? ' ' + series.unit : ''
  const span =
    shape.min === shape.max ? reportNumber(shape.min) : `${reportNumber(shape.min)}–${reportNumber(shape.max)}`
  const flagged = series.points.filter((point) => point.flagged).length
  const label =
    `${series.label}: ${series.points.length} ${series.points.length === 1 ? 'point' : 'points'}, ${span}${unit}` +
    (flagged ? `, ${flagged} out of range` : '')
  const shown = range ?? span + unit

  return (
    <figure className={styles.chart}>
      <figcaption className={styles.chartLabel}>{series.label}</figcaption>
      <p className={styles.chartRange}>{shown}</p>
      <svg role="img" aria-label={label} viewBox={`0 0 ${WIDTH} ${HEIGHT}`}>
        <line x1={0} x2={WIDTH} y1={PAD} y2={PAD} className={styles.chartGrid} />
        <line x1={0} x2={WIDTH} y1={HEIGHT - PAD} y2={HEIGHT - PAD} className={styles.chartGrid} />
        <path d={shape.line} className={styles.sparkLine} vectorEffect="non-scaling-stroke" />
        {shape.points.map((point, index) => (
          <g key={series.points[index].at + ':' + index}>
            {point.low !== point.high && (
              <line
                x1={point.x}
                x2={point.x}
                y1={point.low}
                y2={point.high}
                className={styles.sparkRange}
                vectorEffect="non-scaling-stroke"
              />
            )}
            <circle
              cx={point.x}
              cy={point.mid}
              r={point.flagged ? 4 : 2.5}
              className={point.flagged ? styles.sparkFlag : styles.sparkDot}
            />
          </g>
        ))}
      </svg>
      {flagged > 0 && (
        <p className={styles.chartFlag}>
          {flagged} {flagged === 1 ? 'point' : 'points'} out of range
        </p>
      )}
    </figure>
  )
}
