package cn.netdiscovery.core.watch.etcd;

import cn.netdiscovery.core.config.Configuration;
import cn.netdiscovery.core.config.Constant;
import cn.netdiscovery.core.domain.SpiderEngineState;
import com.safframework.tony.common.utils.Preconditions;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;


/**
 * Created by tony on 2019-06-09.
 */
@Slf4j
public class EtcdManager {

    private Client client;
    private String prefixPath;
    private Map<String, SpiderEngineState> stateMap = new HashMap<>(); // 存储各个节点的状态

    public EtcdManager() {

        this(Configuration.getConfig("spiderEngine.registry.etcd.etcdStr"),Configuration.getConfig("spiderEngine.registry.etcd.etcdPath"));
    }

    public EtcdManager(String etcdStr,String etcdPath) {

        if (Preconditions.isNotBlank(etcdStr)) {
            client = Client.builder().endpoints(etcdStr).build();
        }

        if (Preconditions.isBlank(etcdPath)) {
            prefixPath = Constant.DEFAULT_REGISTRY_PATH;
        } else {
            prefixPath = etcdPath;
        }
    }

    public void process() {

        if (client!=null) {

            CountDownLatch latch = new CountDownLatch(Integer.MAX_VALUE);
            Watch.Watcher watcher = null;

            try {
                ByteSequence watchKey = ByteSequence.from("/"+prefixPath, StandardCharsets.UTF_8);
                WatchOption watchOpts = WatchOption.newBuilder().withRevision(0).withPrefix(ByteSequence.from(("/"+prefixPath).getBytes())).build();

                watcher = client.getWatchClient().watch(watchKey, watchOpts, response -> {
                            for (WatchEvent event : response.getEvents()) {

                                String key = Optional.ofNullable(event.getKeyValue().getKey()).map(bs -> bs.toString(StandardCharsets.UTF_8)).orElse("");
                                String value = Optional.ofNullable(event.getKeyValue().getValue()).map(bs -> bs.toString(StandardCharsets.UTF_8)).orElse("");

                                log.info("type={}, key={}, value={}", event.getEventType().toString(),key,value);

                                switch (event.getEventType()) {

                                    case PUT: {
                                        log.info("新增 SpiderEngine 节点{}", key.replace("/"+prefixPath+"/",""));

                                        break;
                                    }

                                    case DELETE: {
                                        log.info("SpiderEngine 节点【{}】下线了！", key.replace("/"+prefixPath+"/",""));

                                        break;
                                    }

                                    default:
                                        break;
                                }
                            }

                            latch.countDown();
                        }
                );

                latch.await();
            } catch (Exception e) {
                if (watcher != null) {
                    watcher.close();
                }
            }
        }
    }

}
