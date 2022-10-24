package media.platform.qos.info;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author dajin kim
 */
public class NodeInfo {
    private static final Logger log = LoggerFactory.getLogger(NodeInfo.class);

    // key: transactionId & value : MessageInfo
    private final ConcurrentHashMap<String, MessageInfo> msgMap = new ConcurrentHashMap<>();
    private final String targetQname;

    // multi-Thread 동시성 보장
    private final AtomicLong minTime = new AtomicLong();
    private final AtomicLong maxTime = new AtomicLong();
    private final AtomicLong totalTime = new AtomicLong();

    private final AtomicInteger sendMsgCnt = new AtomicInteger();
    private final AtomicInteger recvMsgCnt = new AtomicInteger();
    private final AtomicInteger timeOutCnt = new AtomicInteger();

    public NodeInfo(String targetQname) {
        this.targetQname = targetQname;
    }

    public ConcurrentMap<String, MessageInfo> getMsgMap() {
        return msgMap;
    }
    public int getMsgMapSize() {
        return msgMap.size();
    }
    public List<String> getTransactionIds() {
        synchronized (msgMap) {
            return new ArrayList<>(msgMap.keySet());
        }
    }
    public void clearTransactionMap() {
        synchronized (msgMap) {
            msgMap.clear();
        }
    }

    public String getTargetQname() {
        return targetQname;
    }


    public long getMinTime() {
        return minTime.get();
    }
    public void setMinTime(long minTime) {
        this.minTime.set(minTime);
    }
    /**
     * @fn checkMinTime
     * @brief 메시지 응답 최소시간 계산
     * @param interval : 메시지 응답시간
     * */
    public synchronized void checkMinTime(long interval) {
        if (getMinTime() == 0 || interval < getMinTime()) {
            setMinTime(interval);
        }
    }


    public long getMaxTime() {
        return maxTime.get();
    }
    public void setMaxTime(long maxTime) {
        this.maxTime.set(maxTime);
    }
    /**
     * @fn checkMaxTime
     * @brief 메시지 응답 최대시간 계산
     * @param interval : 메시지 응답시간
     * */
    public synchronized void checkMaxTime(long interval) {
        if (getMaxTime() == 0 || interval > getMaxTime()) {
            setMaxTime(interval);
        }
    }


    public long getTotalTime() {
        return totalTime.get();
    }
    public void addTotalTime(long interval) {
        totalTime.addAndGet(interval);
    }
    public void resetTotalTime() {
        totalTime.set(0);
    }


    public int getSendMsgCnt() {
        return this.sendMsgCnt.get();
    }
    public void increaseSendMsgCnt() {
        this.sendMsgCnt.getAndIncrement();
    }
    public void resetSendMsgCnt() {
        this.sendMsgCnt.set(0);
    }

    public int getRecvMsgCnt() {
        return this.recvMsgCnt.get();
    }
    public void increaseRecvMsgCnt() {
        this.recvMsgCnt.getAndIncrement();
    }
    public void resetRecvMsgCnt() {
        this.recvMsgCnt.set(0);
    }

    public int getTimeoutCnt() {
        return this.timeOutCnt.get();
    }
    public void increaseTimeoutCnt() {
        this.timeOutCnt.getAndIncrement();
    }
    public void resetTimeoutCnt() {
        this.timeOutCnt.set(0);
    }


    public MessageInfo addMsgInfo(String tId, String msgType) {
        return msgMap.putIfAbsent(tId, new MessageInfo(tId, msgType));
    }
    public MessageInfo getMsgInfo(String tId) {
        MessageInfo messageInfo = msgMap.get(tId);
        if (messageInfo == null) {
            log.info("[QOS] [{}] MessageInfo [{}] is Null", targetQname, tId);
        }
        return messageInfo;
    }
    public MessageInfo delMsgInfo(String tId) {
        return msgMap.remove(tId);
    }
/*    public Long getSendTime(String tId) {
        MessageInfo messageInfo = getMsgInfo(tId);
        if (messageInfo != null) {
            return messageInfo.getSendTime();
        } else {
            return (long) 0;
        }
    }*/

    public boolean isMsgTimeout(String tId, long timer) {
        try {
            MessageInfo messageInfo = getMsgInfo(tId);
            if (messageInfo != null) {
                long sendTime = messageInfo.getSendTime();
                return sendTime > 0 && sendTime + timer < System.currentTimeMillis();
            }
        } catch (Exception e) {
            log.error("[QOS] [{}] NodeInfo.isMsgTimeout.Exception (tId:{})", targetQname, tId, e);
        }
        return false;
    }

    /**
     * @fn checkAvgTime
     * @brief 메시지 응답 평균시간 계산
     * @return double : 평균시간 [Unit:ms], (5초동안) 응답받는데 소요된 전체 시간/응답받은 메시지 개수
     * */
    public synchronized double checkAvgTime() {
        double avgTime = 0;
        // 받은 메시지가 없으면 평균계산 불필요
        if (getRecvMsgCnt() > 0) {
            try {
                avgTime = (double) getTotalTime()/getRecvMsgCnt();
            } catch (Exception e) {
                log.error("[QOS] [{}] NodeInfo.checkAvgTime.Exception ", targetQname, e);
            }
        }

        return avgTime;
    }

    public void resetStats() {
        setMinTime(0);
        setMaxTime(0);
        resetSendMsgCnt();
        resetRecvMsgCnt();
        resetTimeoutCnt();
        resetTotalTime();
    }

}
