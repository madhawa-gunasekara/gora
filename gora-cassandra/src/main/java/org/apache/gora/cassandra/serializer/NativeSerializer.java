package org.apache.gora.cassandra.serializer;

import org.apache.gora.cassandra.store.CassClient;
import org.apache.gora.cassandra.store.CassandraMapping;
import org.apache.gora.persistency.impl.PersistentBase;

/**
 * Created by renatomarroquin on 2017-01-30.
 */
public class NativeSerializer extends CassSerializer {
    public NativeSerializer(CassClient cc) {
        super(cc);
    }
    @Override
    public void createSchema() {
        CassandraMapping cm = this.client.getMapping();
        this.client.createKeyspace(cm.getKsName(), cm.getKsReplicationFactor(), cm.getKsReplicationStrategy());
        this.client.createTable(cm.getKsName(), "", cm.getColumnsAttribs());
        //TODO let client create schema

    }

    @Override
    public void put(Object key, PersistentBase value) {

    }

    @Override
    public PersistentBase get(Object key) {
        return null;
    }

    @Override
    public void delete(Object key) {

    }

    @Override
    public void get(String query) {

    }
}
