package org.opendaylight.yangtools.restconf.client;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import com.sun.jersey.api.client.ClientResponse;
import java.io.InputStream;
import java.util.Map.Entry;
import org.opendaylight.yangtools.binding.data.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.yangtools.restconf.client.api.data.Datastore;
import org.opendaylight.yangtools.restconf.client.api.data.DefaultRetrievalStrategy;
import org.opendaylight.yangtools.restconf.client.api.data.RetrievalStrategy;
import org.opendaylight.yangtools.restconf.utils.RestconfUtils;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public abstract class AbstractDataStore implements Datastore {

    private final RestconfClientImpl client;

    public AbstractDataStore(RestconfClientImpl client) {
        super();
        this.client = client;
    }

    protected RestconfClientImpl getClient() {
        return client;
    }

    @Override
    public <T extends DataObject> ListenableFuture<Optional<T>> readData(InstanceIdentifier<T> path) {
        return readData(path, DefaultRetrievalStrategy.getInstance());
    }

    @Override
    public <T extends DataObject> ListenableFuture<Optional<T>> readData(final InstanceIdentifier<T> path,
            RetrievalStrategy strategy) {

        final SchemaContext schemaContext = client.getSchemaContext();
        final BindingNormalizedNodeCodecRegistry mappingService = client.getMappingService();
        final YangInstanceIdentifier domPath = mappingService.toYangInstanceIdentifier(path);
        final Entry<String, DataSchemaNode> pathWithSchema = RestconfUtils.toRestconfIdentifier(domPath,schemaContext);
        String restconfPath = getStorePrefix() + pathWithSchema.getKey();

        return client.get(restconfPath, "application/xml",new Function<ClientResponse, Optional<T>>() {

            @SuppressWarnings("unchecked")
            @Override
            public com.google.common.base.Optional<T> apply(ClientResponse response) {
                switch (response.getStatus()) {
                case 200: // Status OK
                    DataObject dataObject = deserialize(domPath,response.getEntityInputStream());
                    return (Optional<T>) Optional.of(dataObject);
                case 404: // Status Not Found
                    return Optional.<T> absent();
                default:
                    throw new IllegalStateException("Failed : HTTP error code : " + response.getStatus());
                }
            }


        });
    }

    protected final DataObject deserialize(YangInstanceIdentifier domPath, InputStream entityInputStream) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    protected abstract String getStorePrefix();

}
