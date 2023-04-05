package media.platform.qos.manager;

import java.util.List;

/**
 * @author dajin kim
 */
public interface RmqTrafficInterface {

    /**
     * @fn start
     * @brief RmqTrafficManager 시작, TASK_INTERVAL 주기로 QOS 스케쥴링
     * @param timer: 메시지 timeout 처리 시간
     * @param msgGapLimit: 메시지 제한 응답 시간 (Unit:ms)
     */
    public void start(long timer, long msgGapLimit);

    /**
     * @fn addExcludedRmqType
     * @param msgType : Traffic 계산에 포함되지 않는 Message Type
     * */
    public void addExcludedRmqType(String msgType);

    /**
     * @fn rmqSendTimeCheck
     * @brief 메시지 전송시 호출하는 함수, 메시지의 tId 로 전송시간 기록
     * @param targetQueue : peer Node QueueName
     * @param tId : Message transactionId
     * @param msgType : Message Type
     * */
    public void rmqSendTimeCheck(String targetQueue, String tId, String msgType);

    /**
     * @fn rmqRecvTimeCheck
     * @brief 메시지 수신시 호출하는 함수, 메시지의 tId 로 전송시간 조회 후 응답시간 계산
     * @param msgFrom : Message msgFrom (peer Node QueueName)
     * @param tId : Message transactionId
     * @param msgType : Message Type
     * */
    public void rmqRecvTimeCheck(String msgFrom, String tId, String msgType);



    public void stop();

    public void setHaStatus(int status);

    public long getTimer();

    public long getMsgGapLimit();

    public List<String> getExcludedRmqType();

}
