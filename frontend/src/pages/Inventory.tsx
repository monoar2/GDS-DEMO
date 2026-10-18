import { useState } from 'react'
import type { FormEvent } from 'react'
import { api } from '../api'
import type { CarInventory, FlightInventory, HotelInventory } from '../api'
import { AirportSelect, Empty, ErrorNote, Field, Panel } from '../components/ui'
import { addDays, cabinLabel, duration, money, plusDays, time, today } from '../format'

type Tab = 'flights' | 'hotels' | 'cars'

/** Raw seat, room and car counts straight from the inventory tables, without pricing. */
export default function Inventory() {
  const [tab, setTab] = useState<Tab>('flights')
  const [origin, setOrigin] = useState('JFK')
  const [destination, setDestination] = useState('LHR')
  const [date, setDate] = useState(addDays(today(), 1))
  const [flights, setFlights] = useState<FlightInventory[]>()
  const [hotels, setHotels] = useState<HotelInventory[]>()
  const [cars, setCars] = useState<CarInventory[]>()
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(undefined)
    try {
      if (tab === 'flights') setFlights(await api.flights(origin, destination, date))
      if (tab === 'hotels') setHotels(await api.hotels(destination, date))
      if (tab === 'cars') setCars(await api.cars(destination, date))
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="tabs">
        {(['flights', 'hotels', 'cars'] as Tab[]).map(t => (
          <button key={t} className={t === tab ? 'active' : ''} onClick={() => setTab(t)}>
            {t.charAt(0).toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>
      <Panel>
        <form className="query" onSubmit={submit}>
          {tab === 'flights' && (
            <Field label="From"><AirportSelect value={origin} onChange={e => setOrigin(e.target.value)} /></Field>
          )}
          <Field label={tab === 'flights' ? 'To' : 'City'}>
            <AirportSelect value={destination} onChange={e => setDestination(e.target.value)} />
          </Field>
          <Field label="Date"><input type="date" value={date} onChange={e => setDate(e.target.value)} /></Field>
          <button className="primary" disabled={busy}>{busy ? 'Loading…' : 'Show inventory'}</button>
        </form>
        <p className="muted small">
          This is the raw availability the shopping engine reads. Results are cached in Redis for 30 seconds and the
          cache entry is dropped as soon as a booking changes a count.
        </p>
        <ErrorNote error={error} />
      </Panel>

      {tab === 'flights' && flights && (
        <Panel title={`${flights.length} flights`}>
          {flights.length === 0 && <Empty>No direct flights on this route and date.</Empty>}
          {flights.map(f => (
            <div key={f.flightId} className="card">
              <header>
                <b>{f.flight}</b>
                <span>{f.origin} {time(f.departure)} → {f.destination} {time(f.arrival)}{plusDays(f.departure, f.arrival)}</span>
                <span className="muted">{duration(f.durationMinutes)} · {f.aircraft}</span>
              </header>
              <div className="classes">
                {f.classes.map(c => (
                  <span key={c.code} className={`rbd ${c.seatsAvailable === 0 ? 'closed' : ''}`}
                        title={`${cabinLabel[c.cabin]} · ${money(c.baseFare, c.currency)}`}>
                    <b>{c.code}</b>
                    <span>{c.seatsAvailable}/{c.seatsTotal}</span>
                    <span className="muted">{money(c.baseFare, c.currency)}</span>
                  </span>
                ))}
              </div>
            </div>
          ))}
        </Panel>
      )}

      {tab === 'hotels' && hotels && (
        <Panel title={`${hotels.length} hotels`}>
          {hotels.length === 0 && <Empty>No hotels in this city.</Empty>}
          {hotels.map(h => (
            <div key={h.hotelId} className="card">
              <header>
                <b>{h.name}</b>
                <span className="stars">{'★'.repeat(h.stars)}</span>
                <span className="muted">{h.chain}{h.propertyCode} · {h.address}</span>
              </header>
              <div className="classes">
                {h.rooms.map(r => (
                  <span key={r.roomTypeId} className={`rbd ${r.roomsAvailable === 0 ? 'closed' : ''}`} title={r.description}>
                    <b>{r.code}</b>
                    <span>{r.roomsAvailable}/{r.roomsTotal}</span>
                    <span className="muted">{money(r.nightlyRate, r.currency)}</span>
                  </span>
                ))}
              </div>
            </div>
          ))}
        </Panel>
      )}

      {tab === 'cars' && cars && (
        <Panel title={`${cars.length} car types`}>
          {cars.length === 0 && <Empty>No cars at this location.</Empty>}
          {cars.length > 0 && (
            <table className="full">
              <thead><tr><th>Vendor</th><th>SIPP</th><th>Example model</th><th>Available</th><th>Daily rate</th></tr></thead>
              <tbody>
                {cars.map(c => (
                  <tr key={`${c.vendor}${c.sipp}`}>
                    <td>{c.vendor}</td>
                    <td className="mono">{c.sipp}</td>
                    <td>{c.sampleModel}</td>
                    <td className="num">{c.unitsAvailable}/{c.unitsTotal}</td>
                    <td className="num">{money(c.dailyRate, c.currency)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Panel>
      )}
    </>
  )
}
