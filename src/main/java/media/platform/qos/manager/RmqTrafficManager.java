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
public class RmqTrafficManager extends NodeInfoManager {
    private static final Logger log = LoggerFactory.getLogger(RmqTrafficManager.class);
    private static RmqTrafficManager rmqTrafficManager = null;

    private final List<String> excludedMsgType = new ArrayList<>();
    private final ScheduledExecutorService scheduleService;
    private final QosRunnable qosRunnable;

    private static final long DEFAULT_TIMER = 5000;     // [Unit: mSec]
    private static final long TASK_INTERVAL = 1000;     // [Unit: mSec]
    private static final String REQ = "REQ";
    private static final String RES = "RES";

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

    /**
     * @fn start
     * @brief RmqTrafficManager 시작, TASK_INTERVAL 주기로 QOS 스케쥴링
     * @param timer: 메시지 timeout 처리 시간
     * @param msgGapLimit: 메시지 제한 응답 시간 (Unit:ms)
     */
    public void start(long timer, long msgGapLimit) {
        log.warn("RMQ_TRAFFIC_MANAGER START, TIMER:{}, MSG_GAP_LIMIT:{} ({})", timer, msgGapLimit, ServiceDefine.VERSION.getValue());
        if (timer <= 0) {
            log.warn("NEED TO CHECK TIMER : {} (mSec)", timer);
            timer = DEFAULT_TIMER;
        }
        this.timer = timer;
        if (timer <= msgGapLimit) {
            log.warn("NEED TO CHECK MSG_GAP_LIMIT : {} (mSec)", msgGapLimit);
        }
        this.msgGapLimit = msgGapLimit;

        // HA
        this.recvCheckDelay = true;
        setHaTime();

        scheduleService.scheduleAtFixedRate(qosRunnable, TASK_INTERVAL - System.currentTimeMillis() % TASK_INTERVAL, TASK_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (scheduleService != null) {
            log.warn("RMQ_TRAFFIC_MANAGER STOP ({})", ServiceDefine.VERSION.getValue());
            scheduleService.shutdown();
        }
    }

    public void setHaStatus(int status) {
        StatusType curStatus = StatusType.getTypeEnum(status);
        if (curStatus == null) {
            log.info("Check HaStatus Value: {}", status);
            return;
        }

        // HA 상태 변경
        if (haStatus != curStatus) {
            log.warn("RMQ_TRAFFIC_MANAGER STATUS CHANGED {} -> {}", haStatus, curStatus);

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

    public long getTimer() {
        return timer;
    }

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
                    int prevMapSize = nodeInfo.getTransactionMapSize();
                    nodeInfo.clearTransactionMap();
                    int mapSize = nodeInfo.getTransactionMapSize();
                    if (prevMapSize != mapSize) {
                        log.debug("[{}] Clear Transaction Map {} -> {}", getFormatName(nodeInfo), prevMapSize, mapSize);
                    }
                });
    }

    /**
     * @fn addExcludedRmqType
     * @param msgType : Traffic 계산에 포함되지 않는 Message Type
     * */
    public void addExcludedRmqType(String msgType) {
        excludedMsgType.add(msgType);
    }

    public List<String> getExcludedRmqType() {
        return excludedMsgType;
    }

    /**
     * @fn rmqSendTimeCheck
     * @brief 메시지 전송시 호출하는 함수, 메시지의 tId 로 전송시간 기록
     * @param targetQueue : peer Node QueueName
     * @param tId : Message transactionId
     * @param msgType : Message Type
     * */
    public void rmqSendTimeCheck(String targetQueue, String tId, String msgType) {
        NodeInfo nodeInfo = getNodeInfo(targetQueue);
        if (nodeInfo == null) {
            nodeInfo = createNodeInfo(targetQueue);
        }

        if (excludedMsgType.contains(msgType) || tId == null || nodeInfo == null
                // 해당 노드에서 보내기 시작한 메시지만 응답시간 체크, Standby 상태는 skip
                || !msgType.toUpperCase().contains(REQ)) {

            if (nodeInfo == null && msgType.toUpperCase().contains(REQ))
                log.warn("[{}] Fail to Record Send Time - Target Node Info Null ({})", targetQueue, msgType);
            return;
        }

        if (nodeInfo.addMsgInfo(tId, msgType) == null) {
            // sendCnt 누적
            nodeInfo.increaseSendMsgCnt();
        } else {
            log.info("[{}] Fail to Record Send Time - Already Recorded ({}:{})", getFormatName(nodeInfo), msgType, tId);
        }
    }

    /**
     * @fn rmqRecvTimeCheck
     * @brief 메시지 수신시 호출하는 함수, 메시지의 tId 로 전송시간 조회 후 응답시간 계산
     * @param msgFrom : Message msgFrom (peer Node QueueName)
     * @param tId : Message transactionId
     * @param msgType : Message Type
     * */
    public void rmqRecvTimeCheck(String msgFrom, String tId, String msgType) {
        NodeInfo nodeInfo = getNodeInfo(msgFrom);
        if (excludedMsgType.contains(msgType) || tId == null || nodeInfo == null
                // 해당 노드에서 보낸 후 응답받은 메시지만 응답시간 체크
                || !msgType.toUpperCase().contains(RES)) {

            if (nodeInfo == null && msgType.toUpperCase().contains(RES))
                log.warn("[{}] Fail to Record Receive Time - Target Node Info Null ({})", msgFrom, msgType);
            return;
        }

        // delete
        MessageInfo messageInfo = nodeInfo.delMsgInfo(tId);
        if (messageInfo != null) {
            // min, max
            long sendTime = messageInfo.getSendTime();
            long gap = System.currentTimeMillis() - sendTime;
            CalculateTime.checkMinTime(nodeInfo, gap);
            CalculateTime.checkMaxTime(nodeInfo, gap);

            // recvCnt, totalTime 누적
            nodeInfo.increaseRecvMsgCnt();
            nodeInfo.addTotalTime(gap);

            // string format
            String gapStr = String.format("%3d", gap);
            String recvCntStr = String.format("%3d", nodeInfo.getRecvMsgCnt());
            String totalStr = String.format("%4d", nodeInfo.getTotalTime());

            if (this.msgGapLimit > 0 && this.msgGapLimit <= gap) {
                log.warn("[{}] GAP: {}, Recv: {}, Total: {} - Over GapLimit {} ({}:{})",
                        getFormatName(nodeInfo), gapStr, recvCntStr, totalStr,
                        this.msgGapLimit, msgType, tId);
            } else {
                log.debug("[{}] GAP: {}, Recv: {}, Total: {} ({})",
                        getFormatName(nodeInfo), gapStr, recvCntStr, totalStr, msgType);
            }
        } else {
            if (!recvCheckDelay) {
                log.warn("[{}] Fail to Record Receive Time - Message Info Null ({}:{}) ", getFormatName(nodeInfo), msgType, tId);
            } else {
                log.debug("[{}] Fail to Record Receive Time - Just Started. ({}:{})", getFormatName(nodeInfo), msgType, tId);
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
            log.info("DELAY_FLAG {} -> {}, HA_TIME:{}", recvCheckDelay, false, haTimeStr);
            recvCheckDelay = false;
        }

        // 1초에 한 번씩 실행
        getTrafficMapIds().stream()
                .map(this::getNodeInfo).filter(Objects::nonNull)
                .forEach(nodeInfo -> {

                    // timeout 체크
                    checkTimeout(nodeInfo);

                    // 5초 단위로 QOS 로그
                    int curSecond = Calendar.getInstance().get(Calendar.SECOND);
                    if (curSecond % 5 == 0) {
                        // Average
                        double avgTime = CalculateTime.checkAvgTime(nodeInfo);

                        int timeoutCnt = nodeInfo.getTimeoutCnt();

                        // 5초간 송수신한 RMQ 메시지가 있을 경우, 타임아웃 처리된 트랜잭션 존재할 경우 QOS 출력
                        int transactionCnt = nodeInfo.getSendMsgCnt() + nodeInfo.getRecvMsgCnt();
                        if (transactionCnt > 0 || timeoutCnt > 0 || nodeInfo.getTransactionMapSize() > 0) {
                            log.info("[{}] QOS Avg:{}(MinMax:{}/{} T:{}), S:{}, R:{}, Timeout:{}, RemainMsg:{}",
                                    getFormatName(nodeInfo), String.format("%.2f", avgTime), nodeInfo.getMinTime(), nodeInfo.getMaxTime(),
                                    nodeInfo.getTotalTime(), nodeInfo.getSendMsgCnt(), nodeInfo.getRecvMsgCnt(),
                                    timeoutCnt, nodeInfo.getTransactionMapSize());

                            // Reset Stat
                            nodeInfo.resetStats();
                        }

                    }

                });
    }

    /**
     * @fn checkTimeout
     * @brief NodeInfo 의 transactionMap 타임아웃 메시지 조회 및 제거
     * @param nodeInfo : 대상 노드의 NodeInfo
     * @return int : 타임아웃된 메시지 개수
     * */
    private int checkTimeout(NodeInfo nodeInfo) {

        nodeInfo.getTransactionIds().stream()
                .filter(Objects::nonNull)
                .filter(tId -> nodeInfo.isMsgTimeout(tId, this.timer))
                .forEach(tId -> {
                    MessageInfo messageInfo = nodeInfo.delMsgInfo(tId);

                    if (messageInfo != null) {
                        String sendTimeStr = DateFormatUtil.formatYmdHmsS(messageInfo.getSendTime());
                        nodeInfo.increaseTimeoutCnt();
                        log.warn("[{}] TIMEOUT {} [{}] ({}, RemainMsg:{}, Timer:{}) ",
                                getFormatName(nodeInfo), messageInfo.getMsgType(), tId,
                                sendTimeStr, nodeInfo.getTransactionMapSize(), this.timer);
                    }
                });

        return nodeInfo.getTimeoutCnt();
    }

    static class QosRunnable implements Runnable {
        @Override
        public void run() {
            RmqTrafficManager rmqTrafficManager = RmqTrafficManager.getInstance();
            rmqTrafficManager.checkTrafficQos();

        }
    }
}
