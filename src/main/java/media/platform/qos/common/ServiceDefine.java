package media.platform.qos.common;

/**
 * @author dajin kim
 */
public enum ServiceDefine {
    VERSION("1.0.2");

    private final String value;

    ServiceDefine(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
