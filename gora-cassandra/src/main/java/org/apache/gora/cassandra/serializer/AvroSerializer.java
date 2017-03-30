package org.apache.gora.cassandra.serializer;

import org.apache.gora.cassandra.store.CassClient;
import org.apache.gora.persistency.impl.PersistentBase;

/**
 * Created by renatomarroquin on 2017-01-30.
 */
public class AvroSerializer extends CassSerializer {
    public AvroSerializer(CassClient cc){
        super(cc);
    }
    @Override
    public void createSchema() {
        //TODO unroll schema
        this.client.getMapping();
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
