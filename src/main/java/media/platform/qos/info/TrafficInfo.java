package media.platform.qos.info;

/**
 * @author dajin kim
 */
public class TrafficInfo {

    private boolean isTimeout;
    private int cps;

    public boolean isTimeout() {
        return isTimeout;
    }
    public void setTimeout(boolean timeout) {
        isTimeout = timeout;
    }

    public int getCps() {
        return cps;
    }
    public void setCps(int cps) {
        this.cps = cps;
    }

    @Override
    public String toString() {
        return "TrafficInfo{" +
                "isTimeout=" + isTimeout +
                ", cps=" + cps +
                '}';
    }
}
