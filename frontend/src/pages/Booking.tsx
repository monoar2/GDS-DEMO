import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api'
import type { PassengerInput, PassengerType } from '../api'
import { useBasket } from '../state'
import { Empty, ErrorNote, Field, Panel, Stepper } from '../components/ui'
import { money, plusDays, shortDate, time } from '../format'

const typeLabel: Record<PassengerType, string> = { ADT: 'Adult', CHD: 'Child', INF: 'Infant on lap' }

function partyList(adults: number, children: number, infants: number): PassengerType[] {
  return [
    ...Array<PassengerType>(adults).fill('ADT'),
    ...Array<PassengerType>(children).fill('CHD'),
    ...Array<PassengerType>(infants).fill('INF'),
  ]
}

export default function Booking() {
  const { basket, remove, clear, count } = useBasket()
  const navigate = useNavigate()
  const hasFlights = basket.flights.length > 0
  const [extraAdults, setExtraAdults] = useState(1) // party size when there are no flights to take it from
  const types = hasFlights
    ? partyList(basket.party.adults, basket.party.children, basket.party.infants)
    : partyList(extraAdults, 0, 0)

  const [passengers, setPassengers] = useState<PassengerInput[]>([])
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  // keep one form row per traveller in the party, holding on to names already typed
  const signature = types.join(',')
  useEffect(() => {
    setPassengers(current => types.map((type, i) => ({
      type, firstName: current[i]?.firstName ?? '', lastName: current[i]?.lastName ?? '', dateOfBirth: current[i]?.dateOfBirth,
    })))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature])

  const setPassenger = (i: number, patch: Partial<PassengerInput>) =>
    setPassengers(list => list.map((p, j) => (j === i ? { ...p, ...patch } : p)))

  const estimate = basket.flights.reduce((sum, f) => sum + f.price.grandTotal, 0)
    + basket.hotels.reduce((sum, h) => sum + h.room.price.total, 0)
    + basket.cars.reduce((sum, c) => sum + c.price.total, 0)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(undefined)
    try {
      const pnr = await api.createPnr({
        passengers: passengers.map(p => ({ ...p, dateOfBirth: p.dateOfBirth || undefined })),
        contactEmail: email || undefined,
        contactPhone: phone || undefined,
        segments: {
          air: basket.flights.flatMap(f => f.segments.map(s => ({ flightId: s.flightId, bookingClass: s.bookingClass }))),
          hotels: basket.hotels.map(h => ({ roomTypeId: h.room.roomTypeId, checkIn: h.checkIn, checkOut: h.checkOut, rooms: h.rooms })),
          cars: basket.cars.map(c => ({ vendor: c.vendor, location: c.location, sipp: c.sipp, pickupDate: c.pickupDate, days: c.price.nights })),
        },
      })
      clear()
      navigate(`/pnrs/${pnr.recordLocator}`)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    } finally {
      setBusy(false)
    }
  }

  if (count === 0) {
    return (
      <Panel title="Booking">
        <Empty>Nothing picked yet. <Link to="/search">Search flights, hotels or cars</Link> and add what the traveller wants.</Empty>
      </Panel>
    )
  }

  return (
    <form className="booking" onSubmit={submit}>
      <div className="booking-main">
        <Panel title="Itinerary" actions={<button type="button" className="link" onClick={clear}>Clear all</button>}>
          {basket.flights.map((offer, i) => (
            <div key={`f${i}`} className="basket-row">
              <div>
                {offer.segments.map(s => (
                  <div key={s.flightId}>
                    <b>{s.flight}</b> {s.origin} {time(s.departure)} → {s.destination} {time(s.arrival)}{plusDays(s.departure, s.arrival)}
                    <span className="muted"> · {shortDate(s.departure)} · class {s.bookingClass} ({s.rules.fareFamily})</span>
                  </div>
                ))}
              </div>
              <span className="num">{money(offer.price.grandTotal)}</span>
              <button type="button" className="link" onClick={() => remove('flights', i)}>Remove</button>
            </div>
          ))}
          {basket.hotels.map((h, i) => (
            <div key={`h${i}`} className="basket-row">
              <div>
                <b>{h.hotel.name}</b> <span className="muted">{h.room.code} · {h.room.description}</span>
                <div className="muted">{shortDate(h.checkIn)} to {shortDate(h.checkOut)} · {h.rooms} room(s)</div>
              </div>
              <span className="num">{money(h.room.price.total)}</span>
              <button type="button" className="link" onClick={() => remove('hotels', i)}>Remove</button>
            </div>
          ))}
          {basket.cars.map((c, i) => (
            <div key={`c${i}`} className="basket-row">
              <div>
                <b>{c.vendor} {c.sipp}</b> <span className="muted">{c.sampleModel}</span>
                <div className="muted">{c.location} · {shortDate(c.pickupDate)} to {shortDate(c.returnDate)}</div>
              </div>
              <span className="num">{money(c.price.total)}</span>
              <button type="button" className="link" onClick={() => remove('cars', i)}>Remove</button>
            </div>
          ))}
        </Panel>

        <Panel title="Travellers" actions={!hasFlights && (
          <span className="inline">Travellers <Stepper value={extraAdults} min={1} onChange={setExtraAdults} /></span>
        )}>
          {passengers.map((p, i) => (
            <div key={i} className="pax-row">
              <span className="pax-type">{typeLabel[p.type]}</span>
              <Field label="First name"><input required value={p.firstName} onChange={e => setPassenger(i, { firstName: e.target.value })} /></Field>
              <Field label="Last name"><input required value={p.lastName} onChange={e => setPassenger(i, { lastName: e.target.value })} /></Field>
              <Field label={p.type === 'ADT' ? 'Date of birth (optional)' : 'Date of birth'}>
                <input type="date" required={p.type !== 'ADT'} value={p.dateOfBirth ?? ''}
                       onChange={e => setPassenger(i, { dateOfBirth: e.target.value })} />
              </Field>
            </div>
          ))}
          <div className="pax-row">
            <span className="pax-type">Contact</span>
            <Field label="Email"><input type="email" value={email} onChange={e => setEmail(e.target.value)} /></Field>
            <Field label="Phone"><input value={phone} onChange={e => setPhone(e.target.value)} /></Field>
          </div>
        </Panel>
      </div>

      <aside className="booking-side panel">
        <h2>Summary</h2>
        <dl className="totals">
          {basket.flights.length > 0 && <><dt>Flights</dt><dd>{money(basket.flights.reduce((s, f) => s + f.price.grandTotal, 0))}</dd></>}
          {basket.hotels.length > 0 && <><dt>Hotels</dt><dd>{money(basket.hotels.reduce((s, h) => s + h.room.price.total, 0))}</dd></>}
          {basket.cars.length > 0 && <><dt>Cars</dt><dd>{money(basket.cars.reduce((s, c) => s + c.price.total, 0))}</dd></>}
          <dt className="grand">Estimated total</dt><dd className="grand">{money(estimate)}</dd>
        </dl>
        <p className="muted small">
          Prices are confirmed when the PNR is created. Seats, rooms and cars are then held for 30 minutes until ticketing.
        </p>
        <ErrorNote error={error} />
        <button className="primary" disabled={busy}>{busy ? 'Creating PNR…' : 'Create PNR and hold'}</button>
      </aside>
    </form>
  )
}
