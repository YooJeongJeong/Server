public enum MsgType {
    /* 서버가 클라이언트로 보내는 메시지 */
    LOGIN_SUCCESS, LOGIN_FAILED,
    SIGNUP_SUCCESS, SIGNUP_FAILED,
    MAKE_SUCCESS, MAKE_FAILED,
    JOIN_SUCCESS, JOIN_FAILED,
    EXIT_SUCCESS, EXIT_FAILED,

    /* 클라이언트가 서버로 보내는 메시지 */
    LOGIN, SIGNUP,
    INFO, MAKE,

    /* 공통 메시지 */
    JOIN, EXIT, SEND,
    UPLOAD, DOWNLOAD,
}