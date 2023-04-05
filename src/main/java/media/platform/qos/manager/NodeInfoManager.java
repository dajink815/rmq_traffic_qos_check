package media.platform.qos.manager;

import media.platform.qos.info.NodeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author dajin kim
 */
public class NodeInfoManager {
    private static final Logger log = LoggerFactory.getLogger(NodeInfoManager.class);

    // NodeKey (QueueName) : NodeInfo
    protected static final ConcurrentHashMap<String, NodeInfo> trafficMap = new ConcurrentHashMap<>();

    private static final AtomicBoolean checkFlag = new AtomicBoolean(true);
    private static final int MAX_NAME_LENGTH = 10;
    private int maxLength = 0;

    protected NodeInfoManager() {
        // nothing
    }

    /**
     * @fn getTrafficMapIds
     * @brief NodeInfo 가 생성된 노드의 큐 이름 반환 함수
     * @return List<String> : trafficMap 의 Key List
     * */
    protected List<String> getTrafficMapIds() {
        synchronized (trafficMap) {
            return new ArrayList<>(trafficMap.keySet());
        }
    }

    protected ConcurrentMap<String, NodeInfo> getTrafficMap() {
        return trafficMap;
    }

    protected String getFormatName(NodeInfo nodeInfo) {
        if (nodeInfo == null) return null;

        if (checkFlag.get() || maxLength <= 0) {
            getTrafficMapIds().forEach(nodeName -> {
                                if (nodeName.length() > maxLength) {
                                    maxLength = nodeName.length();
                                }
                            });

            checkFlag.set(false);
        }

        String format = "%-" + maxLength + "s";
        return String.format(format, nodeInfo.getTargetQname());
    }

    /**
     * @fn createTrafficInfo
     * @brief NodeInfo 생성 함수
     * @param targetQueue : peer Node QueueName
     * @return NodeInfo
     * */
    protected NodeInfo createNodeInfo(String targetQueue) {
        if (trafficMap.containsKey(targetQueue)) {
            log.error("[QOS] NodeInfo [{}] ALREADY EXIST", targetQueue);
            return null;
        }

        if (targetQueue.length() > MAX_NAME_LENGTH) {
            log.warn("[QOS] check NodeInfo targetQueue Name Length : {}", targetQueue.length());
        }

        NodeInfo nodeInfo = new NodeInfo(targetQueue);
        nodeInfo.resetStats();
        trafficMap.putIfAbsent(targetQueue, nodeInfo);
        checkFlag.set(true);
        log.warn("[QOS] NodeInfo [{}] is Created", targetQueue);
        return nodeInfo;
    }

    /**
     * @fn getTrafficInfo
     * @brief NodeInfo 조회 함수
     * @param targetQueue : peer Node QueueName
     * @return NodeInfo
     * */
    protected NodeInfo getNodeInfo(String targetQueue) {
        if (targetQueue == null) {
            log.warn("[QOS] Target Queue Name is Null");
            return null;
        }
        NodeInfo nodeInfo = trafficMap.get(targetQueue);
/*        if (nodeInfo == null)
            log.warn("[QOS] NodeInfo [{}] is Null", targetQueue);*/
        return nodeInfo;
    }

}
