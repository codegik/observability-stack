import { registerInstrumentations } from "@opentelemetry/instrumentation"
import { FetchInstrumentation } from "@opentelemetry/instrumentation-fetch"
import { WebTracerProvider, SimpleSpanProcessor } from "@opentelemetry/sdk-trace-web"
import { OTLPTraceExporter } from "@opentelemetry/exporter-trace-otlp-http"
import { ZoneContextManager } from "@opentelemetry/context-zone"
import { resourceFromAttributes, defaultResource } from "@opentelemetry/resources"
import { W3CTraceContextPropagator } from "@opentelemetry/core"

const provider = new WebTracerProvider({
  resource: defaultResource().merge(
    resourceFromAttributes({
      "service.name": "loan-frontend"
    })
  ),
  spanProcessors: [
    new SimpleSpanProcessor(
      new OTLPTraceExporter({
        url: "/v1/traces"
      })
    )
  ]
})

provider.register({
  contextManager: new ZoneContextManager(),
  propagator: new W3CTraceContextPropagator()
})

registerInstrumentations({
  instrumentations: [
    new FetchInstrumentation({
      propagateTraceHeaderCorsUrls: [
        /.*\/api\/.*/, // propagate traces to backend endpoints
        window.location.origin
      ]
    })
  ]
})
