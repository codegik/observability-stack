# Talk: Why Thread Dumps Lie to You on This Service (10 min)

Audience: fellow backend engineers / on-call. One takeaway, memorized by the end: **if
`loan-backend` hangs in production, don't reach for a JVM thread dump first — hit
`/admin/fibers`.**

Everything below was run against the live `kind-kind` cluster before writing this
script — every output shown is real, not invented.

## The one thing to say if you only remember one thing

> "This service runs on ZIO. A small, fixed pool of carrier threads runs potentially
> thousands of lightweight fibers. If you `jstack` this app during an incident like you
> would any other Java service, you'll see mostly idle threads and learn nothing — the
> actual stuck work is invisible at the thread level. `/admin/fibers` is where the truth
> is."

That's the entire value proposition. Everything else in this talk exists to prove it and
show what to do about it.

---

## 0. Pre-flight (5 min before the talk, not on stage)

```bash
kubectl --context kind-kind -n observability-stack get pods
```

All pods `Running`. No port-forward needed — `/admin/*` and `/api/*` are reachable
directly at `http://localhost:8080` (kind `extraPortMappings`).

**Known issue, unrelated to this talk**: traces aren't reaching Tempo right now (see
`deploy/README.md`) — don't lean on "search by correlation_id in Tempo" today.

Open one terminal, font size up. No browser needed for this version of the talk.

---

## 1. Hook (0:00–1:00)

Say the boxed line above, out loud, as the opener. Then:

> "I'm going to reproduce that gap live — deliberately and safely — then show you the
> tool that actually works, and how it ties back to a specific customer's loan
> application, not just 'a fiber somewhere.'"

---

## 2. Why this happens (1:00–3:00)

Open `backend/src/main/scala/com/loan/http/HeaderMiddleware.scala`. Point at:

```scala
ContextRefs.correlationId.locally(cid) {
  ContextRefs.userId.locally(uid) {
    ...
```

`correlationId`/`userId` are ZIO `FiberRef`s — every child fiber spawned while handling
this request inherits them automatically, no manual threading through function calls.
That's *why* a fiber-level dump can be labeled per customer/request, and a thread-level
one fundamentally can't: threads don't carry this context, fibers do.

One sentence on the architecture: this app's thread pool is small and constant no matter
how many requests are in flight; the fiber count scales with actual concurrent work. A
thread dump shows you the constant; a fiber dump shows you the work.

---

## 3. Live: the gap, reproduced safely (3:00–6:00) — the core moment

> "I'm going to deliberately reproduce a stuck fiber — this is a built-in, safe way to do
> that for exactly this kind of walkthrough, not a real bug."

```bash
curl -s -X POST http://localhost:8080/admin/fault/stuck
```

Expected: `captured=<uuid>` (an auto-capture bundle already got written — more on that in
a minute).

```bash
curl -s http://localhost:8080/admin/threads | grep -c AdminRoutes
```

Expected: `0`. Say it plainly: "the thread dump has nothing. Not 'hard to find' — nothing.
`ThreadMXBean` only knows about OS threads, and this sleep never blocked one."

```bash
curl -s http://localhost:8080/admin/fibers | grep -A2 AdminRoutes
```

Expected (real output, confirmed moments before writing this):

```
Status: Suspended((Interruption, CooperativeYielding, FiberRoots), com.loan.http.AdminRoutes.routes(AdminRoutes.scala:37))
	at com.loan.http.AdminRoutes.routes(AdminRoutes.scala:37)
```

> "There it is — exact file, exact line. That's the whole pitch. If this were a real
> incident, this is the command that would've told you something a thread dump couldn't."

---

## 4. Live: tying it to a real customer (6:00–8:30)

> "A stuck fiber on its own just says 'something is stuck.' What actually matters
> on-call is *whose* loan application it is. Let's do that with real traffic — no fault
> injection this time, just a normal burst of real requests."

```bash
POD=$(kubectl --context kind-kind -n observability-stack get pod -l app=loan-backend -o jsonpath='{.items[0].metadata.name}')

for i in $(seq 1 150); do
  curl -s -X POST http://localhost:8080/api/loan-requests \
    -H "X-Correlation-Id: demo-corr-$i" -H "X-User-Id: demo-user-$i" \
    -H "Content-Type: application/json" \
    -d '{"amount":15000,"termMonths":36,"purpose":"AUTO"}' >/dev/null &
done
CAPID=$(curl -s -X POST http://localhost:8080/admin/jfr/dump | sed -E 's/captured=//')
wait

if ! [[ "$CAPID" =~ ^[0-9a-f-]{36}$ ]]; then
  echo "manual capture didn't fire (got '$CAPID') — this trigger has a 10s cooldown; wait ~10s and rerun this whole block"
else
  echo "capture id: $CAPID"
  kubectl --context kind-kind -n observability-stack exec "$POD" -- cat "/var/dumps/$CAPID/fiber-journeys.txt" | head -10
fi
```

Run this as **one block, in one terminal** — the capture id has to survive from the
`curl` that creates it to the `kubectl exec` that reads it back, and splitting it across
two separate pastes (or two terminal panes) loses that. The guard above catches the other
way this fails on stage: rerunning within 10 seconds of the last manual capture (easy to
do while rehearsing) gets silently cooldown-suppressed by `CaptureService`, and without
the check you'd otherwise see a confusing `cat: ... No such file or directory` instead of
a clear "wait and rerun" message.

`POST /admin/jfr/dump` isn't a fault trigger — it's the literal "take a snapshot right
now" operator action. While those 150 real requests were actually in flight, this took a
runtime snapshot.

Expected (real output, confirmed moments before writing this):

```
capture id: ee227cca-bc4d-4bf6-9a11-bd92da5bad88
zio-fiber-809843173 -> correlation_id=demo-corr-124 user_id=demo-user-124 ageMs=17
zio-fiber-256112103 -> correlation_id=demo-corr-87 user_id=demo-user-87 ageMs=17
...
```

> "Every one of those lines is a real, genuinely in-flight request at the moment I took
> this snapshot, with its actual correlation id and user id attached. Swap 'stuck fiber
> from section 3' for any of these rows and you have your answer during a real incident:
> not 'a fiber is stuck,' but 'correlation id X, customer Y, has been stuck for Z
> milliseconds.' That's the difference between a curiosity and something you can act on."

Two things to know but not necessarily raise unprompted:
- A negative `ageMs` on a row is benign — a fiber registered a few milliseconds after the
  snapshot's own timestamp was taken, a harmless ordering artifact of concurrent
  registration.
- Even with a valid capture id and real concurrent load, the file can occasionally come
  back with zero lines — the snapshot landed in a genuine gap with no fiber in flight at
  that instant. Happened once in 5 rehearsal trials; a rerun (after the 10s cooldown)
  immediately succeeded. If it happens live, just say so and rerun the block.

---

## 5. Close (8:30–10:00)

Restate the one-liner from the top: **if this app hangs, `/admin/fibers`, not `jstack`.**
And now with the second half: **`/admin/captures` already ties that to a specific
customer's request, via `correlation_id`/`user_id` — you don't have to build that
correlation by hand during an incident.**

What's next / open questions:
- The Tempo trace-export outage (section 0) — worth fixing so trace search by
  correlation_id actually works as documented.
- The auto-capture "unhandled error / 5xx" trigger had a real bug (fixed, verified) where
  it silently never fired for genuine application errors — worth a one-line mention if
  asked "has this actually been tested against something real," since the answer is yes.

Hand off to questions.

---

## Appendix: going further (not part of the 10-minute talk)

The backend image also bundles `async-profiler` (`asprof`) for ad-hoc wall-clock flame
graphs of real, successful traffic — useful if someone asks "can we see this over a
longer window instead of one instant," but it's a bigger, separate demo and doesn't
belong in this talk's 10 minutes. See `docs/architecture.md` §8.4(e) if it comes up.

## Quick reference

| Command | What it shows |
|---|---|
| `POST /admin/fault/stuck` | Deliberately reproduces a stuck fiber (safe, built-in) |
| `GET /admin/threads` \| `grep -c AdminRoutes` | `0` — the JVM thread dump sees nothing |
| `GET /admin/fibers` \| `grep -A2 AdminRoutes` | The exact stuck file/line |
| `POST /admin/jfr/dump` | Manual snapshot, right now — not a fault trigger |
| `GET /admin/captures/{id}` + `cat .../fiber-journeys.txt` | Which customer's request each live fiber belongs to |
