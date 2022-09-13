package media.platform.qos.info;

import media.platform.qos.common.DateFormatUtil;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author dajin kim
 */
public class MessageInfo {
    private final String tId;
    private final long sendTime;
    private final String msgType;

    public MessageInfo(String tId, String msgType) {
        this.tId = tId;
        this.sendTime = System.currentTimeMillis();
        this.msgType = msgType;
    }

    public String getTId() {
        return tId;
    }

    public long getSendTime() {
        return sendTime;
    }

    public String getMsgType() {
        return msgType;
    }

    public static void main(String[] args) {

/*
        String format = String.format("%3d", 1211);
        System.out.println(format);
*/

        ScheduledExecutorService scheduleService = Executors.newScheduledThreadPool(1);
        scheduleService.scheduleAtFixedRate(() -> System.out.println(DateFormatUtil.formatYmdHmsS(System.currentTimeMillis())),
                5000 - System.currentTimeMillis() % 5000,
                5000,
                TimeUnit.MILLISECONDS);
    }

}
