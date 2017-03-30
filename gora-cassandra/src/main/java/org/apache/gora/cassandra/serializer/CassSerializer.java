package org.apache.gora.cassandra.serializer;

import org.apache.gora.cassandra.store.CassClient;
import org.apache.gora.persistency.impl.PersistentBase;

import static org.apache.gora.cassandra.store.CassandraStore.SerializerType;

/**
 * Created by renatomarroquin on 2017-01-30.
 */
public abstract class CassSerializer<K, T extends PersistentBase> {
    public CassClient client;

    CassSerializer(CassClient cc) {
        this.client = cc;
    }

    public abstract void createSchema();

    public void deleteSchema() {

    }

    public abstract void put(K key, T value);

    public abstract T get(K key);

    public abstract void delete(K key);

    public abstract void get(String query);

    public static CassSerializer getSerializer(CassClient cc, String type) {
        SerializerType serType = type.isEmpty() ? SerializerType.NATIVE : SerializerType.valueOf(type.toUpperCase());
        CassSerializer ser;
        switch (serType) {
            case AVRO:
                ser = new AvroSerializer(cc);
                break;
            case BINARY:
                ser = new BinarySerializer(cc);
                break;
            case NATIVE:
            default:
                ser = new NativeSerializer(cc);

        }
        return ser;
    }
}
