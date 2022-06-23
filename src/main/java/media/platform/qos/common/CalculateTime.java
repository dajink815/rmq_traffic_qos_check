package media.platform.qos.common;

import media.platform.qos.info.RmqTrafficInfo;
import media.platform.qos.manager.RmqTrafficManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dajin kim
 */
public class CalculateTime {
    private static final Logger log = LoggerFactory.getLogger(RmqTrafficManager.class);

    private CalculateTime() {
        // Do Nothing
    }

    /**
     * @fn checkMinTime
     * @brief RMQ 메시지 응답 최소시간 계산
     * @param rmqTrafficInfo : MinTime 체크하는 대상 노드의 RmqTrafficInfo
     * @param interval : Rmq 메시지 응답시간
     * */
    public static void checkMinTime(RmqTrafficInfo rmqTrafficInfo, long interval) {
        if (interval < rmqTrafficInfo.getMinTime()) {
            rmqTrafficInfo.setMinTime(interval);
        }
    }

    /**
     * @fn checkMaxTime
     * @brief RMQ 메시지 응답 최대시간 계산
     * @param rmqTrafficInfo : MaxTime 체크하는 대상 노드의 RmqTrafficInfo
     * @param interval : Rmq 메시지 응답시간
     * */
    public static void checkMaxTime(RmqTrafficInfo rmqTrafficInfo, long interval) {
        if (interval > rmqTrafficInfo.getMaxTime()) {
            rmqTrafficInfo.setMaxTime(interval);
        }
    }

    /**
     * @fn checkAvgTime
     * @brief RMQ 메시지 응답 평균시간 계산
     * @param rmqTrafficInfo : 평균시간 계산하는 대상 노드의 RmqTrafficInfo
     * @return double : 평균시간 [Unit:ms], (5초동안) 응답받는데 소요된 전체 시간/응답받은 메시지 개수
     * */
    public static double checkAvgTime(RmqTrafficInfo rmqTrafficInfo) {
        int recvMsgCnt = rmqTrafficInfo.getRecvMsgCnt();
        long totalTime = rmqTrafficInfo.getTotalTime();
        double avgTime = 0;
        // 받은 Rmq 메시지가 없으면 평균계산 불필요
        if (recvMsgCnt > 0) {
            try {
                avgTime = (double) totalTime/recvMsgCnt;
            } catch (Exception e) {
                log.error("RmqTrafficManager.checkAvgTime ", e);
            }
        }

        return avgTime;
    }
}
