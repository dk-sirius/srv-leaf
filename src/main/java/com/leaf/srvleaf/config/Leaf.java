package com.leaf.srvleaf.config;

import com.leaf.leafcore.SnowflakeIdGenerator;
import com.leaf.leafcore.config.GeneratorProperties;
import com.leaf.leafcore.config.ZookeeperProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class Leaf {

    @Resource
    LeafProperties properties;

    @Bean
    public SnowflakeIdGenerator generator() {
        GeneratorProperties p = new GeneratorProperties();
        p.setStartTimestamp(Long.parseLong(properties.getStartTimestamp()));
        ZookeeperProperties zookeeperProperties = new ZookeeperProperties();
        zookeeperProperties.setPort(Integer.parseInt(properties.getServicePort()));
        zookeeperProperties.setServiceName(properties.getServiceName());
        zookeeperProperties.setLocalNodeCachePath(properties.getLocalNodeCache());
        zookeeperProperties.setZkConnectingCluster(properties.getZkConnectingCluster());
        p.setZookeeperProperties(zookeeperProperties);
        return new SnowflakeIdGenerator(p);
    }
}
