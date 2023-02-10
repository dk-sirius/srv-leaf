package com.leaf.leafcore.config;

import lombok.Data;

@Data
public class GeneratorProperties {
    private Long startTimestamp;

    private ZookeeperProperties zookeeperProperties;
}
