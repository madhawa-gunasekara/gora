package org.apache.gora.cassandra.store;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.exceptions.NoHostAvailableException;
import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import org.apache.gora.persistency.impl.PersistentBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Properties;

import static org.apache.gora.cassandra.store.CassandraStore.DEF_CLUSTER_NAME;
import static org.apache.gora.cassandra.store.CassandraStore.DEF_CONTACT_POINT;
import static org.apache.gora.cassandra.store.CassandraStore.DEF_CONTACT_PORT;

/**
 * Created by renatomarroquin on 2017-01-30.
 */
public class CassClient<K, T extends PersistentBase> {
    public static final Logger LOG = LoggerFactory.getLogger(CassClient.class);
    private Class<K> keyClass;
    private Class<T> persistentClass;
    private CassandraMapping cassandraMapping;
    private Cluster cluster;
    private Session session;

    public void initialize(Class<K> keyClass, Class<T> persistentClass, Properties properties) throws Exception {
        this.keyClass = keyClass;
        // get cassandra mapping with persistent class
        this.persistentClass = persistentClass;
        this.cassandraMapping = CassandraMappingManager.getManager().get(persistentClass);
        String username = "";
        String password = "";
        String clusterName = DEF_CLUSTER_NAME;
        String contactPoint = DEF_CONTACT_POINT;
        String contactPort = DEF_CONTACT_PORT;

        if (properties != null) {
            username = properties.getProperty("gora.cassandrastore.username", "");
            password = properties.getProperty("gora.cassandrastore.password", "");
            clusterName = properties.getProperty("gora.cassandrastore.cluster", clusterName);
            contactPoint = properties.getProperty("gora.cassandrastore.host", contactPoint);
            contactPort = properties.getProperty("gora.cassandrastore.host.port", contactPort);
        }

        Cluster.Builder clBuilder = Cluster.builder();
        clBuilder.withClusterName(clusterName).addContactPoint(contactPoint).withPort(Integer.parseInt(contactPort));

        if (username != null || password != null) {
            clBuilder.withCredentials(username, password);
        }
        try {
            this.cluster = clBuilder.build();
            this.session = this.cluster.connect();
        } catch (IllegalArgumentException | NoHostAvailableException e) {
            LOG.error(String.format("Error while connecting to Cassandra cluster.%s", e.getMessage()));
        }
    }

    public void createKeyspace(String ks, int ksRepFactor, String ksRepStrategy) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE KEYSPACE ").append(ks).append(" WITH replication = {'class':'");
        sb.append(ksRepStrategy).append("', 'replication_factor':").append(ksRepFactor).append("};");
        session.execute(sb.toString());
    }

    public CassandraMapping getMapping() {
        return this.cassandraMapping;
    }

    public String getColType(String colName) {
        String colType = "";
        try {
            colType = persistentClass.getDeclaredField(colName).getType().getName();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return colType;
    }

    public void addColumn(String colFamily, String colName, String colTtl, String colType) {

    }

    public void createTable(String ksName, String colFamily, Map<String, String> colsAttribs) {
        SchemaBuilder.createTable(ksName, colFamily);
        for(Map.Entry entry: colsAttribs.entrySet()) {
            String colType = getColType(entry.getKey().toString());
        }

    }
}
