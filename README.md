# micrometer-tracing: `OtelFinishedSpan` rebuilds the `Resource` on every call

Minimal reproducer. It exports the same three spans twice, once directly from the
OpenTelemetry SDK and once through Micrometer's `CompositeSpanExporter`. Then it
compares what an OTLP exporter would send.

```
./gradlew -q run
```

Output with `micrometer-tracing-bridge-otel:1.6.8`:

```
== direct (SDK only)
  spans                          : 3
  ResourceSpans in OTLP request  : 1
  getResource() same instance    : true
  resource schemaUrl             : https://opentelemetry.io/schemas/1.24.0
  OTLP payload bytes             : 280
  distinct payloads in 20 runs   : 1
== via CompositeSpanExporter
  spans                          : 3
  ResourceSpans in OTLP request  : 3
  getResource() same instance    : false
  resource schemaUrl             : null
  OTLP payload bytes             : 315
  distinct payloads in 20 runs   : 6
```

The last number varies between runs: the `ResourceSpans` order follows the identity hash
codes of the freshly built `Resource` instances.

`TraceRequestMarshaler` and `ResourceSpansMarshaler` are OpenTelemetry internal classes (the ones
the OTLP exporters use), so the OpenTelemetry version is pinned to 1.55.0.

To run against a locally published build: `./gradlew -q run -PbridgeVersion=<version>`
(uses `mavenLocal()`).
