import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Vector;

public class Client {
    final Selector selector;
    final SocketChannel socketChannel;
    final List<Client> connections;
    final List<Room> rooms;
    final List<User> users;

    private Message message;
    private User user;
    private Room room;
    private String answer;

    Client(SocketChannel socketChannel, Selector selector,
           List<Client> connections, List<Room> rooms, List<User> users) throws IOException {
        this.socketChannel = socketChannel;
        this.selector = selector;
        this.connections = connections;
        this.rooms = rooms;
        this.users = users;

        socketChannel.configureBlocking(false);
        SelectionKey selectionKey = socketChannel.register(selector, SelectionKey.OP_READ);
        selectionKey.attach(this);
    }

    public String receive(SelectionKey selectionKey) {
        try {
            message = Message.readMsg(socketChannel);
            switch (message.getMsgType()) {
                case LOG_IN:
                    doLogin(selectionKey);      break;
                case SIGN_UP:
                    doSignup(selectionKey);     break;
                case SEND:
                    doSend(selectionKey);       break;
                case JOIN:
                    doJoin(selectionKey);       break;
                case EXIT:
                    doExit(selectionKey);       break;
                case ROOM_INFO:
                    doRoomInfo(selectionKey);   break;
                case USER_INFO:
                    doUserInfo(selectionKey);   break;
                case MAKE_ROOM:
                    doMakeRoom(selectionKey);   break;
                default:
            }
            answer = new String("[" + message.getMsgType() + " 요청 처리: " +
                    socketChannel.getRemoteAddress() + ": " + Thread.currentThread().getName() + "]");
        } catch (Exception e) {
            try {
                connections.remove(this);
                answer = new String("[클라이언트 통신 안됨: " +
                        socketChannel.getRemoteAddress() + ": " +
                        Thread.currentThread().getName() + "]");
            } catch(Exception e2) {}
        }
        return answer;
    }

    public String send(SelectionKey selectionKey) {
        try {
            Message.writeMsg(socketChannel, message);
            selectionKey.interestOps(SelectionKey.OP_READ);
            selector.wakeup();
            answer = new String("[" + message.getMsgType() + " 요청 처리: " +
                    socketChannel.getRemoteAddress() + ": " + Thread.currentThread().getName() + "]");
        } catch (Exception e) {
            try {
                answer = new String("[클라이언트 통신 안됨: " +
                        socketChannel.getRemoteAddress() + ": " +
                        Thread.currentThread().getName() + "]");
                connections.remove(this);
                socketChannel.close();
            } catch (Exception e2) {}
        }
        return answer;
    }

    public void doLogin(SelectionKey selectionKey) {
        user = findUser(message.getId());
        if(user != null) {
            if(user.getPw().equals(message.getPw())) {
                message.setData("[" + message.getId() + " [로그인 성공]");
                message.setMsgType(MsgType.SUCCESS);
                room = findRoom(Room.LOBBY);
            } else {
                message.setData("[비밀번호가 틀렸습니다]");
                message.setMsgType(MsgType.FAILED);
            }
        } else {
            message.setData("[존재하지 않는 아이디입니다]");
            message.setMsgType(MsgType.FAILED);
        }
        selectionKey.interestOps(SelectionKey.OP_WRITE);
        selector.wakeup();
    }

    public void doSignup(SelectionKey selectionKey) {
        User user = findUser(message.getId());
        if(user == null) {
            message.setData("[" + message.getId() + " 회원가입 성공]");
            message.setMsgType(MsgType.SUCCESS);
            users.add(new User(message.getId(), message.getPw()));
        } else {
            message.setData("[이미 존재하는 아이디입니다]");
            message.setMsgType(MsgType.FAILED);
        }
        selectionKey.interestOps(SelectionKey.OP_WRITE);
        selector.wakeup();
    }

    public void doSend(SelectionKey selectionKey) {
        for(Client client : connections) {
            client.setMessage(message.clone());
            SelectionKey key = client.getSocketChannel().keyFor(selector);
            key.interestOps(SelectionKey.OP_WRITE);
        }
        selector.wakeup();
    }

    public void doJoin(SelectionKey selectionKey) {
        String roomName = message.getData();
        room = findRoom(roomName);
        room.addClient(this);
        List<Client> roomConnections = room.getConnections();
        for(Client client : roomConnections) {
            String id = message.getId();
            String data = new String( "[" + id + " 님이 입장하셨습니다]");
            Message msg = new Message(id, "", data, MsgType.JOIN);
            client.setMessage(msg);
            SelectionKey key = client.getSocketChannel().keyFor(selector);
            key.interestOps(SelectionKey.OP_WRITE);
        }
        message.setMsgType(MsgType.SUCCESS);
        selector.wakeup();
    }

    public void doExit(SelectionKey selectionKey) {
        room.removeClient(this);
        List<Client> roomConnections = room.getConnections();
        for(Client client : roomConnections) {
            String id = message.getId();
            String data = new String("[" + id + " 님이 퇴장하셨습니다]");
            Message msg = new Message(id, "", data, MsgType.EXIT);
            client.setMessage(msg);
            SelectionKey key = client.getSocketChannel().keyFor(selector);
            key.interestOps(SelectionKey.OP_WRITE);
        }
        room = findRoom(Room.LOBBY);
        room.addClient(this);
        message.setMsgType(MsgType.SUCCESS);
        selector.wakeup();
    }

    public void doRoomInfo(SelectionKey selectionKey) {
        String roomName = message.getData();
        if(roomName.equals(Room.LOBBY)) {
            message.setRooms(rooms);
        }
        selectionKey.interestOps(SelectionKey.OP_WRITE);
        selector.wakeup();
    }

    public void doUserInfo(SelectionKey selectionKey) {
        List<User> roomUsers = new Vector<User>();
        String roomName = message.getData();
        List<Client> roomConnections = findRoom(roomName).getConnections();
        for(Client client : roomConnections) {
            roomUsers.add(client.getUser());
        }
        message.setMsgType(MsgType.SUCCESS);
        message.setUsers(roomUsers);
        selectionKey.interestOps(SelectionKey.OP_WRITE);
        selector.wakeup();
    }

    public void doMakeRoom(SelectionKey selectionKey) {
        String roomName = message.getData();
        if(findRoom(roomName) == null) {
            room = new Room(roomName, message.getId());
            rooms.add(room);
            message.setData("[" + roomName+" 방이 만들어졌습니다]");
            message.setMsgType(MsgType.SUCCESS);
        } else {
            message.setData("[같은 이름의 방이 존재합니다]");
            message.setMsgType(MsgType.FAILED);
        }
        selectionKey.interestOps(SelectionKey.OP_WRITE);
        selector.wakeup();
    }


    public User findUser(String id) {
        for(User user : users)
            if(user.getId().equals(id))
                return user;
        return null;
    }

    public Room findRoom(String name) {
        for(Room room : rooms)
            if(room.getName().equals(name))
                return room;
        return null;
    }

    public SocketChannel getSocketChannel() {
        return socketChannel;
    }

    public User getUser() {
        return user;
    }

    public void setMessage(Message message) {
        this.message = message;
    }
}
