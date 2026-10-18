import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api'
import type { Pnr, QueueName, SegmentType } from '../api'
import { Badge, ErrorNote, Field, Panel } from '../components/ui'
import { countdown, dateTime, money, plusDays, shortDate, time } from '../format'

export default function PnrDetail() {
  const { locator = '' } = useParams()
  const [pnr, setPnr] = useState<Pnr>()
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const [now, setNow] = useState(Date.now())

  useEffect(() => {
    setPnr(undefined)
    api.pnr(locator).then(setPnr).catch(e => setError(e.message))
  }, [locator])

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])

  const act = async (action: () => Promise<Pnr | void>, confirmText?: string) => {
    if (confirmText && !window.confirm(confirmText)) return
    setBusy(true)
    setError(undefined)
    try {
      const updated = await action()
      setPnr(updated ?? await api.pnr(locator))
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  if (!pnr) {
    return <Panel title={locator}>{error ? <ErrorNote error={error} /> : <p className="muted">Retrieving…</p>}</Panel>
  }

  const held = pnr.status === 'HELD'
  const cancelSegment = (type: SegmentType, id: number) =>
    act(() => api.cancelSegment(pnr.recordLocator, type, id), 'Cancel this segment and release its inventory?')

  return (
    <div className="pnr">
      <div className="pnr-main">
        <Panel>
          <div className="pnr-head">
            <div>
              <span className="muted small">Record locator</span>
              <h1 className="mono">{pnr.recordLocator}</h1>
            </div>
            <Badge>{pnr.status}</Badge>
            {held && pnr.ticketingDeadline && (
              <span className={`deadline ${new Date(pnr.ticketingDeadline).getTime() - now < 5 * 60_000 ? 'soon' : ''}`}>
                Ticket by {dateTime(pnr.ticketingDeadline)} · {countdown(pnr.ticketingDeadline, now)}
              </span>
            )}
            <span className="muted small push">Created {dateTime(pnr.createdAt)} by {pnr.createdBy} · {pnr.pcc}</span>
          </div>
          <ErrorNote error={error} />
        </Panel>

        <Panel title="Passengers">
          <table className="full">
            <tbody>
              {pnr.passengers.map((p, i) => (
                <tr key={p.id}>
                  <td className="muted">{i + 1}.</td>
                  <td><b className="mono">{p.name}</b></td>
                  <td>{p.type}</td>
                  <td className="muted">{p.dateOfBirth ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Contact pnr={pnr} onSave={(email, phone) => act(() => api.updateContact(pnr.recordLocator, email, phone))} />
        </Panel>

        {pnr.air.length > 0 && (
          <Panel title="Flights">
            <table className="full">
              <thead><tr><th>Flight</th><th>Route</th><th>Date</th><th>Times</th><th>Class</th><th>Fare basis</th><th>Seats</th><th>Status</th><th /></tr></thead>
              <tbody>
                {pnr.air.map(s => (
                  <tr key={s.id} className={s.status === 'XX' ? 'struck' : ''}>
                    <td><b>{s.flight}</b></td>
                    <td className="mono">{s.origin}–{s.destination}</td>
                    <td>{shortDate(s.departure)}</td>
                    <td>{time(s.departure)} → {time(s.arrival)}{plusDays(s.departure, s.arrival)}</td>
                    <td>{s.bookingClass} · {s.cabin.toLowerCase().replace('_', ' ')}</td>
                    <td className="mono" title={s.rules.refundable ? `Refundable, penalty ${money(s.rules.cancelPenalty)}` : 'Non-refundable'}>{s.fareBasis}</td>
                    <td>{s.seats}</td>
                    <td><Badge>{s.status}</Badge></td>
                    <td className="num">{held && s.status === 'HK' && <button className="link danger" disabled={busy} onClick={() => cancelSegment('AIR', s.id)}>Cancel</button>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Panel>
        )}

        {pnr.hotels.length > 0 && (
          <Panel title="Hotels">
            <table className="full">
              <tbody>
                {pnr.hotels.map(h => (
                  <tr key={h.id} className={h.status === 'XX' ? 'struck' : ''}>
                    <td><b>{h.hotelName}</b><div className="muted small">{h.propertyCode} · {h.roomCode} · conf {h.confirmation}</div></td>
                    <td>{shortDate(h.checkIn)} to {shortDate(h.checkOut)}</td>
                    <td>{h.rooms} room(s)</td>
                    <td className="num">{money(h.total)}</td>
                    <td><Badge>{h.status}</Badge></td>
                    <td className="num">{held && h.status === 'HK' && <button className="link danger" disabled={busy} onClick={() => cancelSegment('HOTEL', h.id)}>Cancel</button>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Panel>
        )}

        {pnr.cars.length > 0 && (
          <Panel title="Cars">
            <table className="full">
              <tbody>
                {pnr.cars.map(c => (
                  <tr key={c.id} className={c.status === 'XX' ? 'struck' : ''}>
                    <td><b>{c.vendor} {c.sipp}</b><div className="muted small">{c.model} · conf {c.confirmation}</div></td>
                    <td>{c.location} · {shortDate(c.pickupDate)} to {shortDate(c.returnDate)}</td>
                    <td className="num">{money(c.total)}</td>
                    <td><Badge>{c.status}</Badge></td>
                    <td className="num">{held && c.status === 'HK' && <button className="link danger" disabled={busy} onClick={() => cancelSegment('CAR', c.id)}>Cancel</button>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Panel>
        )}

        {pnr.tickets.length > 0 && (
          <Panel title="Tickets">
            <table className="full">
              <thead><tr><th>Number</th><th>Passenger</th><th className="num">Fare</th><th className="num">Taxes</th><th className="num">Total</th><th>Paid by</th><th>Status</th><th className="num">Refunded</th></tr></thead>
              <tbody>
                {pnr.tickets.map(t => (
                  <tr key={t.number}>
                    <td className="mono">{t.number.slice(0, 3)}-{t.number.slice(3)}</td>
                    <td>{t.passenger}</td>
                    <td className="num">{money(t.fare)}</td>
                    <td className="num">{money(t.taxes)}</td>
                    <td className="num"><b>{money(t.total)}</b></td>
                    <td>{t.formOfPayment}</td>
                    <td><Badge>{t.status}</Badge></td>
                    <td className="num">{t.refundAmount != null ? money(t.refundAmount) : ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Panel>
        )}

        <Panel title="History">
          <ol className="history">
            {pnr.history.map((h, i) => (
              <li key={i}>
                <span className="muted small">{dateTime(h.at)}</span>
                <Badge tone="muted">{h.action}</Badge>
                <span>{h.detail}</span>
                <span className="muted small push">{h.actor}</span>
              </li>
            ))}
          </ol>
        </Panel>
      </div>

      <aside className="pnr-side">
        <Panel title="Price">
          <dl className="totals">
            {pnr.airPrice?.passengers.map(p => (
              <Row key={p.type} label={`${p.count} × ${p.type}`} value={money(p.totalPerPax * p.count)}
                   hint={`${money(p.baseFare)} fare + ${money(p.taxes)} taxes each`} />
            ))}
            {pnr.hotelTotal > 0 && <Row label="Hotels" value={money(pnr.hotelTotal)} />}
            {pnr.carTotal > 0 && <Row label="Cars" value={money(pnr.carTotal)} />}
            <dt className="grand">Total</dt><dd className="grand">{money(pnr.grandTotal, pnr.currency)}</dd>
          </dl>
        </Panel>
        {held && <TicketForm busy={busy} disabled={!pnr.air.some(s => s.status === 'HK')}
                             onTicket={(fop, last4) => act(() => api.ticket(pnr.recordLocator, fop, last4))} />}
        {(held || pnr.status === 'TICKETED') && (
          <Panel title="Cancel">
            <p className="muted small">
              {held ? 'Releases every held seat, room and car.'
                : 'Tickets issued in the last 24 hours are voided for a full refund. Older tickets refund according to their fare rules.'}
            </p>
            <button className="danger" disabled={busy}
                    onClick={() => act(() => api.cancel(pnr.recordLocator), `Cancel PNR ${pnr.recordLocator}?`)}>
              Cancel PNR
            </button>
          </Panel>
        )}
        <QueueForm busy={busy} onPlace={(queue, remark) => act(() => api.placeOnQueue(queue, pnr.recordLocator, remark))} />
        <Link to="/pnrs" className="muted small">← All PNRs</Link>
      </aside>
    </div>
  )
}

function Row({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return <><dt title={hint}>{label}</dt><dd>{value}</dd></>
}

function Contact({ pnr, onSave }: { pnr: Pnr; onSave: (email: string, phone: string) => void }) {
  const [editing, setEditing] = useState(false)
  const [email, setEmail] = useState(pnr.contactEmail ?? '')
  const [phone, setPhone] = useState(pnr.contactPhone ?? '')
  if (!editing) {
    return (
      <p className="contact muted">
        Contact: {pnr.contactEmail || 'no email'} · {pnr.contactPhone || 'no phone'}
        {' '}<button className="link" onClick={() => setEditing(true)}>Edit</button>
      </p>
    )
  }
  return (
    <form className="query" onSubmit={e => { e.preventDefault(); onSave(email, phone); setEditing(false) }}>
      <Field label="Email"><input type="email" value={email} onChange={e => setEmail(e.target.value)} /></Field>
      <Field label="Phone"><input value={phone} onChange={e => setPhone(e.target.value)} /></Field>
      <button className="primary">Save</button>
      <button type="button" className="link" onClick={() => setEditing(false)}>Cancel</button>
    </form>
  )
}

function TicketForm({ busy, disabled, onTicket }: { busy: boolean; disabled: boolean; onTicket: (fop: string, last4?: string) => void }) {
  const [fop, setFop] = useState('CARD')
  const [last4, setLast4] = useState('4242')
  const submit = (e: FormEvent) => {
    e.preventDefault()
    onTicket(fop, fop === 'CARD' ? last4 : undefined)
  }
  return (
    <Panel title="Ticket">
      <form className="stack" onSubmit={submit}>
        <Field label="Form of payment">
          <select value={fop} onChange={e => setFop(e.target.value)}>
            <option value="CARD">Card</option><option value="CASH">Cash</option><option value="INVOICE">Agency invoice</option>
          </select>
        </Field>
        {fop === 'CARD' && (
          <Field label="Card, last 4 digits">
            <input value={last4} pattern="\d{4}" maxLength={4} inputMode="numeric" onChange={e => setLast4(e.target.value)} />
          </Field>
        )}
        <button className="primary" disabled={busy || disabled}>Issue tickets</button>
        {disabled && <p className="muted small">There are no confirmed flights to ticket.</p>}
      </form>
    </Panel>
  )
}

function QueueForm({ busy, onPlace }: { busy: boolean; onPlace: (queue: QueueName, remark: string) => void }) {
  const [remark, setRemark] = useState('')
  const [done, setDone] = useState(false)
  return (
    <Panel title="Queue for follow-up">
      <form className="stack" onSubmit={e => { e.preventDefault(); onPlace('GENERAL', remark); setRemark(''); setDone(true) }}>
        <Field label="Note"><input value={remark} maxLength={300} placeholder="e.g. call about seat request" onChange={e => setRemark(e.target.value)} /></Field>
        <button disabled={busy}>Place on GENERAL queue</button>
        {done && <p className="muted small">Placed. <Link to="/queues">Open queues</Link></p>}
      </form>
    </Panel>
  )
}
