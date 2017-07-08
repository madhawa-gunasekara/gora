package org.apache.gora.cassandra.store;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.ProtocolOptions;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SocketOptions;
import com.datastax.driver.core.TypeCodec;
import com.datastax.driver.core.policies.ConstantReconnectionPolicy;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.DefaultRetryPolicy;
import com.datastax.driver.core.policies.DowngradingConsistencyRetryPolicy;
import com.datastax.driver.core.policies.ExponentialReconnectionPolicy;
import com.datastax.driver.core.policies.FallthroughRetryPolicy;
import com.datastax.driver.core.policies.LatencyAwarePolicy;
import com.datastax.driver.core.policies.LoggingRetryPolicy;
import com.datastax.driver.core.policies.RoundRobinPolicy;
import com.datastax.driver.core.policies.TokenAwarePolicy;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * This class provides the Cassandra Client Connection.
 * Initialize the Cassandra Connection according to the Properties.
 */
public class CassandraClient {

  private static final Logger LOG = LoggerFactory.getLogger(CassandraClient.class);


  private Cluster cluster;

  public Session getSession() {
    return session;
  }

  public Cluster getCluster() {
    return cluster;
  }

  private Session session;


  public void initialize(Properties properties) throws Exception {
    Cluster.Builder builder = Cluster.builder();
    List<String> codecs = readCustomCodec(properties);
    builder = populateSettings(builder, properties);
    this.cluster = builder.build();
    if (codecs != null) {
      registerCustomCodecs(codecs);
    }
    this.session = this.cluster.connect();
  }


  private Cluster.Builder populateSettings(Cluster.Builder builder, Properties properties) {
    String serversParam = properties.getProperty(CassandraStoreParameters.CASSANDRA_SERVERS);
    String[] servers = serversParam.split(",");
    for (String server : servers) {
      builder = builder.addContactPoint(server);
    }
    String portProp = properties.getProperty(CassandraStoreParameters.PORT);
    if (portProp != null) {
      builder = builder.withPort(Integer.parseInt(portProp));
    }
    String clusterNameProp = properties.getProperty(CassandraStoreParameters.CLUSTER_NAME);
    if (clusterNameProp != null) {
      builder = builder.withClusterName(clusterNameProp);
    }
    String compressionProp = properties.getProperty(CassandraStoreParameters.COMPRESSION);
    if (compressionProp != null) {
      builder = builder.withCompression(ProtocolOptions.Compression.valueOf(compressionProp));
    }
    builder = this.populateCredentials(properties, builder);
    builder = this.populateLoadBalancingProp(properties, builder);
    String enableJMXProp = properties.getProperty(CassandraStoreParameters.ENABLE_JMX_REPORTING);
    if (!Boolean.parseBoolean(enableJMXProp)) {
      builder = builder.withoutJMXReporting();
    }
    String enableMetricsProp = properties.getProperty(CassandraStoreParameters.ENABLE_METRICS);
    if (!Boolean.parseBoolean(enableMetricsProp)) {
      builder = builder.withoutMetrics();
    }
    builder = this.populatePoolingSettings(properties, builder);
    String versionProp = properties.getProperty(CassandraStoreParameters.PROTOCOL_VERSION);
    if (versionProp != null) {
      builder = builder.withProtocolVersion(ProtocolVersion.fromInt(Integer.parseInt(versionProp)));
    }
    builder = this.populateQueryOptions(properties, builder);
    builder = this.populateReconnectPolicy(properties, builder);
    builder = this.populateRetrytPolicy(properties, builder);
    builder = this.populateSocketOptions(properties, builder);
    String enableSSLProp = properties.getProperty(CassandraStoreParameters.ENABLE_SSL);
    if (enableSSLProp != null) {
      if (Boolean.parseBoolean(enableSSLProp)) {
        builder = builder.withSSL();
      }
    }
    return builder;
  }


  private Cluster.Builder populateLoadBalancingProp(Properties properties, Cluster.Builder builder) {
    String loadBalancingProp = properties.getProperty(CassandraStoreParameters.LOAD_BALANCING_POLICY);
    if (loadBalancingProp != null) {
      switch (loadBalancingProp) {
        case "LatencyAwareRoundRobinPolicy":
          builder = builder.withLoadBalancingPolicy(LatencyAwarePolicy.builder(new RoundRobinPolicy()).build());
          break;
        case "RoundRobinPolicy":
          builder = builder.withLoadBalancingPolicy(new RoundRobinPolicy());
          break;
        case "DCAwareRoundRobinPolicy": {
          String dataCenter = properties.getProperty(CassandraStoreParameters.DATA_CENTER);
          boolean allowRemoteDCsForLocalConsistencyLevel = Boolean.parseBoolean(
                  properties.getProperty(CassandraStoreParameters.ALLOW_REMOTE_DCS_FOR_LOCAL_CONSISTENCY_LEVEL));
          if (dataCenter != null && !dataCenter.isEmpty()) {
            if (allowRemoteDCsForLocalConsistencyLevel) {
              builder = builder.withLoadBalancingPolicy(
                      DCAwareRoundRobinPolicy.builder().withLocalDc(dataCenter)
                              .allowRemoteDCsForLocalConsistencyLevel().build());
            } else {
              builder = builder.withLoadBalancingPolicy(
                      DCAwareRoundRobinPolicy.builder().withLocalDc(dataCenter).build());
            }
          } else {
            if (allowRemoteDCsForLocalConsistencyLevel) {
              builder = builder.withLoadBalancingPolicy(
                      (DCAwareRoundRobinPolicy.builder().allowRemoteDCsForLocalConsistencyLevel().build()));
            } else {
              builder = builder.withLoadBalancingPolicy((DCAwareRoundRobinPolicy.builder().build()));
            }
          }
          break;
        }
        case "TokenAwareRoundRobinPolicy":
          builder = builder.withLoadBalancingPolicy(new TokenAwarePolicy(new RoundRobinPolicy()));
          break;
        case "TokenAwareDCAwareRoundRobinPolicy": {
          String dataCenter = properties.getProperty(CassandraStoreParameters.DATA_CENTER);
          boolean allowRemoteDCsForLocalConsistencyLevel = Boolean.parseBoolean(
                  properties.getProperty(CassandraStoreParameters.ALLOW_REMOTE_DCS_FOR_LOCAL_CONSISTENCY_LEVEL));
          if (dataCenter != null && !dataCenter.isEmpty()) {
            if (allowRemoteDCsForLocalConsistencyLevel) {
              builder = builder.withLoadBalancingPolicy(new TokenAwarePolicy(
                      DCAwareRoundRobinPolicy.builder().withLocalDc(dataCenter)
                              .allowRemoteDCsForLocalConsistencyLevel().build()));
            } else {
              builder = builder.withLoadBalancingPolicy(new TokenAwarePolicy(
                      DCAwareRoundRobinPolicy.builder().withLocalDc(dataCenter).build()));
            }
          } else {
            if (allowRemoteDCsForLocalConsistencyLevel) {
              builder = builder.withLoadBalancingPolicy(new TokenAwarePolicy(
                      DCAwareRoundRobinPolicy.builder().allowRemoteDCsForLocalConsistencyLevel().build()));
            } else {
              builder = builder.withLoadBalancingPolicy(
                      new TokenAwarePolicy(DCAwareRoundRobinPolicy.builder().build()));
            }
          }
          break;
        }
        default:
          LOG.error("Unsupported Cassandra load balancing  policy: {} ", loadBalancingProp);
          break;
      }
    }
    return builder;
  }

  private Cluster.Builder populateCredentials(Properties properties, Cluster.Builder builder) {
    String usernameProp = properties.getProperty(CassandraStoreParameters.USERNAME);
    String passwordProp = properties.getProperty(CassandraStoreParameters.PASSWORD);
    if (usernameProp != null) {
      builder = builder.withCredentials(usernameProp, passwordProp);
    }
    return builder;
  }

  private Cluster.Builder populatePoolingSettings(Properties properties, Cluster.Builder builder) {
    String localCoreConnectionsPerHost = properties.getProperty(CassandraStoreParameters.LOCAL_CORE_CONNECTIONS_PER_HOST);
    String remoteCoreConnectionsPerHost = properties.getProperty(CassandraStoreParameters.REMOTE_CORE_CONNECTIONS_PER_HOST);
    String localMaxConnectionsPerHost = properties.getProperty(CassandraStoreParameters.LOCAL_MAX_CONNECTIONS_PER_HOST);
    String remoteMaxConnectionsPerHost = properties.getProperty(CassandraStoreParameters.REMOTE_MAX_CONNECTIONS_PER_HOST);
    String localNewConnectionThreshold = properties.getProperty(CassandraStoreParameters.LOCAL_NEW_CONNECTION_THRESHOLD);
    String remoteNewConnectionThreshold = properties.getProperty(CassandraStoreParameters.REMOTE_NEW_CONNECTION_THRESHOLD);
    String localMaxRequestsPerConnection = properties.getProperty(CassandraStoreParameters.LOCAL_MAX_REQUESTS_PER_CONNECTION);
    String remoteMaxRequestsPerConnection = properties.getProperty(CassandraStoreParameters.REMOTE_MAX_REQUESTS_PER_CONNECTION);
    PoolingOptions options = new PoolingOptions();
    if (localCoreConnectionsPerHost != null) {
      options.setCoreConnectionsPerHost(HostDistance.LOCAL, Integer.parseInt(localCoreConnectionsPerHost));
    }
    if (remoteCoreConnectionsPerHost != null) {
      options.setCoreConnectionsPerHost(HostDistance.REMOTE, Integer.parseInt(remoteCoreConnectionsPerHost));
    }
    if (localMaxConnectionsPerHost != null) {
      options.setMaxConnectionsPerHost(HostDistance.LOCAL, Integer.parseInt(localMaxConnectionsPerHost));
    }
    if (remoteMaxConnectionsPerHost != null) {
      options.setMaxConnectionsPerHost(HostDistance.REMOTE, Integer.parseInt(remoteMaxConnectionsPerHost));
    }
    if (localNewConnectionThreshold != null) {
      options.setNewConnectionThreshold(HostDistance.LOCAL, Integer.parseInt(localNewConnectionThreshold));
    }
    if (remoteNewConnectionThreshold != null) {
      options.setNewConnectionThreshold(HostDistance.REMOTE, Integer.parseInt(remoteNewConnectionThreshold));
    }
    if (localMaxRequestsPerConnection != null) {
      options.setMaxRequestsPerConnection(HostDistance.LOCAL, Integer.parseInt(localMaxRequestsPerConnection));
    }
    if (remoteMaxRequestsPerConnection != null) {
      options.setMaxRequestsPerConnection(HostDistance.REMOTE, Integer.parseInt(remoteMaxRequestsPerConnection));
    }
    builder = builder.withPoolingOptions(options);
    return builder;
  }

  private Cluster.Builder populateQueryOptions(Properties properties, Cluster.Builder builder) {
    String consistencyLevelProp = properties.getProperty(CassandraStoreParameters.CONSISTENCY_LEVEL);
    String serialConsistencyLevelProp = properties.getProperty(CassandraStoreParameters.SERIAL_CONSISTENCY_LEVEL);
    String fetchSize = properties.getProperty(CassandraStoreParameters.FETCH_SIZE);
    QueryOptions options = new QueryOptions();
    if (consistencyLevelProp != null) {
      options.setConsistencyLevel(ConsistencyLevel.valueOf(consistencyLevelProp));
    }
    if (serialConsistencyLevelProp != null) {
      options.setSerialConsistencyLevel(ConsistencyLevel.valueOf(serialConsistencyLevelProp));
    }
    if (fetchSize != null) {
      options.setFetchSize(Integer.parseInt(fetchSize));
    }
    return builder.withQueryOptions(options);
  }

  private Cluster.Builder populateReconnectPolicy(Properties properties, Cluster.Builder builder) {
    String reconnectionPolicy = properties.getProperty(CassandraStoreParameters.RECONNECTION_POLICY);
    if (reconnectionPolicy != null) {
      switch (reconnectionPolicy) {
        case "ConstantReconnectionPolicy": {
          String constantReconnectionPolicyDelay = properties.getProperty(CassandraStoreParameters.CONSTANT_RECONNECTION_POLICY_DELAY);
          ConstantReconnectionPolicy policy = new ConstantReconnectionPolicy(Long.parseLong(constantReconnectionPolicyDelay));
          builder = builder.withReconnectionPolicy(policy);
          break;
        }
        case "ExponentialReconnectionPolicy": {
          String exponentialReconnectionPolicyBaseDelay = properties.getProperty(CassandraStoreParameters.EXPONENTIAL_RECONNECTION_POLICY_BASE_DELAY);
          String exponentialReconnectionPolicyMaxDelay = properties.getProperty(CassandraStoreParameters.EXPONENTIAL_RECONNECTION_POLICY_MAX_DELAY);

          ExponentialReconnectionPolicy policy = new ExponentialReconnectionPolicy(Long.parseLong(exponentialReconnectionPolicyBaseDelay),
                  Long.parseLong(exponentialReconnectionPolicyMaxDelay));
          builder = builder.withReconnectionPolicy(policy);
          break;
        }
        default:
          LOG.error("Unsupported reconnection policy : {} ", reconnectionPolicy);
      }
    }
    return builder;
  }

  private Cluster.Builder populateRetrytPolicy(Properties properties, Cluster.Builder builder) {
    String retryPolicy = properties.getProperty(CassandraStoreParameters.RETRY_POLICY);
    if (retryPolicy != null) {
      switch (retryPolicy) {
        case "DefaultRetryPolicy":
          builder = builder.withRetryPolicy(DefaultRetryPolicy.INSTANCE);
          break;
        case "DowngradingConsistencyRetryPolicy":
          builder = builder.withRetryPolicy(DowngradingConsistencyRetryPolicy.INSTANCE);
          break;
        case "FallthroughRetryPolicy":
          builder = builder.withRetryPolicy(FallthroughRetryPolicy.INSTANCE);
          break;
        case "LoggingDefaultRetryPolicy":
          builder = builder.withRetryPolicy(new LoggingRetryPolicy(DefaultRetryPolicy.INSTANCE));
          break;
        case "LoggingDowngradingConsistencyRetryPolicy":
          builder = builder.withRetryPolicy(new LoggingRetryPolicy(DowngradingConsistencyRetryPolicy.INSTANCE));
          break;
        case "LoggingFallthroughRetryPolicy":
          builder = builder.withRetryPolicy(new LoggingRetryPolicy(FallthroughRetryPolicy.INSTANCE));
          break;
        default:
          LOG.error("Unsupported retry policy : {} ", retryPolicy);
          break;
      }
    }
    return builder;
  }

  private Cluster.Builder populateSocketOptions(Properties properties, Cluster.Builder builder) {
    String connectionTimeoutMillisProp = properties.getProperty(CassandraStoreParameters.CONNECTION_TIMEOUT_MILLIS);
    String keepAliveProp = properties.getProperty(CassandraStoreParameters.KEEP_ALIVE);
    String readTimeoutMillisProp = properties.getProperty(CassandraStoreParameters.READ_TIMEOUT_MILLIS);
    String receiveBufferSizeProp = properties.getProperty(CassandraStoreParameters.RECEIVER_BUFFER_SIZE);
    String reuseAddress = properties.getProperty(CassandraStoreParameters.REUSE_ADDRESS);
    String sendBufferSize = properties.getProperty(CassandraStoreParameters.SEND_BUFFER_SIZE);
    String soLinger = properties.getProperty(CassandraStoreParameters.SO_LINGER);
    String tcpNoDelay = properties.getProperty(CassandraStoreParameters.TCP_NODELAY);
    SocketOptions options = new SocketOptions();
    if (connectionTimeoutMillisProp != null) {
      options.setConnectTimeoutMillis(Integer.parseInt(connectionTimeoutMillisProp));
    }
    if (keepAliveProp != null) {
      options.setKeepAlive(Boolean.parseBoolean(keepAliveProp));
    }
    if (readTimeoutMillisProp != null) {
      options.setReadTimeoutMillis(Integer.parseInt(readTimeoutMillisProp));
    }
    if (receiveBufferSizeProp != null) {
      options.setReceiveBufferSize(Integer.parseInt(receiveBufferSizeProp));
    }
    if (reuseAddress != null) {
      options.setReuseAddress(Boolean.parseBoolean(reuseAddress));
    }
    if (sendBufferSize != null) {
      options.setSendBufferSize(Integer.parseInt(sendBufferSize));
    }
    if (soLinger != null) {
      options.setSoLinger(Integer.parseInt(soLinger));
    }
    if (tcpNoDelay != null) {
      options.setTcpNoDelay(Boolean.parseBoolean(tcpNoDelay));
    }
    return builder.withSocketOptions(options);
  }


  private List<String> readCustomCodec(Properties properties) throws JDOMException, IOException {
    String filename = properties.getProperty(CassandraStoreParameters.CUSTOM_CODEC_FILE);
    if (filename != null) {
      List<String> codecs = new ArrayList<>();
      SAXBuilder builder = new SAXBuilder();
      Document doc = builder.build(getClass().getClassLoader().getResourceAsStream(filename));
      List<Element> codecElementList = doc.getRootElement().getChildren("codec");
      for (Element codec : codecElementList) {
        codecs.add(codec.getValue());
      }
      return codecs;
    }
    return null;
  }


  public void close() {
    this.session.close();
    this.cluster.close();
  }

  private void registerCustomCodecs(List<String> codecs) throws Exception {
    for (String codec : codecs) {
      this.cluster.getConfiguration().getCodecRegistry().register((TypeCodec<?>) Class.forName(codec).newInstance());
    }
  }

}