package media.platform.qos.info;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author dajin kim
 */
public class RmqTrafficInfo {
    private final ConcurrentHashMap<String, Long> transactionMap =  new ConcurrentHashMap<>();
    private final String targetQname;
    private long minTime;
    private long maxTime;
    private long totalTime;
    // multi-Thread 동시성 보장
    private final AtomicInteger sendMsgCnt = new AtomicInteger();
    private final AtomicInteger recvMsgCnt = new AtomicInteger();

    public RmqTrafficInfo(String targetQname) {
        this.targetQname = targetQname;
    }

    public ConcurrentMap<String, Long> getTransactionMap() {
        return transactionMap;
    }
    public int getTransactionMapSize() {
        return transactionMap.size();
    }
    public List<String> getTransactionIds() {
        synchronized (transactionMap) {
            return new ArrayList<>(transactionMap.keySet());
        }
    }
    public void clearTransactionMap() {
        synchronized (transactionMap) {
            transactionMap.clear();
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

    public long getTotalTime() {
        return totalTime;
    }
    public synchronized void addTotalTime(long interval) {
        this.totalTime += interval;
    }
    public void resetTotalTime() {
        this.totalTime = 0;
    }

    public Long addTransaction(String tId) {
        return transactionMap.putIfAbsent(tId, System.currentTimeMillis());
    }
    public Long delTransaction(String tId) {
        return transactionMap.remove(tId);
    }
    public Long getSendTime(String tId) {
        return transactionMap.get(tId);
    }

    public boolean isMsgTimeout(String tId, long timer) {
        Long sendTime = getSendTime(tId);
        return sendTime != null && sendTime > 0 && sendTime + timer < System.currentTimeMillis();
    }

/*    public static void main(String[] args) {
        String target = "TEST";
        RmqTrafficInfo rmqInfo = new RmqTrafficInfo(target);
        rmqInfo.addTransaction("a");

        System.out.println(rmqInfo.getSendTime("b"));

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Timeout 10s: " + rmqInfo.isMsgTimeout("a", 10000));
        System.out.println("Timeout 5s: " + rmqInfo.isMsgTimeout("a", 5000));
        System.out.println("sendTime: " + DateFormatUtil.formatYmdHmsS(rmqInfo.getSendTime("a")));
        System.out.println("delete: " + DateFormatUtil.formatYmdHmsS(rmqInfo.delTransaction("a")));
    }*/
}
