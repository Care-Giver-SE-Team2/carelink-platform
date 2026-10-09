import { Button, Modal } from '../../../shared/components/ui'
import styles from './CertificateScan.module.css'

/** The scan of one uploaded certificate, as the server describes it. */
export type Scan = {
  fileName: string
  /** Null when no file arrived; the thumbnail then can't be opened. */
  url: string | null
  /** The file's media type: an image, or application/pdf. */
  contentType: string | null
}

function isPdf(scan: Scan): boolean {
  return scan.contentType === 'application/pdf'
}

/**
 * A certificate's scan as a small preview — the image itself, or a PDF badge — that opens the
 * full-size viewer when clicked. Without a file it is a plain striped placeholder.
 */
export function ScanThumbnail({ scan, onOpen }: { scan: Scan; onOpen: () => void }) {
  if (!scan.url) {
    return (
      <div className={styles.placeholder} role="img" aria-label={`No scan for ${scan.fileName}`}>
        <span className={styles.fileName}>{scan.fileName}</span>
      </div>
    )
  }
  return (
    <button type="button" className={styles.thumbnail} onClick={onOpen} aria-label={`Open scan ${scan.fileName}`}>
      {isPdf(scan) ? (
        <span className={styles.pdf}>
          <span className={styles.pdfBadge}>PDF</span>
          <span className={styles.fileName}>{scan.fileName}</span>
        </span>
      ) : (
        <img className={styles.image} src={scan.url} alt="" />
      )}
      <span className={styles.open} aria-hidden="true">
        View
      </span>
    </button>
  )
}

/**
 * The full-size scan in a dialog: an image fitted to the window, or a PDF in the browser's own
 * viewer. "Open in new tab" is there for zooming and downloading.
 */
export function ScanViewer({ title, scan, onClose }: { title: string; scan: Scan & { url: string }; onClose: () => void }) {
  return (
    <Modal
      eyebrow="Certificate scan"
      title={title}
      meta={scan.fileName}
      width={880}
      onClose={onClose}
      footer={
        <>
          <a className={styles.newTab} href={scan.url} target="_blank" rel="noopener noreferrer">
            Open in new tab
          </a>
          <Button variant="secondary" onClick={onClose}>
            Close
          </Button>
        </>
      }
    >
      {isPdf(scan) ? (
        <iframe className={styles.pdfFrame} src={scan.url} title={`${title} — ${scan.fileName}`} />
      ) : (
        <img className={styles.full} src={scan.url} alt={`${title} — ${scan.fileName}`} />
      )}
    </Modal>
  )
}
