package media.platform.qos.common;

/**
 * @author dajin kim
 */
public enum StatusType {
    STANDALONE(2),
    ACTIVE(1),
    STANDBY(0),
    DOWN(-1);

    private final int value;

    StatusType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static StatusType getTypeEnum(int type) {
        switch (type) {
            case 2: return STANDALONE;
            case 1:  return ACTIVE;
            case 0: return STANDBY;
            case -1 : return DOWN;
            default : return null;
        }
    }
}
