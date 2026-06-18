function uuid() {
  return crypto.randomUUID()
}

export function userId() {
  let u = sessionStorage.getItem("user_uuid")
  if (!u) {
    u = uuid()
    sessionStorage.setItem("user_uuid", u)
  }
  return u
}

export function correlationId() {
  return uuid()
}
