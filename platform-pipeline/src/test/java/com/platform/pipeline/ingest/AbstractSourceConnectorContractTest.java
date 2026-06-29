package com.platform.pipeline.ingest;

import com.platform.pipeline.ingest.sync.InMemoryOffsetStore;
import com.platform.pipeline.ingest.sync.OffsetStore;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class AbstractSourceConnectorContractTest {
    private final OffsetStore offsetStore = new InMemoryOffsetStore();

    abstract String protocol();

    SourceConnector connector() {
        ProtocolAdapter adapter = new ContractAdapter(protocol(), "[{\"id\":\"1\"},{\"id\":\"2\"}]");
        return new AbstractSourceConnector(adapter, ConnectorSpecs.forProtocol(protocol()), offsetStore);
    }

    URI goodEndpoint() {
        return URI.create(protocol().toLowerCase().replace("_", "") + "://ok/source");
    }

    URI badEndpoint() {
        return URI.create(protocol().toLowerCase().replace("_", "") + "://bad/source");
    }

    @Test
    void checkReportsSuccessAndFailure() {
        SourceConnector connector = connector();

        assertTrue(connector.check(goodEndpoint()).ok());
        assertFalse(connector.check(badEndpoint()).ok());
    }

    @Test
    void readReturnsBatchAndAdvancesOffset() {
        SourceConnector connector = connector();

        RawDataBatch first = connector.read(goodEndpoint(), 0, 100);
        RawDataBatch resumed = connector.read(goodEndpoint(), first.nextOffset(), 100);

        assertEquals(1, first.records().size());
        assertEquals(1L, first.nextOffset());
        assertTrue(resumed.records().isEmpty());
        assertEquals(first.nextOffset(), resumed.nextOffset());
    }

    @Test
    void checkpointPersistsMonotonicOffset() {
        SourceConnector connector = connector();

        assertEquals(3L, connector.checkpoint(99L, 3L));
        assertEquals(3L, connector.checkpoint(99L, 2L));
    }

    @Test
    void exposesSpecDiscoverAndCloseContract() {
        SourceConnector connector = connector();

        assertEquals(protocol(), connector.protocol());
        assertEquals(protocol(), connector.spec().protocol());
        assertEquals(List.of(), connector.discover(goodEndpoint()));
        assertDoesNotThrow(connector::close);
    }

    private static class ContractAdapter implements ProtocolAdapter {
        private final String protocol;
        private final String payload;

        private ContractAdapter(String protocol, String payload) {
            this.protocol = protocol;
            this.payload = payload;
        }

        @Override
        public String protocol() {
            return protocol;
        }

        @Override
        public String fetch(URI endpoint) {
            if ("bad".equals(endpoint.getHost())) {
                throw new IllegalStateException("contract endpoint is unavailable");
            }
            return payload;
        }
    }
}
