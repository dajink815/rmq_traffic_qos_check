package media.platform.qos.common;

/**
 * @author dajin kim
 */
public class StringUtil {
    private static final String REQ = "REQ";
    private static final String RES = "RES";

    private StringUtil() {
        // nothing
    }

    public static boolean isReqType(String msgType) {
        return msgType.toUpperCase().contains(REQ);
    }

    public static boolean isResType(String msgType) {
        return msgType.toUpperCase().contains(RES);
    }

    public static boolean isNull(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean notNull(String str) {
        return str != null && !str.isEmpty();
    }

}
