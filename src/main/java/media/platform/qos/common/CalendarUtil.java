package media.platform.qos.common;

import java.util.Calendar;

/**
 * @author dajin kim
 */
public class CalendarUtil {

    private CalendarUtil() {
        // Do Nothing
    }

    public static int getSecond() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.SECOND);
    }

    public static long getMilliSecond(){
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.MILLISECOND);
    }

    public static boolean checkInterval(int interval) {
        return (getSecond() + 1) % interval == 0;
    }
}
