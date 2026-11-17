package repro;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.micrometer.tracing.otel.bridge.CompositeSpanExporter;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.internal.otlp.traces.ResourceSpansMarshaler;
import io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Exports the same three spans once directly and once through Micrometer's
 * CompositeSpanExporter, then compares what an OTLP exporter would send.
 */
public class Main {

    static final int SPAN_COUNT = 3;

    static final int SERIALIZE_ROUNDS = 20;

    public static void main(String[] args) throws IOException {
        Resource resource = Resource.create(
                Attributes.of(AttributeKey.stringKey("service.name"), "demo"),
                "https://opentelemetry.io/schemas/1.24.0");

        CapturingExporter direct = new CapturingExporter();
        CapturingExporter viaMicrometer = new CapturingExporter();
        SpanExporter composite = new CompositeSpanExporter(Collections.singletonList(viaMicrometer), null, null, null);

        SdkTracerProvider provider = SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(SimpleSpanProcessor.create(direct))
            .addSpanProcessor(SimpleSpanProcessor.create(composite))
            .build();
        Tracer tracer = provider.get("repro");
        for (int i = 0; i < SPAN_COUNT; i++) {
            tracer.spanBuilder("span-" + i).startSpan().end();
        }
        provider.close();

        report("direct (SDK only)", direct.spans);
        report("via CompositeSpanExporter", viaMicrometer.spans);
    }

    static void report(String label, List<SpanData> spans) throws IOException {
        Set<String> distinctPayloads = new HashSet<>();
        int payloadSize = 0;
        for (int i = 0; i < SERIALIZE_ROUNDS; i++) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            TraceRequestMarshaler.create(spans).writeBinaryTo(out);
            distinctPayloads.add(java.util.Base64.getEncoder().encodeToString(out.toByteArray()));
            payloadSize = out.size();
        }
        System.out.println("== " + label);
        System.out.println("  spans                          : " + spans.size());
        System.out.println("  ResourceSpans in OTLP request  : " + ResourceSpansMarshaler.create(spans).length);
        System.out.println("  getResource() same instance    : " + (spans.get(0).getResource() == spans.get(0).getResource()));
        System.out.println("  resource schemaUrl             : " + spans.get(0).getResource().getSchemaUrl());
        System.out.println("  OTLP payload bytes             : " + payloadSize);
        System.out.println("  distinct payloads in " + SERIALIZE_ROUNDS + " runs   : " + distinctPayloads.size());
    }

    static class CapturingExporter implements SpanExporter {

        final List<SpanData> spans = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> batch) {
            spans.addAll(batch);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }

    }

}
