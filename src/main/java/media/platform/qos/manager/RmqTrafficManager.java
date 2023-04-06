package media.platform.qos.manager;

import media.platform.qos.common.*;
import media.platform.qos.info.MessageInfo;
import media.platform.qos.info.NodeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.*;

import static media.platform.qos.common.StatusType.*;
/**
 * 메시지 QOS 체크
 *
 * @author dajin kim
 */
public class RmqTrafficManager extends NodeInfoManager implements RmqTrafficInterface {
    private static final Logger log = LoggerFactory.getLogger(RmqTrafficManager.class);
    private static RmqTrafficManager rmqTrafficManager = null;

    private final List<String> excludedMsgType = new ArrayList<>();
    private final ScheduledExecutorService scheduleService;
    private final QosRunnable qosRunnable;

    private static final long DEFAULT_TIMER = 5000;     // [Unit: mSec]
    private static final long DEFAULT_MSG_GAP_LIMIT = 2000;
    private static final long TASK_INTERVAL = 1000;     // [Unit: mSec]

    // HA Status
    private StatusType haStatus = DOWN;
    private boolean recvCheckDelay;
    private long haTime;

    private long timer;
    private long msgGapLimit;

    private RmqTrafficManager() {
        this.scheduleService = Executors.newScheduledThreadPool(1);
        this.qosRunnable = new QosRunnable();
    }

    public static RmqTrafficManager getInstance() {
        if (rmqTrafficManager == null)
            rmqTrafficManager = new RmqTrafficManager();

        return rmqTrafficManager;
    }

    @Override
    public void start(long timer, long msgGapLimit) {
        // todo 버전
        log.warn("[QOS] RMQ_TRAFFIC_MANAGER START, TIMER:{}, MSG_GAP_LIMIT:{} ({})", timer, msgGapLimit, ServiceDefine.VERSION.getValue());
        if (timer <= 0) {
            log.warn("[QOS] NEED TO CHECK TIMER : {} (mSec)", timer);
            timer = DEFAULT_TIMER;
        }
        this.timer = timer;
        if (msgGapLimit <= 0 || timer <= msgGapLimit) {
            log.warn("[QOS] NEED TO CHECK MSG_GAP_LIMIT : {} (mSec)", msgGapLimit);
            msgGapLimit = DEFAULT_MSG_GAP_LIMIT;
        }
        this.msgGapLimit = msgGapLimit;

        // HA
        this.recvCheckDelay = true;
        setHaTime();

        scheduleService.scheduleAtFixedRate(qosRunnable, TASK_INTERVAL - System.currentTimeMillis() % TASK_INTERVAL, TASK_INTERVAL, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        if (scheduleService != null) {
            log.warn("[QOS] RMQ_TRAFFIC_MANAGER STOP ({})", ServiceDefine.VERSION.getValue());
            scheduleService.shutdown();
        }
    }

    @Override
    public void setHaStatus(int status) {
        StatusType curStatus = StatusType.getTypeEnum(status);
        if (curStatus == null) {
            log.info("[QOS] Check HaStatus Value: {}", status);
            return;
        }

        // HA 상태 변경
        if (haStatus != curStatus) {
            log.warn("[QOS] RMQ_TRAFFIC_MANAGER STATUS CHANGED {} -> {}", haStatus, curStatus);

            // ACTIVE (Standby/Down -> Active)
            if (ACTIVE.equals(curStatus)) {
                // Res 수신시 TransactionId 없어도 에러로그 delay
                recvCheckDelay = true;
                setHaTime();
            }
            // STANDBY (Active/Down -> Standby)
            // 현재 haStatus 상태가 DOWN, STANDALONE 일때는 고려 X
            else if (STANDBY.equals(curStatus)){
                // TransactionId 모두 삭제
                clearTIdMap();
            }

            haStatus = curStatus;
        }
    }

    @Override
    public long getTimer() {
        return timer;
    }

    @Override
    public long getMsgGapLimit() {
        return msgGapLimit;
    }

    private void setHaTime() {
        haTime = System.currentTimeMillis();
    }

    private void clearTIdMap() {

        this.getTrafficMapIds().stream()
                .map(this::getNodeInfo).filter(Objects::nonNull)
                .forEach(nodeInfo -> {
                    int prevMapSize = nodeInfo.getMsgMapSize();
                    nodeInfo.clearTransactionMap();
                    int mapSize = nodeInfo.getMsgMapSize();
                    if (prevMapSize != mapSize) {
                        log.debug("[QOS] [{}] Clear Transaction Map {} -> {}", nodeInfo.getTargetQname(), prevMapSize, mapSize);
                    }
                });
    }

    @Override
    public void addExcludedRmqType(String msgType) {
        excludedMsgType.add(msgType);
    }

    @Override
    public List<String> getExcludedRmqType() {
        return excludedMsgType;
    }

    @Override
    public void rmqSendTimeCheck(String targetQueue, String tId, String msgType) {
        NodeInfo nodeInfo = getNodeInfo(targetQueue);
        if (nodeInfo == null) {
            nodeInfo = createNodeInfo(targetQueue);
        }

        // 해당 노드에서 보내기 시작한 REQ 메시지만 응답시간 체크, Standby 상태는 skip
        if (excludedMsgType.contains(msgType) || tId == null
                || nodeInfo == null || !StringUtil.isReqType(msgType)) {
            if (nodeInfo == null && StringUtil.isReqType(msgType))
                log.warn("[QOS] [{}] Fail to Record Send Time - Target Node Info Null ({}:{})", targetQueue, msgType, tId);
            return;
        }

        try {
            if (nodeInfo.addMsgInfo(tId, msgType) == null) {
                // sendCnt 누적
                nodeInfo.increaseSendMsgCnt();
            } else {
                log.info("[QOS] [{}] Fail to Record Send Time - Already Recorded ({}:{})", targetQueue, msgType, tId);
            }
        } catch (Exception e) {
            log.error("[QOS] [{}] rmqSendTimeCheck.Exception ({}:{})", targetQueue, msgType, tId, e);

        }
    }

    @Override
    public void rmqRecvTimeCheck(String msgFrom, String tId, String msgType) {
        NodeInfo nodeInfo = getNodeInfo(msgFrom);

        // 해당 노드에서 보낸 후 응답받은 메시지만 응답시간 체크
        if (excludedMsgType.contains(msgType) || tId == null
                || nodeInfo == null || !StringUtil.isResType(msgType)) {
            if (nodeInfo == null && StringUtil.isResType(msgType))
                log.warn("[QOS] [{}] Fail to Record Receive Time - Target Node Info Null ({}:{})", msgFrom, msgType, tId);
            return;
        }

        // delete
        MessageInfo messageInfo = nodeInfo.delMsgInfo(tId);
        if (messageInfo != null) {
            try {
                // min, max
                long gap = System.currentTimeMillis() - messageInfo.getSendTime();
                nodeInfo.checkMinTime(gap);
                nodeInfo.checkMaxTime(gap);

                // recvCnt, totalTime 누적
                nodeInfo.increaseRecvMsgCnt();
                nodeInfo.addTotalTime(gap);

                // string format
                String gapStr = String.format("%3d", gap);
                String recvCntStr = String.format("%3d", nodeInfo.getRecvMsgCnt());
                String totalStr = String.format("%4d", nodeInfo.getTotalTime());

                if (0 < this.msgGapLimit && this.msgGapLimit <= gap) {
                    log.warn("[QOS] [{}] GAP: {}, Recv: {}, Total: {} - Over GapLimit {} ({}:{})",
                            msgFrom, gapStr, recvCntStr, totalStr, this.msgGapLimit, msgType, tId);
                } else {
                    log.debug("[QOS] [{}] GAP: {}, Recv: {}, Total: {} ({})",
                            msgFrom, gapStr, recvCntStr, totalStr, msgType);
                }
            } catch (Exception e) {
                log.error("[QOS] [{}] rmqRecvTimeCheck.Exception ({}:{})", msgFrom, msgType, tId, e);
            }
        } else {
            if (!recvCheckDelay) {
                log.warn("[QOS] [{}] Fail to Record Receive Time - Message Info Null ({}:{}) ", msgFrom, msgType, tId);
            } else {
                log.debug("[QOS] [{}] Fail to Record Receive Time - Just Started. ({}:{})", msgFrom, msgType, tId);
            }
        }
    }

    /**
     * @fn checkTrafficQos
     * @brief trafficMap 의 NodeInfo 조회 & 체크 (TASK_INTERVAL 간격으로 호출)
     * */
    private void checkTrafficQos() {

        // 에러로그 delay Flag - 현재 Active 일때 조건 추가 필요?
        if (recvCheckDelay && haTime + 5000 < System.currentTimeMillis()) {
            String haTimeStr = DateFormatUtil.formatYmdHmsS(haTime);
            log.info("[QOS] DELAY_FLAG {} -> {}, HA_TIME:{}", recvCheckDelay, false, haTimeStr);
            recvCheckDelay = false;
        }

        try {
            // 1초에 한 번씩 실행
            getTrafficMapIds().stream()
                    .map(this::getNodeInfo).filter(Objects::nonNull)
                    .forEach(nodeInfo -> {

                        // timeout 체크
                        checkTimeout(nodeInfo);

                        // 5초 단위로 QOS 로그
                        int curSecond = Calendar.getInstance().get(Calendar.SECOND);
                        if (curSecond % 5 == 0) {

                            // QOS 출력 조건
                            // 1. 5초간 송수신한 RMQ 메시지가 있을 경우,
                            // 2. 타임아웃 처리된 트랜잭션 존재할 경우
                            // 3. 맵에 잔여 메시지가 있는 경우
                            int sendMsgCnt = nodeInfo.getSendMsgCnt();
                            int recvMsgCnt = nodeInfo.getRecvMsgCnt();

                            int timeoutCnt = nodeInfo.getTimeoutCnt();
                            int msgMapSize = nodeInfo.getMsgMapSize();

                            if (0 < (sendMsgCnt + recvMsgCnt) || 0 < timeoutCnt || 0 < msgMapSize) {

                                double avgTime = nodeInfo.checkAvgTime();
                                String strAvgTime = String.format("%.2f", avgTime);

                                long minTime = nodeInfo.getMinTime();
                                long maxTime = nodeInfo.getMaxTime();
                                long totalTime = nodeInfo.getTotalTime();

                                log.info("[QOS] [{}] Avg:{}(MinMax:{}/{} T:{}), S:{}, R:{}, Timeout:{}, RemainMsg:{}",
                                        nodeInfo.getTargetQname(), strAvgTime, minTime, maxTime, totalTime,
                                        sendMsgCnt, recvMsgCnt, timeoutCnt, msgMapSize);

                                // Reset Stat
                                nodeInfo.resetStats();

                            } else if (curSecond % 30 == 0) {
                                log.debug("[QOS] [{}] No Messages sent or received.", nodeInfo.getTargetQname());
                            }

                        }

                    });
        } catch (Exception e) {
            log.error("[QOS] checkTrafficQos.Exception ", e);
        }
    }

    /**
     * @fn checkTimeout
     * @brief NodeInfo 의 transactionMap 타임아웃 메시지 조회 및 제거
     * @param nodeInfo : 대상 노드의 NodeInfo
     * @return int : 타임아웃된 메시지 개수
     * */
    private void checkTimeout(NodeInfo nodeInfo) {

        try {
            nodeInfo.getTransactionIds().stream()
                    .filter(Objects::nonNull)
                    .filter(tId -> nodeInfo.isMsgTimeout(tId, this.timer))
                    .forEach(tId -> {
                        MessageInfo messageInfo = nodeInfo.delMsgInfo(tId);

                        if (messageInfo != null) {
                            nodeInfo.increaseTimeoutCnt();

                            String msgType = messageInfo.getMsgType();
                            String sendTimeStr = DateFormatUtil.formatYmdHmsS(messageInfo.getSendTime());
                            int timeoutCnt = nodeInfo.getTimeoutCnt();
                            int mapSize = nodeInfo.getMsgMapSize();

                            log.warn("[QOS] [{}] TIMEOUT {} [{}] ({}, Timeout:{}, RemainMsg:{}, Timer:{}) ",
                                    nodeInfo.getTargetQname(), msgType, tId, sendTimeStr,
                                    timeoutCnt, mapSize, this.timer);
                        }
                    });
        } catch (Exception e) {
            log.error("[QOS] [{}] checkTimeout.Exception ", nodeInfo.getTargetQname(), e);
        }

    }

    static class QosRunnable implements Runnable {
        @Override
        public void run() {
            RmqTrafficManager rmqTrafficManager = RmqTrafficManager.getInstance();
            rmqTrafficManager.checkTrafficQos();

        }
    }
}
