import { userId, correlationId } from "./ids.js"

async function call(method, path, body) {
  const res = await fetch(path, {
    method,
    headers: {
      "Content-Type": "application/json",
      "X-Correlation-Id": correlationId(),
      "X-User-Id": userId()
    },
    body: body ? JSON.stringify(body) : undefined
  })
  if (!res.ok) throw new Error(`${res.status}: ${await res.text()}`)
  return res.json()
}

export const api = {
  createLoanRequest: (d) => call("POST", "/api/loan-requests", d),
  createUser: (d) => call("POST", "/api/users", d),
  offers: (id) => call("GET", `/api/loan-requests/${id}/offers`),
  createApplication: (d) => call("POST", "/api/applications", d)
}
