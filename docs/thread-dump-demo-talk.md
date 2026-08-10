# Thread Dumps: Observability on Loan Application

Most of Java engineers know how to run thread dump and look the data, but here is different. The thread dump will lie to you.

We're using a different framework called Scala ZIO, that is using Fibers under the hood.

Fibers are not threads, and not virtual threads. It has its own implementation. So running a thread dump you will get nothing.

Think about a fellow backend engineer on-call. And the backend get stuck in production. What needs to do?


## The one thing to say if you only remember one thing

This service runs on ZIO. A small, fixed pool of carrier threads runs potentially
thousands of lightweight fibers. If you `jstack` this app during an incident like you
would any other Java service, you'll see mostly idle threads and learn nothing — the
actual stuck work is invisible at the thread level. This endpoint is where the truth
is `curl -s -X GET http://localhost:8080/admin/fibers`


---

## 0. Infrastructure

Everything is running on live `kind-kind` cluster — every output shown is real, not invented.

So no browser is needed for this talk, only the terminal.

```bash
kubectl --context kind-kind -n observability-stack get pods
```

---

## 1. Hook (0:00–1:00)

I'm going to reproduce that gap live — deliberately and safely — then show you the
tool that actually works, and how it ties back to a specific customer's loan
application, not just 'a fiber somewhere.'

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

I'm going to reproduce a stuck fiber — this is a built-in, safe way to do
that for exactly this kind of walkthrough, not a real bug.

```bash
curl -s -X POST http://localhost:8080/admin/fault/stuck
```

Expected: `captured=<uuid>` (an auto-capture bundle already got written — more on that in
a minute).

```bash
curl -s http://localhost:8080/admin/threads | grep -c AdminRoutes
```

Expected: `0`. It means: "the thread dump has nothing.
`ThreadMXBean` only knows about OS threads, and this sleep never blocked a single thread.

```bash
curl -s http://localhost:8080/admin/fibers | grep -A2 AdminRoutes
```

Expected real output.

```
Status: Suspended((Interruption, CooperativeYielding, FiberRoots), com.loan.http.AdminRoutes.routes(AdminRoutes.scala:37))
	at com.loan.http.AdminRoutes.routes(AdminRoutes.scala:37)
```

There it is — exact file, exact line. That's the whole pitch. If this were a real
incident, this is the command that would you run to see something, not a thread dump.

---

## 4. Live: tying it to a real customer (6:00–8:30)

A stuck fiber on its own just says 'something is stuck.' What actually matters
on-call is who is affected? or what loan application is affected?. Let's do that with real traffic — no fault
injection this time, just a normal burst of real requests.

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

That is great! Nos I can track all the fibers with the correlation id and user id.

When a fiber get stuck, we can identify exactly what users are being impacted.

`POST /admin/jfr/dump` isn't a fault trigger — it's the literal "take a snapshot right
now". While those 150 real requests were actually in flight, this took a
runtime snapshot.

Expected real output:

```
capture id: ee227cca-bc4d-4bf6-9a11-bd92da5bad88
zio-fiber-809843173 -> correlation_id=demo-corr-124 user_id=demo-user-124 ageMs=17
zio-fiber-256112103 -> correlation_id=demo-corr-87 user_id=demo-user-87 ageMs=17
```

For any of these rows and you have your answer during a real incident:
not just 'a fiber is stuck' message, but you get 'correlation id X, customer Y, and any other variables you can put on the context'. That's the difference between a curiosity and something you can act on.


---

## 5. Close

That all that I've for today. I hope you get new insights from this use case.

And just reminding, not all JVM applications can be monitored as equal.