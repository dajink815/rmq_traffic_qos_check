package media.platform.qos.main;


import media.platform.qos.common.DateFormatUtil;
import media.platform.qos.service.ScenarioTest;
import org.junit.Test;

import java.util.Calendar;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author dajin kim
 */
public class TestMain {
    private boolean isQuit = false;

    @Test
    public void mapTest() {
        ScenarioTest scenarioTest = new ScenarioTest();
        scenarioTest.mapTest();
    }

    @Test
    public void scheduleServiceTest() {
        long interval = 1000;

        ScheduledExecutorService scheduleService = Executors.newScheduledThreadPool(1);
        scheduleService.scheduleAtFixedRate(() -> {
                    int curSecond = Calendar.getInstance().get(Calendar.SECOND);
                    if (curSecond % 30 == 0) {
                        System.out.println(DateFormatUtil.formatYmdHmsS(System.currentTimeMillis()));
                    }
                },
                interval - System.currentTimeMillis() % interval,
                interval,
                TimeUnit.MILLISECONDS);

        while (!isQuit) {
            try {
                trySleep(1000);
            } catch (Exception e) {
                System.err.println("ServiceManager.loop.Exception " + e);
            }
        }
    }

    public static void trySleep(long sleepTime) {
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
