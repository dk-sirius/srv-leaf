package com.leaf.srvleaf.config;


import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class LeafProperties {
    @Value("${server.port}")
    private String servicePort;

    @Value("${spring.application.name}")
    private String serviceName;

    @Value("${snowflake.leaf.start-time}")
    private String startTimestamp;

    @Value("${snowflake.leaf.zk.cluster}")
    private String zkConnectingCluster;

    @Value("${snowflake.leaf.zk.cache}")
    private String localNodeCache;

}
