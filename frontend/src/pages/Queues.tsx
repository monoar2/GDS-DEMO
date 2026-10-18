import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api'
import type { QueueItem, QueueName } from '../api'
import { Empty, ErrorNote, Panel } from '../components/ui'
import { dateTime } from '../format'

const queues: { name: QueueName; hint: string }[] = [
  { name: 'TICKETING', hint: 'Held PNRs close to their ticketing deadline' },
  { name: 'EXPIRED', hint: 'Holds released because nobody ticketed in time' },
  { name: 'REFUNDS', hint: 'Cancelled tickets with a refund to process' },
  { name: 'GENERAL', hint: 'PNRs agents placed here by hand' },
]

export default function Queues() {
  const [active, setActive] = useState<QueueName>('TICKETING')
  const [counts, setCounts] = useState<Partial<Record<QueueName, number>>>({})
  const [items, setItems] = useState<QueueItem[]>()
  const [error, setError] = useState<string>()

  const load = useCallback(() => {
    setError(undefined)
    Promise.all([api.queueCounts(), api.queue(active)])
      .then(([c, list]) => { setCounts(c); setItems(list) })
      .catch(e => setError(String(e.message ?? e)))
  }, [active])

  useEffect(() => { load() }, [load])

  const work = (id: number) => api.workQueueItem(id).then(load).catch(e => setError(String(e.message ?? e)))

  return (
    <>
      <div className="tabs">
        {queues.map(q => (
          <button key={q.name} className={q.name === active ? 'active' : ''} onClick={() => setActive(q.name)}>
            {q.name.charAt(0) + q.name.slice(1).toLowerCase()}
            <span className="count">{counts[q.name] ?? 0}</span>
          </button>
        ))}
        <button className="link push" onClick={load}>Refresh</button>
      </div>
      <Panel title={`${active.charAt(0)}${active.slice(1).toLowerCase()} queue`}>
        <p className="muted small">{queues.find(q => q.name === active)?.hint}. Items are shared by everyone in your agency.</p>
        <ErrorNote error={error} />
        {items && items.length === 0 && <Empty>This queue is empty.</Empty>}
        {items && items.length > 0 && (
          <table className="full">
            <thead><tr><th>Locator</th><th>Remark</th><th>Queued</th><th /></tr></thead>
            <tbody>
              {items.map(i => (
                <tr key={i.id}>
                  <td><Link to={`/pnrs/${i.recordLocator}`}><b className="mono">{i.recordLocator}</b></Link></td>
                  <td>{i.remark}</td>
                  <td>{dateTime(i.createdAt)}</td>
                  <td className="num">
                    <Link to={`/pnrs/${i.recordLocator}`}>Open PNR</Link>
                    {' · '}
                    <button className="link" onClick={() => work(i.id)}>Mark worked</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </>
  )
}
