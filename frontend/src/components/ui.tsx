import type { ReactNode, SelectHTMLAttributes } from 'react'
import { useAirports } from '../state'

export function Panel({ title, actions, children }: { title?: ReactNode; actions?: ReactNode; children: ReactNode }) {
  return (
    <section className="panel">
      {(title || actions) && (
        <header className="panel-head">
          {title && <h2>{title}</h2>}
          {actions && <div className="actions">{actions}</div>}
        </header>
      )}
      {children}
    </section>
  )
}

const statusTone: Record<string, string> = {
  HELD: 'warn', TICKETED: 'ok', CANCELLED: 'muted', EXPIRED: 'bad',
  HK: 'ok', XX: 'muted', ISSUED: 'ok', VOIDED: 'muted', REFUNDED: 'warn',
  ADMIN: 'accent', SUPERVISOR: 'warn', AGENT: 'muted',
}

export function Badge({ children, tone }: { children: ReactNode; tone?: string }) {
  const resolved = tone ?? (typeof children === 'string' ? statusTone[children] : undefined) ?? 'muted'
  return <span className={`badge ${resolved}`}>{children}</span>
}

export function Field({ label, children, wide }: { label: ReactNode; children: ReactNode; wide?: boolean }) {
  return (
    <label className={`field ${wide ? 'wide' : ''}`}>
      <span>{label}</span>
      {children}
    </label>
  )
}

export function AirportSelect(props: SelectHTMLAttributes<HTMLSelectElement>) {
  const airports = useAirports()
  return (
    <select {...props}>
      {airports.map(a => <option key={a.code} value={a.code}>{a.code} · {a.city}</option>)}
    </select>
  )
}

export function ErrorNote({ error }: { error?: string | null }) {
  return error ? <p className="error" role="alert">{error}</p> : null
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="empty">{children}</p>
}

export function Stepper({ value, onChange, min = 0, max = 9 }: {
  value: number; onChange: (v: number) => void; min?: number; max?: number
}) {
  return (
    <div className="stepper">
      <button type="button" onClick={() => onChange(Math.max(min, value - 1))} aria-label="Fewer">−</button>
      <span>{value}</span>
      <button type="button" onClick={() => onChange(Math.min(max, value + 1))} aria-label="More">+</button>
    </div>
  )
}

/** Tiny line chart for the live dashboard. */
export function Sparkline({ values, height = 48 }: { values: number[]; height?: number }) {
  const width = 240
  if (values.length < 2) return <svg className="spark" viewBox={`0 0 ${width} ${height}`} />
  const max = Math.max(...values, 1)
  const step = width / (values.length - 1)
  const points = values.map((v, i) => `${(i * step).toFixed(1)},${(height - 2 - (v / max) * (height - 4)).toFixed(1)}`)
  return (
    <svg className="spark" viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" aria-hidden="true">
      <polyline points={`0,${height} ${points.join(' ')} ${width},${height}`} className="spark-fill" />
      <polyline points={points.join(' ')} className="spark-line" />
    </svg>
  )
}
