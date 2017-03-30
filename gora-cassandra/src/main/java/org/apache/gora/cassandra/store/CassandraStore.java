package org.apache.gora.cassandra.store;

import org.apache.gora.cassandra.serializer.CassSerializer;
import org.apache.gora.persistency.impl.PersistentBase;
import org.apache.gora.query.PartitionQuery;
import org.apache.gora.query.Query;
import org.apache.gora.query.Result;
import org.apache.gora.store.impl.DataStoreBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.apache.gora.store.DataStoreFactory.findProperty;

/**
 * Created by renatomarroquin on 2017-01-30.
 */
public class CassandraStore<K, T extends PersistentBase> extends DataStoreBase<K, T> {
    // Logging implementation
    public static final Logger LOG = LoggerFactory.getLogger(CassandraStore.class);
    // Consistency level for Cassandra column families
    private static final String COL_FAM_CL = "cf.consistency.level";
    // Consistency level for Cassandra read operations.
    private static final String READ_OP_CL = "read.consistency.level";
    // Consistency level for Cassandra write operations.
    private static final String WRITE_OP_CL = "write.consistency.level";
    private static final String SERIALIZER_TYPE = "serialization";
    public static final String DEF_CLUSTER_NAME = "Test Cluster";
    public static final String DEF_CONTACT_POINT = "localhost";
    public static final String DEF_CONTACT_PORT = "9042";

    // Variables to hold different consistency levels defined by the properties.
    private static String colFamConsLvl;
    private static String readOpConsLvl;
    private static String writeOpConsLvl;

    private static CassSerializer serializer;
    private static CassClient client;

    public enum SerializerType {
        AVRO("AVRO"),NATIVE("NATIVE"),BINARY("BINARY");
        String val;
        SerializerType(String v){
            this.val = v;
        }
    }

    public void initialize(Class<K> keyClass, Class<T> persistentClass, Properties properties) {
        try {
            super.initialize(keyClass, persistentClass, properties);
            client = new CassClient();
            client.initialize(keyClass, persistentClass, properties);
            serializer = CassSerializer.getSerializer(client, findProperty(properties, this, SERIALIZER_TYPE, ""));
            if (autoCreateSchema) {
                // column family
                colFamConsLvl = findProperty(properties, this, COL_FAM_CL, null);
                // operations
                readOpConsLvl = findProperty(properties, this, READ_OP_CL, null);
                writeOpConsLvl = findProperty(properties, this, WRITE_OP_CL, null);
                // create schema
                serializer.createSchema();
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

    @Override
    public String getSchemaName() {
        return null;
    }

    @Override
    public void createSchema() {
        serializer.createSchema();
    }

    @Override
    public void deleteSchema() {

    }

    @Override
    public boolean schemaExists() {
        return false;
    }

    @Override
    public T get(K key, String[] fields) {
        return null;
    }

    @Override
    public void put(K key, T obj) {

    }

    @Override
    public boolean delete(K key) {
        return false;
    }

    @Override
    public long deleteByQuery(Query<K, T> query) {
        return 0;
    }

    @Override
    public Result<K, T> execute(Query<K, T> query) {
        return null;
    }

    @Override
    public Query<K, T> newQuery() {
        return null;
    }

    @Override
    public List<PartitionQuery<K, T>> getPartitions(Query<K, T> query) throws IOException {
        return null;
    }

    @Override
    public void flush() {

    }

    @Override
    public void close() {

    }
}
