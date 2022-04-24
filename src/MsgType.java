public enum MsgType {
    /* 서버가 클라이언트로 보내는 메시지 */
    SUCCESS, FAILED,

    /* 클라이언트가 서버로 보내는 메시지 */
    LOG_IN, SIGN_UP,
    ROOM_INFO, USER_INFO,
    MAKE_ROOM,

    /* 공통 메시지 */
    JOIN, EXIT,
    SEND
}
