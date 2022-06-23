package media.platform.qos.manager;

import media.platform.qos.common.*;
import media.platform.qos.info.RmqTrafficInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.*;

import static media.platform.qos.common.StatusType.*;
/**
 * RMQ 메시지 QOS 체크
 *
 * @author dajin kim
 */
public class RmqTrafficManager {
    private static final Logger log = LoggerFactory.getLogger(RmqTrafficManager.class);
    private static RmqTrafficManager rmqTrafficManager = null;

    private static final ConcurrentHashMap<String, RmqTrafficInfo> trafficMap = new ConcurrentHashMap<>();
    private final List<String> excludedRmqType = new ArrayList<>();
    private final ScheduledExecutorService scheduleService;
    private final RmqTrafficRunnable rmqTrafficRunnable;

    private static final long TASK_INTERVAL = 5000;     // [Unit: mSec]
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
        this.rmqTrafficRunnable = new RmqTrafficRunnable();
    }

    public static RmqTrafficManager getInstance() {
        if (rmqTrafficManager == null)
            rmqTrafficManager = new RmqTrafficManager();

        return rmqTrafficManager;
    }

    /**
     * TASK_INTERVAL 주기 스케쥴링
     */
    public void start(long timer, long msgGapLimit) {
        log.warn("RMQ_TRAFFIC_MANAGER START, TIMER:{}, MSG_GAP_LIMIT:{} ({})", timer, msgGapLimit, ServiceDefine.VERSION.getValue());
        // todo timer 최소시간 설정?
        this.timer = timer;
        this.msgGapLimit = msgGapLimit;

        // HA
        this.recvCheckDelay = true;
        setHaTime();

        // Scheduler
        int sec = CalendarUtil.getSecond() % 5;
        long mill = CalendarUtil.getMilliSecond();
        mill = sec * 1000 + mill;
        scheduleService.scheduleAtFixedRate(rmqTrafficRunnable, 5000 - mill, TASK_INTERVAL, TimeUnit.MILLISECONDS);
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
            log.warn("RmqTrafficManager Status Changed {} -> {}", haStatus, curStatus);

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
/*        this.getTrafficMapIds().stream()
                .map(trafficMap::get).filter(Objects::nonNull)
                .forEach(RmqTrafficInfo::clearTransactionMap);*/
        this.getTrafficMapIds().stream()
                .map(trafficMap::get).filter(Objects::nonNull)
                .forEach(rmqTrafficInfo -> {
                    int prevMapSize = rmqTrafficInfo.getTransactionMapSize();
                    rmqTrafficInfo.clearTransactionMap();
                    int mapSize = rmqTrafficInfo.getTransactionMapSize();
                    if (prevMapSize != mapSize) {
                        log.debug("[{}] clearTidMap {} -> {}", rmqTrafficInfo.getTargetQname(), prevMapSize, mapSize);
                    }
                });
    }

    /**
     * @fn getTrafficMapIds
     * @brief RmqTrafficInfo 가 생성된 노드의 큐 이름 반환 함수
     * @return List<String> : trafficMap 의 Key List
     * */
    public List<String> getTrafficMapIds() {
        synchronized (trafficMap) {
            return new ArrayList<>(trafficMap.keySet());
        }
    }

    public static ConcurrentMap<String, RmqTrafficInfo> getTrafficMap() {
        return trafficMap;
    }

    /**
     * @fn createTrafficInfo
     * @brief RmqTrafficInfo 생성 함수
     * @param targetQueue : peer Node QueueName
     * @return RmqTrafficInfo
     * */
    public RmqTrafficInfo createRmqTrafficInfo(String targetQueue) {
        if (trafficMap.containsKey(targetQueue)) {
            log.error("RmqTrafficInfo [{}] ALREADY EXIST", targetQueue);
            return null;
        }

        RmqTrafficInfo rmqTrafficInfo = new RmqTrafficInfo(targetQueue);
        rmqTrafficInfo.setMinTime(1000);
        trafficMap.put(targetQueue, rmqTrafficInfo);
        log.warn("() () () RmqTrafficInfo [{}] is Created", targetQueue);
        return rmqTrafficInfo;
    }

    /**
     * @fn getTrafficInfo
     * @brief RmqTrafficInfo 조회 함수
     * @param targetQueue : peer Node QueueName
     * @return RmqTrafficInfo
     * */
    public RmqTrafficInfo getRmqTrafficInfo(String targetQueue) {
        if (targetQueue == null) {
            log.warn("() () () Target Queue Name [{}] is Null", targetQueue);
            return null;
        }
        RmqTrafficInfo rmqTrafficInfo = trafficMap.get(targetQueue);
        if (rmqTrafficInfo == null)
            log.warn("() () () RmqTrafficInfo [{}] is Null", targetQueue);
        return rmqTrafficInfo;
    }

    /**
     * @fn addExcludedRmqType
     * @param rmqType : Traffic 계산에 포함되지 않는 Rmq Message Type
     * */
    public void addExcludedRmqType(String rmqType) {
        excludedRmqType.add(rmqType);
    }

    public List<String> getExcludedRmqType() {
        return excludedRmqType;
    }

    /**
     * @fn rmqSendTimeCheck
     * @brief RMQ 메시지 전송시 호출하는 함수, 메시지의 tId 로 전송시간 기록
     * @param targetQueue : peer Node QueueName
     * @param tId : RmqHeader transactionId
     * @param msgType : Rmq Message Type
     * */
    public void rmqSendTimeCheck(String targetQueue, String tId, String msgType) {
        RmqTrafficInfo rmqTrafficInfo = getRmqTrafficInfo(targetQueue);
        if (rmqTrafficInfo == null) {
            rmqTrafficInfo = createRmqTrafficInfo(targetQueue);
        }

        if (excludedRmqType.contains(msgType) || tId == null || rmqTrafficInfo == null
                // 해당 노드에서 보내기 시작한 RMQ 메시지만 응답시간 체크, Standby 상태는 skip
                || !msgType.toUpperCase().contains(REQ)) {

            if (rmqTrafficInfo == null)
                log.warn("[{}:{}] RmqTrafficInfo is Null", targetQueue, msgType);
            return;
        }

        if (rmqTrafficInfo.addTransaction(tId) == null) {
            // sendCnt 누적
            rmqTrafficInfo.increaseSendMsgCnt();
        } else {
            log.debug("[{}] Rmq TransactionId [{}] already exist", targetQueue, tId);
        }
    }

    /**
     * @fn rmqRecvTimeCheck
     * @brief RMQ 메시지 수신시 호출하는 함수, 메시지의 tId 로 전송시간 조회 후 응답시간 계산
     * @param msgFrom : RmqHeader msgFrom (peer Node QueueName)
     * @param tId : RmqHeader transactionId
     * @param msgType : Rmq Message Type
     * */
    public void rmqRecvTimeCheck(String msgFrom, String tId, String msgType) {
        RmqTrafficInfo rmqTrafficInfo = getRmqTrafficInfo(msgFrom);
        if (excludedRmqType.contains(msgType) || tId == null || rmqTrafficInfo == null
                // 해당 노드에서 보낸 후 응답받은 RMQ 메시지만 응답시간 체크
                || !msgType.toUpperCase().contains(RES)) {
            return;
        }

        // delete
        Long sendTime = rmqTrafficInfo.delTransaction(tId);
        if (sendTime != null) {
            // min, max
            long interval = System.currentTimeMillis() - sendTime;
            CalculateTime.checkMinTime(rmqTrafficInfo, interval);
            CalculateTime.checkMaxTime(rmqTrafficInfo, interval);

            // recvCnt, totalTime 누적
            rmqTrafficInfo.increaseRecvMsgCnt();
            rmqTrafficInfo.addTotalTime(interval);

            if (this.msgGapLimit > 0 && interval >= this.msgGapLimit) {
                log.warn("[{}:{}] GAP: {}, RecvCnt: {}, Total: {} - Over GapLimit ({}), tId: {} ",
                        msgFrom, msgType, interval,
                        rmqTrafficInfo.getRecvMsgCnt(), rmqTrafficInfo.getTotalTime(),
                        this.msgGapLimit, tId);
            } else {
                log.debug("[{}:{}] GAP : {}, RecvCnt : {}, Total : {}",
                        msgFrom, msgType, interval,
                        rmqTrafficInfo.getRecvMsgCnt(), rmqTrafficInfo.getTotalTime());
            }
        } else {
            if (!recvCheckDelay) {
                log.warn("[{}:{}] Rmq TransactionId [{}] is Not Exist", msgFrom, msgType, tId);
            } else {
                log.debug("[{}:{}] RmqTrafficManager just started. (Tid not exist: {})", msgFrom, msgType, tId);
            }
        }
    }

    /**
     * @fn rmqTrafficCheckLogic
     * @brief trafficMap 의 RmqTrafficInfo 조회 & 체크 (TASK_INTERVAL 간격으로 호출)
     * */
    private void rmqTrafficCheckLogic() {

        // 에러로그 delay Flag - 현재 Active 일때 조건 추가 필요?
        if (recvCheckDelay && haTime + TASK_INTERVAL < System.currentTimeMillis()) {
            String haTimeStr = DateFormatUtil.formatYmdHmsS(haTime);
            log.info("DELAY_FLAG {} -> {}, HA_TIME:{}", recvCheckDelay, false, haTimeStr);
            recvCheckDelay = false;
        }

        // ConcurrentHashMap 복사할때 유의
        this.getTrafficMapIds().stream()
                .map(trafficMap::get).filter(Objects::nonNull)
                .forEach(rmqTrafficInfo -> {
                    // Average
                    double avgTime = CalculateTime.checkAvgTime(rmqTrafficInfo);

                    // 잔여 트랜잭션 Timeout 체크
                    int timeoutCnt = 0;
                    if (rmqTrafficInfo.getTransactionMapSize() > 0) {
                        timeoutCnt = rmqTrafficTimeoutLogic(rmqTrafficInfo);
                    }

                    // 5초간 송수신한 RMQ 메시지가 있을 경우, 타임아웃 처리된 트랜잭션 존재할 경우 QOS 출력
                    int transactionCnt = rmqTrafficInfo.getSendMsgCnt() + rmqTrafficInfo.getRecvMsgCnt();
                    if (transactionCnt > 0 || timeoutCnt > 0 || rmqTrafficInfo.getTransactionMapSize() > 0) {
                        log.info("[{}] QOS Avg:{}(MinMax:{}/{} T:{}), S:{}, R:{}, Timeout:{}, RemainMsg:{}",
                                rmqTrafficInfo.getTargetQname(), String.format("%.2f", avgTime), rmqTrafficInfo.getMinTime(), rmqTrafficInfo.getMaxTime(),
                                rmqTrafficInfo.getTotalTime(), rmqTrafficInfo.getSendMsgCnt(), rmqTrafficInfo.getRecvMsgCnt(),
                                timeoutCnt, rmqTrafficInfo.getTransactionMapSize());

                        // Reset
                        rmqTrafficInfo.setMinTime(1000);
                        rmqTrafficInfo.setMaxTime(0);
                        rmqTrafficInfo.resetSendMsgCnt();
                        rmqTrafficInfo.resetRecvMsgCnt();
                        rmqTrafficInfo.resetTotalTime();
                    }
                });
    }

    /**
     * @fn rmqTrafficTimeoutLogic
     * @brief RmqTrafficInfo 의 transactionMap 타임아웃 메시지 조회 및 제거
     * @param rmqTrafficInfo : 대상 노드의 RmqTrafficInfo
     * @return int : 타임아웃된 메시지 개수
     * */
    private int rmqTrafficTimeoutLogic(RmqTrafficInfo rmqTrafficInfo) {
        int timeOutCnt = 0;

        for (String tId : rmqTrafficInfo.getTransactionIds()) {
            if (rmqTrafficInfo.isMsgTimeout(tId, this.timer)) {

                // delete 이후에는 map 에서 제거되므로 미리 조회
                // TransactionId 정보 삭제하면서 sendTime 반환 -> 포맷에 맞춰 변환
                String sendTimeStr = DateFormatUtil.formatYmdHmsS(rmqTrafficInfo.delTransaction(tId));
                timeOutCnt++;

                log.warn("[{}] TIMEOUT (tId:{}, SendTime:{}, RemainMsg:{}, Timer:{}) ",
                        rmqTrafficInfo.getTargetQname(), tId, sendTimeStr, rmqTrafficInfo.getTransactionMapSize(), this.timer);
            }
        }

        return timeOutCnt;
    }

    static class RmqTrafficRunnable implements Runnable {
        @Override
        public void run() {
            RmqTrafficManager rmqTrafficManager = RmqTrafficManager.getInstance();
            rmqTrafficManager.rmqTrafficCheckLogic();

        }
    }
}
