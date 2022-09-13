package media.platform.qos.info;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author dajin kim
 */
public class NodeInfo {
    // key: transactionId & value : MessageInfo
    private final ConcurrentHashMap<String, MessageInfo> msgMap = new ConcurrentHashMap<>();
    private final String targetQname;
    private long minTime;
    private long maxTime;
    private long totalTime;
    // multi-Thread 동시성 보장
    private final AtomicInteger sendMsgCnt = new AtomicInteger();
    private final AtomicInteger recvMsgCnt = new AtomicInteger();
    private final AtomicInteger timeOutCnt = new AtomicInteger();

    public NodeInfo(String targetQname) {
        this.targetQname = targetQname;
    }

    public ConcurrentMap<String, MessageInfo> getMsgMap() {
        return msgMap;
    }
    public int getTransactionMapSize() {
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
        return minTime;
    }
    public void setMinTime(long minTime) {
        this.minTime = minTime;
    }

    public long getMaxTime() {
        return maxTime;
    }
    public void setMaxTime(long maxTime) {
        this.maxTime = maxTime;
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

    public long getTotalTime() {
        return totalTime;
    }
    public synchronized void addTotalTime(long interval) {
        this.totalTime += interval;
    }
    public void resetTotalTime() {
        this.totalTime = 0;
    }

    public MessageInfo addMsgInfo(String tId, String msgType) {
        return msgMap.putIfAbsent(tId, new MessageInfo(tId, msgType));
    }
    public MessageInfo delMsgInfo(String tId) {
        return msgMap.remove(tId);
    }
    public Long getSendTime(String tId) {
        if (msgMap.containsKey(tId)) {
            return msgMap.get(tId).getSendTime();
        }
        return (long)0;
    }

    public boolean isMsgTimeout(String tId, long timer) {
        if (!msgMap.containsKey(tId)) return false;
        long sendTime = msgMap.get(tId).getSendTime();
        return sendTime > 0 && sendTime + timer < System.currentTimeMillis();
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
