package media.platform.qos.common;

import java.text.SimpleDateFormat;

/**
 * @author dajin kim
 */
public class DateFormatUtil {
    private static final String YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd HH:mm:ss";
    private static final String YYYY_MM_DD_HH_MM_SS_SSS = "yyyy-MM-dd HH:mm:ss.SSS";
    private static final String YYYY_MM_DD = "yyyyMMdd";
    private static final String HH_MM_SS = "HHmmss";
    private static final String HH_MM = "HHmm";

    private DateFormatUtil() {
        // Do Nothing
    }

    public static String formatYmdHms(long longDate) {
        return format(YYYY_MM_DD_HH_MM_SS, longDate);
    }

    public static String formatYmdHmsS(long longDate) {
        return format(YYYY_MM_DD_HH_MM_SS_SSS, longDate);
    }

    public static String formatYmd(long longDate) {
        return format(YYYY_MM_DD, longDate);
    }

    public static String formatHms(long longDate) {
        return format(HH_MM_SS, longDate);
    }

    public static String formatHm(long longDate) {
        return format(HH_MM, longDate);
    }

    private static String format(String dateFormat, long longDate) {
        SimpleDateFormat format = new SimpleDateFormat(dateFormat);
        return format.format(longDate);
    }
}
