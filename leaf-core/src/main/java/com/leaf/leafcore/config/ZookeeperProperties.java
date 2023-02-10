package com.leaf.leafcore.config;

import lombok.Data;

@Data
public class ZookeeperProperties {
    private Integer port;

    private String serviceName;

    private String zkConnectingCluster;

    private String localNodeCachePath;
}
