import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api'
import type { Page, PnrStatus, PnrSummary } from '../api'
import { Badge, Empty, ErrorNote, Field, Panel } from '../components/ui'
import { dateTime } from '../format'

export default function PnrList() {
  const navigate = useNavigate()
  const [locator, setLocator] = useState('')
  const [lastName, setLastName] = useState('')
  const [status, setStatus] = useState<PnrStatus | ''>('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<Page<PnrSummary>>()
  const [error, setError] = useState<string>()

  const load = useCallback((p: number) => {
    setError(undefined)
    api.searchPnrs({ status: status || undefined, lastName: lastName || undefined, page: p })
      .then(r => { setResult(r); setPage(p) })
      .catch(e => setError(String(e.message ?? e)))
  }, [status, lastName])

  useEffect(() => { load(0) }, [load])

  const open = (e: FormEvent) => {
    e.preventDefault()
    if (locator.trim()) navigate(`/pnrs/${locator.trim().toUpperCase()}`)
  }

  return (
    <>
      <Panel>
        <div className="query">
          <form className="query" onSubmit={open}>
            <Field label="Record locator">
              <input value={locator} placeholder="e.g. T5JNV6" maxLength={6} onChange={e => setLocator(e.target.value)} />
            </Field>
            <button className="primary">Retrieve</button>
          </form>
          <span className="divider" />
          <Field label="Last name"><input value={lastName} onChange={e => setLastName(e.target.value)} /></Field>
          <Field label="Status">
            <select value={status} onChange={e => setStatus(e.target.value as PnrStatus | '')}>
              <option value="">Any</option>
              <option>HELD</option><option>TICKETED</option><option>CANCELLED</option><option>EXPIRED</option>
            </select>
          </Field>
        </div>
        <ErrorNote error={error} />
      </Panel>
      <Panel title={result ? `${result.totalElements} PNR${result.totalElements === 1 ? '' : 's'}` : 'PNRs'}>
        {result && result.content.length === 0 && <Empty>No PNRs match. <Link to="/search">Make a booking</Link>.</Empty>}
        {result && result.content.length > 0 && (
          <table className="full clickable">
            <thead>
              <tr><th>Locator</th><th>Status</th><th>Lead passenger</th><th>Pax</th><th>Itinerary</th><th>Departs</th><th>Ticket by</th><th>Agent</th></tr>
            </thead>
            <tbody>
              {result.content.map(p => (
                <tr key={p.recordLocator} onClick={() => navigate(`/pnrs/${p.recordLocator}`)}>
                  <td><Link to={`/pnrs/${p.recordLocator}`}><b className="mono">{p.recordLocator}</b></Link></td>
                  <td><Badge>{p.status}</Badge></td>
                  <td>{p.leadPassenger}</td>
                  <td>{p.passengers}</td>
                  <td className="mono">{p.itinerary}</td>
                  <td>{p.firstDeparture ? dateTime(p.firstDeparture) : ''}</td>
                  <td>{p.ticketingDeadline ? dateTime(p.ticketingDeadline) : ''}</td>
                  <td className="muted">{p.createdBy}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        {result && result.totalPages > 1 && (
          <div className="pager">
            <button disabled={page === 0} onClick={() => load(page - 1)}>Previous</button>
            <span className="muted">Page {page + 1} of {result.totalPages}</span>
            <button disabled={page + 1 >= result.totalPages} onClick={() => load(page + 1)}>Next</button>
          </div>
        )}
      </Panel>
    </>
  )
}
