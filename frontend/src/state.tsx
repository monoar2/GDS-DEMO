/* eslint-disable react-refresh/only-export-components -- providers and their hooks live together on purpose */
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api, setToken, setUnauthorizedHandler } from './api'
import type { AirOffer, Airport, CarOffer, HotelOffer, RoomOffer, Session } from './api'

// ---------------------------------------------------------------- session

const SESSION_KEY = 'gds.session'

interface AuthState {
  session: Session | null
  login: (username: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthState | null>(null)

function readSession(): Session | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    return new Date(session.expiresAt).getTime() > Date.now() ? session : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => {
    const restored = readSession()
    setToken(restored?.token ?? null)
    return restored
  })

  const logout = useCallback(() => {
    setToken(null)
    setSession(null)
    try {
      localStorage.removeItem(SESSION_KEY)
    } catch {
      // storage unavailable, nothing to clear
    }
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const next = await api.login(username, password)
    setToken(next.token)
    setSession(next)
    try {
      localStorage.setItem(SESSION_KEY, JSON.stringify(next))
    } catch {
      // storage unavailable, the session lasts until the tab closes
    }
  }, [])

  useEffect(() => setUnauthorizedHandler(logout), [logout])

  const value = useMemo(() => ({ session, login, logout }), [session, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth outside AuthProvider')
  return context
}

// ---------------------------------------------------------------- reference data

const AirportsContext = createContext<Airport[]>([])

export function AirportsProvider({ children }: { children: ReactNode }) {
  const [airports, setAirports] = useState<Airport[]>([])
  useEffect(() => {
    api.airports().then(setAirports).catch(() => setAirports([]))
  }, [])
  return <AirportsContext.Provider value={airports}>{children}</AirportsContext.Provider>
}

export const useAirports = () => useContext(AirportsContext)

// ---------------------------------------------------------------- booking basket

/** What the agent has picked while shopping, before it becomes a PNR. */
export interface Basket {
  flights: AirOffer[]
  hotels: { hotel: HotelOffer; room: RoomOffer; checkIn: string; checkOut: string; rooms: number }[]
  cars: CarOffer[]
  party: { adults: number; children: number; infants: number }
}

const emptyBasket: Basket = { flights: [], hotels: [], cars: [], party: { adults: 1, children: 0, infants: 0 } }

interface BasketState {
  basket: Basket
  addFlight: (offer: AirOffer, party: Basket['party']) => void
  addHotel: (item: Basket['hotels'][number]) => void
  addCar: (offer: CarOffer) => void
  remove: (kind: 'flights' | 'hotels' | 'cars', index: number) => void
  clear: () => void
  count: number
}

const BasketContext = createContext<BasketState | null>(null)

export function BasketProvider({ children }: { children: ReactNode }) {
  const [basket, setBasket] = useState<Basket>(emptyBasket)

  const value = useMemo<BasketState>(() => ({
    basket,
    addFlight: (offer, party) => setBasket(b => ({ ...b, flights: [...b.flights, offer], party })),
    addHotel: item => setBasket(b => ({ ...b, hotels: [...b.hotels, item] })),
    addCar: offer => setBasket(b => ({ ...b, cars: [...b.cars, offer] })),
    remove: (kind, index) => setBasket(b => ({ ...b, [kind]: b[kind].filter((_, i) => i !== index) })),
    clear: () => setBasket(emptyBasket),
    count: basket.flights.length + basket.hotels.length + basket.cars.length,
  }), [basket])

  return <BasketContext.Provider value={value}>{children}</BasketContext.Provider>
}

export function useBasket() {
  const context = useContext(BasketContext)
  if (!context) throw new Error('useBasket outside BasketProvider')
  return context
}
