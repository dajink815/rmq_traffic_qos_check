package media.platform.qos.common;

import media.platform.qos.info.NodeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dajin kim
 */
public class CalculateTime {
    private static final Logger log = LoggerFactory.getLogger(CalculateTime.class);

    private CalculateTime() {
        // Do Nothing
    }

    /**
     * @fn checkMinTime
     * @brief 메시지 응답 최소시간 계산
     * @param nodeInfo : MinTime 체크하는 대상 노드의 NodeInfo
     * @param interval : 메시지 응답시간
     * */
    public static void checkMinTime(NodeInfo nodeInfo, long interval) {
        if (nodeInfo.getMinTime() == 0 || interval < nodeInfo.getMinTime()) {
            nodeInfo.setMinTime(interval);
        }
    }

    /**
     * @fn checkMaxTime
     * @brief 메시지 응답 최대시간 계산
     * @param nodeInfo : MaxTime 체크하는 대상 노드의 NodeInfo
     * @param interval : 메시지 응답시간
     * */
    public static void checkMaxTime(NodeInfo nodeInfo, long interval) {
        if (nodeInfo.getMaxTime() == 0 || interval > nodeInfo.getMaxTime()) {
            nodeInfo.setMaxTime(interval);
        }
    }

    /**
     * @fn checkAvgTime
     * @brief 메시지 응답 평균시간 계산
     * @param nodeInfo : 평균시간 계산하는 대상 노드의 NodeInfo
     * @return double : 평균시간 [Unit:ms], (5초동안) 응답받는데 소요된 전체 시간/응답받은 메시지 개수
     * */
    public static double checkAvgTime(NodeInfo nodeInfo) {
        int recvMsgCnt = nodeInfo.getRecvMsgCnt();
        long totalTime = nodeInfo.getTotalTime();
        double avgTime = 0;
        // 받은 메시지가 없으면 평균계산 불필요
        if (recvMsgCnt > 0) {
            try {
                avgTime = (double) totalTime/recvMsgCnt;
            } catch (Exception e) {
                log.error("CalculateTime.checkAvgTime ", e);
            }
        }

        return avgTime;
    }
}
