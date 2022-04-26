import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.List;

public class Message implements Serializable {
    static final long serialVersionUID = 1L;

    private String id, pw;
    private String data;
    private MsgType msgType;

    private List<User> users;
    private List<Room> rooms;

    Message (String id, String pw, MsgType msgType) {
        this(id, pw, null, msgType);
    }

    Message(String id, String pw, String data, MsgType msgType) {
        this.id = id;
        this.pw = pw;
        this.data = data;
        this.msgType = msgType;
    }

    /* 소켓 채널을 통해 바이트 데이터를 읽고, 버퍼에 담긴 데이터를 Message 객체로 복원하는 메소드 */
    public static Message readMsg (SocketChannel socketChannel) throws Exception {
        Message message = null;
        /* 소켓 채널을 통해 바이트화된 Message 객체 정보를 읽어들임 */
        ByteBuffer byteBuffer = ByteBuffer.allocate(1024 * 4);
        int byteCount = socketChannel.read(byteBuffer);

        /* 상대방이 SocketChannel의 close() 메소드를 호출한 경우 */
        if(byteCount == -1) {
            throw new IOException();
        }
        /* 바이트 버퍼에 담긴 데이터를 바이트 배열에 저장 */
        byteBuffer.flip();
        byte[] serializedMsg = new byte[byteBuffer.remaining()];
        byteBuffer.get(serializedMsg);

        /* 바이트 배열을 Message 객체로 복원 */
        ByteArrayInputStream bais = new ByteArrayInputStream(serializedMsg);
        ObjectInputStream ois = new ObjectInputStream(bais);
        Object obj = ois.readObject();
        if(obj instanceof Message)
            message = (Message) obj;

        return message;
    }

    /* Message 객체를 바이트 데이터로 변환하여 버퍼에 담은 뒤, 소켓 채널을 통해 쓰는 메소드 */
    public static void writeMsg (SocketChannel socketChannel, Message msg) throws Exception {
        /* Message 객체를 바이트 배열로 변환 */
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(msg);
        byte[] serializedMsg = baos.toByteArray();
        /* 바이트 배열을 바이트 버퍼에 담은 뒤, 소켓 채널을 통해 전송 */
        ByteBuffer byteBuffer = ByteBuffer.wrap(serializedMsg);

        socketChannel.write(byteBuffer);
    }

    public String getId() {
        return id;
    }

    public String getPw() {
        return pw;
    }

    public String getData() {
        return data;
    }

    public MsgType getMsgType() {
        return msgType;
    }

    public List<Room> getRooms() {
        return rooms;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setData(String data) {
        this.data = data;
    }

    public void setMsgType(MsgType msgType) {
        this.msgType = msgType;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    public void setRooms(List<Room> rooms) {
        this.rooms = rooms;
    }
}
