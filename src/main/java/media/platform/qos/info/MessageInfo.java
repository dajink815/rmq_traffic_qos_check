package media.platform.qos.info;

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

}
