import React, { useState } from "react"
import { api } from "./api.js"

const PURPOSES = ["HOME_IMPROVEMENT", "DEBT_CONSOLIDATION", "AUTO", "MEDICAL", "EDUCATION", "OTHER"]
const box = { maxWidth: 560, margin: "40px auto", fontFamily: "system-ui, sans-serif" }
const field = { display: "block", width: "100%", padding: 8, margin: "6px 0 16px", boxSizing: "border-box" }
const btn = { padding: "10px 16px", cursor: "pointer" }

export default function App() {
  const [step, setStep] = useState("configure")
  const [requestId, setRequestId] = useState(null)
  const [config, setConfig] = useState({ amount: 18000, termMonths: 36, purpose: "AUTO" })
  const [person, setPerson] = useState({ email: "", displayName: "", monthlyIncome: 4000 })
  const [offers, setOffers] = useState([])
  const [chosen, setChosen] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const run = async (fn) => {
    setBusy(true); setError(null)
    try { await fn() } catch (e) { setError(String(e.message || e)) } finally { setBusy(false) }
  }

  const submitConfig = () => run(async () => {
    const r = await api.createLoanRequest({
      amount: Number(config.amount), termMonths: Number(config.termMonths), purpose: config.purpose
    })
    setRequestId(r.id)
    setStep("personal")
  })

  const submitPerson = () => run(async () => {
    await api.createUser({
      email: person.email, displayName: person.displayName, monthlyIncome: Number(person.monthlyIncome)
    })
    const o = await api.offers(requestId)
    setOffers(o.offers)
    setStep("offers")
  })

  const chooseOffer = (o) => run(async () => {
    await api.createApplication({ loanRequestId: requestId, productId: o.productId })
    setChosen(o)
    setStep("finish")
  })

  return (
    <div style={box}>
      <h1>Loan Application</h1>
      {error && <p style={{ color: "crimson" }}>{error}</p>}

      {step === "configure" && (
        <section>
          <h2>1. Configure your loan</h2>
          <label>Amount
            <input style={field} type="number" value={config.amount}
              onChange={(e) => setConfig({ ...config, amount: e.target.value })} />
          </label>
          <label>Term (months)
            <input style={field} type="number" value={config.termMonths}
              onChange={(e) => setConfig({ ...config, termMonths: e.target.value })} />
          </label>
          <label>Purpose
            <select style={field} value={config.purpose}
              onChange={(e) => setConfig({ ...config, purpose: e.target.value })}>
              {PURPOSES.map((p) => <option key={p} value={p}>{p}</option>)}
            </select>
          </label>
          <button style={btn} disabled={busy} onClick={submitConfig}>Continue</button>
        </section>
      )}

      {step === "personal" && (
        <section>
          <h2>2. Your details</h2>
          <label>Email
            <input style={field} type="email" value={person.email}
              onChange={(e) => setPerson({ ...person, email: e.target.value })} />
          </label>
          <label>Name
            <input style={field} value={person.displayName}
              onChange={(e) => setPerson({ ...person, displayName: e.target.value })} />
          </label>
          <label>Monthly income
            <input style={field} type="number" value={person.monthlyIncome}
              onChange={(e) => setPerson({ ...person, monthlyIncome: e.target.value })} />
          </label>
          <button style={btn} disabled={busy} onClick={submitPerson}>See offers</button>
        </section>
      )}

      {step === "offers" && (
        <section>
          <h2>3. Choose an offer</h2>
          {offers.length === 0 && <p>No offers found.</p>}
          {offers.map((o) => (
            <div key={o.productId} style={{ border: "1px solid #ccc", padding: 12, marginBottom: 12 }}>
              <strong>{o.name}</strong> — {o.apr}% APR
              <div>Amount: {o.offeredAmount} over {o.offeredTermMonths} months</div>
              <div>Monthly payment: {o.monthlyPayment} {o.affordable ? "(affordable)" : "(over budget)"}</div>
              <button style={btn} disabled={busy} onClick={() => chooseOffer(o)}>Choose</button>
            </div>
          ))}
        </section>
      )}

      {step === "finish" && (
        <section>
          <h2>Done</h2>
          <p>Your application for <strong>{chosen.name}</strong> has been submitted.</p>
          <p>Monthly payment: {chosen.monthlyPayment}</p>
        </section>
      )}
    </div>
  )
}
