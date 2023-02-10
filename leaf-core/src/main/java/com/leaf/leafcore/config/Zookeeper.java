package com.leaf.leafcore.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryUntilElapsed;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Getter
public class Zookeeper {

    private final static String ROOT = "/leaf/snowflake/";

    private final String PRE_PATH;

    private final String PERSISTENCE_PATH;

    private final String WORK_ID_CACHE_PATH;

    private final String LISTEN_ADDRESS;

    private final String IP;

    private final int PORT;

    private final String CONNECTING_CLUSTER;

    /**
     * leaf zk持久化节点 : PERSISTENCE_PATH/IP:PORT-0000000000
     */
    private String addressNode;

    private int workerId;

    private long lastUpdateTime;

    private final CuratorFramework curator;

    public Zookeeper(ZookeeperProperties properties, String currentIp) {
        this.CONNECTING_CLUSTER = properties.getZkConnectingCluster();
        this.IP = currentIp;
        this.PORT = properties.getPort();
        this.LISTEN_ADDRESS = String.format("%s:%d", this.IP, this.PORT);
        this.curator = curatorFramework(properties.getZkConnectingCluster());
        this.PRE_PATH = ROOT + properties.getServiceName();
        this.PERSISTENCE_PATH = this.PRE_PATH + "/persistence";
        this.WORK_ID_CACHE_PATH = String.format("%s%s%s%s", properties.getLocalNodeCachePath(),
                File.separator, properties.getServiceName(), "/conf/%d/work_id.properties");
    }

    private CuratorFramework curatorFramework(String connectCluster) {
        return CuratorFrameworkFactory.builder().connectString(connectCluster)
                .retryPolicy(new RetryUntilElapsed(1000, 4))
                .connectionTimeoutMs(10000)
                .sessionTimeoutMs(6000)
                .build();
    }

    public boolean init() {
        curator.start();
        try {
            Stat stat = curator.checkExists().forPath(PERSISTENCE_PATH);
            if (null == stat) {
                // 创建节点
                addressNode = createNode();
                // worker id 默认为0
                mountLocalWordId(workerId);
                // 定时上报本机时间给持久化节点
                scheduledPersistenceNode(addressNode);
                return true;
            } else {
                // {ip:port = 00001}
                Map<String, Integer> nodeMap = new HashMap<>();
                // {ip:port->(ip:port-00001)}
                Map<String, String> realNode = new HashMap<>();
                List<String> keys = curator.getChildren().forPath(PERSISTENCE_PATH);
                for (String key : keys) {
                    String[] nodeKey = key.split("-");
                    realNode.put(nodeKey[0], key);
                    if (nodeKey.length > 1) {
                        nodeMap.put(nodeKey[0], Integer.parseInt(nodeKey[1]));
                    }
                }
                Integer _workerId = nodeMap.get(LISTEN_ADDRESS);
                if (null != _workerId) {
                    addressNode = PERSISTENCE_PATH + File.separator + realNode.get(LISTEN_ADDRESS);
                    workerId = _workerId;
                    if (!checkInitTimestamp(addressNode)) {
                        throw new Exception("Init timestamp check error, current server time lt persistence node timestamp !");
                    }
                    // 创建临时节点
                    scheduledPersistenceNode(addressNode);
                    mountLocalWordId(_workerId);
                    if (log.isInfoEnabled()) {
                        log.info("[Old NODE]find forever node have this endpoint ip-{} servicePort-{} worker id-{} child node and start SUCCESS !", IP, PORT, _workerId);
                    }
                } else {
                    String newNode = createNode();
                    addressNode = newNode;
                    String[] nodeKey = newNode.split("-");
                    workerId = Integer.parseInt(nodeKey[1]);
                    scheduledPersistenceNode(addressNode);
                    mountLocalWordId(workerId);
                    if (log.isInfoEnabled()) {
                        log.info("[New NODE]can not find node on forever node that endpoint ip-{} servicePort-{} worker id-{},create own node on forever node and start SUCCESS !", IP, PORT, workerId);
                    }
                }

            }
        } catch (Exception e) {
            log.error("Capture an exception while initializing zookeeper node ", e);
            try {
                //初始化zk节点失败后，使用本地缓存的节点
                Properties properties = new Properties();
                properties.load(new FileInputStream(new File(String.format(WORK_ID_CACHE_PATH, PORT))));
                workerId = Integer.parseInt(properties.getProperty("workerId"));
                log.warn("SnowflakeIdGenerator Zookeeper node initialization failed , use local cache node workerId - {}", workerId);
            } catch (IOException ioe) {
                log.error("Capture an exception while reading local cache file ", ioe);
                return false;
            }
        }
        return true;
    }

    private boolean checkInitTimestamp(String addressNode) throws Exception {
        byte[] bytes = curator.getData().forPath(addressNode);
        Endpoint endpoint = parserData(new String(bytes));
        // 当前时间不能小于服务器最后一次上报时间
        return endpoint.getTimestamp() < System.currentTimeMillis();
    }

    private Endpoint parserData(String data) throws JsonProcessingException {
        return new ObjectMapper().readValue(data, Endpoint.class);
    }


    private String createNode() throws Exception {
        try {
            return curator.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.PERSISTENT)
                    .forPath(PERSISTENCE_PATH + File.separator + this.LISTEN_ADDRESS + "-", buildData());
        } catch (Exception e) {
            log.error(String.format("createNode() happen with %s", e));
            throw e;
        }
    }

    private byte[] buildData() throws JsonProcessingException {
        Endpoint endpoint = new Endpoint(this.IP, this.PORT, System.currentTimeMillis());
        return new ObjectMapper().writeValueAsString(endpoint).getBytes(StandardCharsets.UTF_8);
    }

    private void scheduledPersistenceNode(String node) {
        // 每3s 上报数据
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
                r -> {
                    Thread thread = new Thread(r, "snowflake-generator-node-time");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setMaximumPoolSize(1);
        executor.scheduleWithFixedDelay(() -> syncTimestamp(node), 1L, 3L, TimeUnit.SECONDS);
    }

    private void syncTimestamp(String path) {
        if (System.currentTimeMillis() >= this.lastUpdateTime) {
            try {
                curator.setData().forPath(path, buildData());
                this.lastUpdateTime = System.currentTimeMillis();
            } catch (Exception e) {
                log.error("Capture an exception while update node data . node path is {},error is {}", path, e);
            }
        }
    }

    private void mountLocalWordId(int workId) {
        File conf = new File(String.format(WORK_ID_CACHE_PATH, this.PORT));
        boolean flag = true;
        if (!conf.exists()) {
            // 如果文件不存在，
            File parent = conf.getParentFile();
            flag = parent.exists();
            if (!flag) {
                flag = parent.mkdirs();
            }
        }
        if (flag) {
            try (OutputStream stream = open(conf)) {
                stream.write(String.format("workerId=%d", workId).getBytes(StandardCharsets.UTF_8));
                if (log.isInfoEnabled()) {
                    log.info("mount local file cache workerId is:{}", workId);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private FileOutputStream open(File file) throws FileNotFoundException {
        return new FileOutputStream(file, false);
    }

    @Data
    @AllArgsConstructor
    static class Endpoint {
        private String ip;
        private int port;
        private long timestamp;
    }

}
