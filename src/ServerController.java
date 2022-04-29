import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ResourceBundle;
import javafx.application.Platform;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;

public class ServerController implements Initializable {
    ExecutorService executorService;

    Selector selector;
    ServerSocketChannel serverSocketChannel;
    List<Client> connections = new Vector<Client>();    // 연결된 클라이언트
    List<User> users = new Vector<User>();              // 회원가입된 유저 리스트
    List<Room> rooms = new Vector<Room>();              // 생성된 방 리스트

    public void startServer() {
        try {
            executorService = Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors()
            );

            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(5001));
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch(Exception e) {
            if(serverSocketChannel.isOpen())
                stopServer();
            return;
        }

        Thread thread = new Thread (() -> {
            while(true) {
                try {
                    int keyCount = selector.select();
                    if(keyCount == 0)
                        continue;

                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectedKeys.iterator();
                    while(iterator.hasNext()) {
                        SelectionKey selectionKey = iterator.next();
                        if(selectionKey.isAcceptable()) {
                            accept();
                        }
                        else if(selectionKey.isReadable()) {
                            Client client = (Client)selectionKey.attachment();
                            client.receive(selectionKey);
                        }
                        else if(selectionKey.isWritable()) {
                            Client client = (Client)selectionKey.attachment();
                            client.send(selectionKey);
                        }
                        iterator.remove();
                    }
                } catch (Exception e) {
                    if(serverSocketChannel.isOpen()) {
                        stopServer();
                    }
                    break;
                }
            }
        });
        thread.start();

        Platform.runLater(() -> {
            displayText("[서버 시작]");
            btnConn.setText("stop");
        });
    }

    public void stopServer() {
        try {
            Iterator<Client> iterator = connections.iterator();
            while(iterator.hasNext()) {
                Client client = iterator.next();
                client.socketChannel.close();
                iterator.remove();
            }
            if(serverSocketChannel != null && serverSocketChannel.isOpen())
                serverSocketChannel.close();
            if(executorService != null && !executorService.isShutdown())
                executorService.shutdown();
            if(selector != null && selector.isOpen())
                selector.close();
            Platform.runLater(() -> {
                displayText("[서버 멈춤]");
                btnConn.setText("start");
            });
        } catch (Exception e) {}
    }

    public void accept(){
        try {
            SocketChannel socketChannel = serverSocketChannel.accept();

            String message = "[연결 수락: " + socketChannel.getRemoteAddress() + ": " +
                    Thread.currentThread().getName() + "]";
            Platform.runLater(() -> displayText(message));

            Client client = new Client(socketChannel);
            connections.add(client);

            Platform.runLater(() -> {
                displayText("[연결 개수: " + connections.size() + "]");
            });
        } catch (Exception e) {
            if(serverSocketChannel.isOpen())
                stopServer();
        }
    }

    class Client {
        SocketChannel socketChannel;
        Message message;
        User user;
        Room room;

        FileChannel fileChannel;

        Client(SocketChannel socketChannel) throws IOException {
            this.socketChannel = socketChannel;
            socketChannel.configureBlocking(false);
            SelectionKey selectionKey = socketChannel.register(selector, SelectionKey.OP_READ);
            selectionKey.attach(this);
        }

        public void receive(SelectionKey selectionKey) {
            try {
                message = Message.readMsg(socketChannel);
                switch (message.getMsgType()) {
                    case LOGIN:
                        doLogin(selectionKey);      break;
                    case SIGNUP:
                        doSignup(selectionKey);     break;
                    case SEND:
                        doSend(selectionKey);       break;
                    case JOIN:
                        doJoin(selectionKey);       break;
                    case EXIT:
                        doExit(selectionKey);       break;
                    case ROOM_INFO:
                        doInfo(selectionKey);       break;
                    case MAKE_ROOM:
                        doMakeRoom(selectionKey);   break;
                    /* 클라이언트가 서버로 업로드 요청을 할 때 시작 - 처리 - 완료 세 단계를 거치게 됨 */
                    case UPLOAD_START:
                        startUPload(selectionKey);  return;
                    case UPLOAD_DO:
                        receiveFile(selectionKey);  return;
                    case UPLOAD_END:
                        endUpload(selectionKey);    return;
                    /* 클라이언트의 다운로드 요청 시 서버도 파일 리스트 보내고 시작 - 처리 - 완료 단계를 거침 */
                    case DOWNLOAD_LIST:
                        downloadList(selectionKey); return;
                    case DOWNLOAD_START:
                        startDownload(selectionKey);return;
                    case DOWNLOAD_DO:
                        sendFile(selectionKey);     return;
                    case DOWNLOAD_END:
                        endDownload(selectionKey);  return;
                    /* 초대 요청에 대한 서버의 대답 */
                    case INVITE:
                        doInvite(selectionKey);     break;
                    case INVITE_SUCCESS:
                        inviteSuccess(selectionKey);break;
                    case INVITE_FAILED:
                        inviteFailed(selectionKey); break;
                    default:
                        Platform.runLater(()->displayText("[Error: unexpected message type]")); return;
                }
                String msg = "[" + message.getMsgType() + " 요청 처리: " + socketChannel.getRemoteAddress() + ": " +
                        Thread.currentThread().getName() + "]";
                Platform.runLater(()->displayText(msg));
            } catch (Exception e) {
                try {
                    displayText("[클라이언트 통신 안됨: " +
                            socketChannel.getRemoteAddress() + ": " +
                            Thread.currentThread().getName() + "]");
                    connections.remove(this);
                    socketChannel.close();
                    sendErrorMsg();
                } catch(Exception e2) {}
            }
        }

        public void send(SelectionKey selectionKey) {
            try {
                Message.writeMsg(socketChannel, message);
                switch(message.getMsgType()) {
                    case LOGIN_FAILED:
                    case SIGNUP_FAILED:
                        connections.remove(this);
                        socketChannel.close();;
                        break;
                    default:
                        selectionKey.interestOps(SelectionKey.OP_READ);
                        selector.wakeup();
                }
            } catch (Exception e) {
                try {
                    displayText("[클라이언트 통신 안됨: " +
                            socketChannel.getRemoteAddress() + ": " +
                            Thread.currentThread().getName() + "]");
                    connections.remove(this);
                    socketChannel.close();
                    sendErrorMsg();
                } catch(Exception e2) {
                    e2.printStackTrace();
                }
            }
        }

        public void doLogin(SelectionKey selectionKey) {
            user = findUser(message.getId());
            if(user != null) {
                if(user.getPw().equals(message.getPw())) {
                    refresh(Room.LOBBY);
                    message.setData("[" + message.getId() + " [로그인 성공]");
                    message.setMsgType(MsgType.LOGIN_SUCCESS);
                    room = findRoom(Room.LOBBY);
                } else {
                    message.setData("[비밀번호가 틀렸습니다]");
                    message.setMsgType(MsgType.LOGIN_FAILED);
                }
            } else {
                message.setData("[존재하지 않는 아이디입니다]");
                message.setMsgType(MsgType.LOGIN_FAILED);
            }
            selectionKey.interestOps(SelectionKey.OP_WRITE);
            selector.wakeup();
        }

        public void doSignup(SelectionKey selectionKey) {
            user = findUser(message.getId());
            if(user == null) {
                message.setData("[" + message.getId() + " 회원가입 성공]");
                message.setMsgType(MsgType.SIGNUP_SUCCESS);
                users.add(new User(message.getId(), message.getPw()));
            } else {
                message.setData("[이미 존재하는 아이디입니다]");
                message.setMsgType(MsgType.SIGNUP_FAILED);
            }
            selectionKey.interestOps(SelectionKey.OP_WRITE);
            selector.wakeup();
        }

        public void doSend(SelectionKey selectionKey) {
            String data = "[" + message.getId() + "] " + message.getData();
            List<Client> clientList = findClient(room.getName());
            for(Client client : clientList) {
                client.message = new Message(message.getId(), "", data, MsgType.SEND);
                SelectionKey key = client.socketChannel.keyFor(selector);
                key.interestOps(SelectionKey.OP_WRITE);
            }
            selectionKey.interestOps(SelectionKey.OP_WRITE);
            selector.wakeup();
        }

        public void doJoin(SelectionKey selectionKey) {
            try {
                refresh(Room.LOBBY);
                String roomName = message.getData();
                List<Client> clientList = findClient(roomName);
                for(Client client : clientList) {
                    String data = "[" + user.getId() + " 님이 입장하셨습니다]";
                    client.message = new Message(user.getId(), "", data, MsgType.JOIN);
                    SelectionKey key = client.socketChannel.keyFor(selector);
                    key.interestOps(SelectionKey.OP_WRITE);
                }
                room = findRoom(roomName);
                message.setData(roomName);
                message.setMsgType(MsgType.JOIN_SUCCESS);
            } catch (Exception e) {
                message.setMsgType(MsgType.JOIN_FAILED);
            }
            selectionKey.interestOps(SelectionKey.OP_WRITE);
            selector.wakeup();
        }

        public void doExit(SelectionKey selectionKey) {
            try {
                refresh(Room.LOBBY);
                String roomName = room.getName();
                room = findRoom(Room.LOBBY);
                List<Client> clientList = findClient(roomName);

                if(!roomName.equals(Room.LOBBY) &&
                        (clientList == null || clientList.size() == 0)) {
                    rooms.remove(findRoom(roomName));
                } else {
                    for(Client client : clientList) {
                        String data = "[" + user.getId() + " 님이 퇴장하셨습니다]";
                        client.message = new Message(user.getId(), "", data, MsgType.EXIT);
                        SelectionKey key = client.socketChannel.keyFor(selector);
                        key.interestOps(SelectionKey.OP_WRITE);
                    }
                }
                message.setMsgType(MsgType.EXIT_SUCCESS);
            } catch (Exception e) {
                message.setMsgType(MsgType.EXIT_FAILED);
            }
            selectionKey.interestOps(SelectionKey.OP_WRITE);
            selector.wakeup();
        }

        public void doInfo(SelectionKey selectionKey) {
            List<User> users = new Vector<User>();
            String roomName = message.getData();
            List<Client> clientList = findClient(roomName);
            for(Client client : clientList) {
                users.add(client.user);
            }
            message.setUsers(users);
            message.setRooms(rooms);

            selectionKey.interestOps(SelectionKey.OP_WRITE);
            selector.wakeup();
        }

        public void doMakeRoom(SelectionKey selectionKey) {
            String roomName = message.getData();
            if(findRoom(roomName) == null) {
                refresh(Room.LOBBY);
                Room newRoom = new Room(roomName, message.getId());
                rooms.add(newRoom);
                room = findRoom(roomName);
                message.setMsgType(MsgType.MAKE_SUCCESS);
            } else {
                message.setData("[같은 이름의 방이 존재합니다]");
                message.setMsgType(MsgType.MAKE_FAILED);
            }
            selectionKey.interestOps(SelectionKey.OP_WRITE);
            selector.wakeup();
        }

        public void startUPload(SelectionKey selectionKey) {
            try {
                String fileName = message.getData();
                String filePath = "file" + File.separator + fileName;
                Path path = Paths.get(filePath);
                Files.createDirectories(path.getParent());

                fileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                Platform.runLater(() -> {displayText("[업로드 시작: " + fileName + "]");});

                message = new Message(MsgType.UPLOAD_START);
                selectionKey.interestOps(SelectionKey.OP_WRITE);
                selector.wakeup();

            } catch (Exception e) {
                Platform.runLater(() -> {displayText("[파일 채널 생성 중 오류 발생]");});
            }
        }

        public void endUpload(SelectionKey selectionKey) {
            try {
                fileChannel.close();
                String fileName = message.getData();
                Platform.runLater(() -> {displayText("[업로드 완료: " + fileName + "]");});

                message = new Message(MsgType.UPLOAD_END);
                selectionKey.interestOps(SelectionKey.OP_WRITE);
                selector.wakeup();
            } catch (Exception e) {
                Platform.runLater(() -> {displayText("[파일 채널 닫는 중 오류 발생]");});
            }
        }

        public void receiveFile(SelectionKey selectionKey) {
            try {
                byte[] fileData = message.getFileData();
                ByteBuffer byteBuffer = ByteBuffer.wrap(fileData);
                fileChannel.write(byteBuffer);

                message = new Message(MsgType.UPLOAD_DO);
                selectionKey.interestOps(SelectionKey.OP_WRITE);
                selector.wakeup();
            } catch (Exception e) {
                Platform.runLater(() -> {displayText("[파일 쓰는 중 오류 발생]");});
                e.printStackTrace();
            }
        }

        public void downloadList(SelectionKey selectionKey) {
            List<FileInfo> fileList = new Vector<FileInfo>();
            File[] files = new File("file").listFiles();
            for(File file : Objects.requireNonNull(files)) {
                FileInfo fileInfo = new FileInfo(file.getName(), file.length());
                fileList.add(fileInfo);
            }
            message = new Message(fileList, MsgType.DOWNLOAD_LIST);
            selectionKey.interestOps(SelectionKey.OP_WRITE);
            selector.wakeup();
        }

        public void startDownload(SelectionKey selectionKey) {
            /* 파일 채널 열고 클라이언트에게 응답 */
            try {
                String fileName = message.getData();
                String filePath = "file" + File.separator + fileName;

                Path path = Paths.get(filePath);
                fileChannel = FileChannel.open(path, StandardOpenOption.READ);

                Platform.runLater(() -> {displayText("[파일 전송 준비 완료 : " + fileName + "]");});

                message = new Message(fileName, MsgType.DOWNLOAD_START);
                selectionKey.interestOps(SelectionKey.OP_WRITE);
                selector.wakeup();
            } catch (Exception e) {}
        }

        public void sendFile(SelectionKey selectionKey) {
            try {
                String fileName = message.getData();
                ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
                int byteCount = fileChannel.read(byteBuffer);
                if(byteCount == -1) {
                    message = new Message(fileName, MsgType.DOWNLOAD_END);
                } else {
                    byteBuffer.flip();
                    byte[] fileData = new byte[byteBuffer.remaining()];
                    byteBuffer.get(fileData);
                    message = new Message(fileName, MsgType.DOWNLOAD_DO);
                    message.setFileData(fileData);
                }
            } catch (Exception e) {
                Platform.runLater(() -> {displayText("[파일 전송 중 오류 발생]");});
            }
            selectionKey.interestOps(SelectionKey.OP_WRITE);
            selector.wakeup();
        }

        public void endDownload(SelectionKey selectionKey) {
            /* 클라이언트가 무사히 다운로드 했으면 서버도 파일 채널 닫음 */
            try {
                fileChannel.close();
                String fileName = message.getData();
                Platform.runLater(() -> {displayText("[파일 전송 완료: " + fileName + "]");});
            } catch (Exception e) {}
        }

        public void doInvite(SelectionKey selectionKey) {
            String targetId = message.getId();    // 초대받은 유저의 ID
            String roomName = message.getData();  // 초대받은 방 이름
            for(Client client : connections) {
                if( client.user.getId().equals(targetId) && client.room.getName().equals(Room.LOBBY)) {
                    /* 초대 받는 사람에게 초대한 사람의 id와 방이름을 함께 보냄 */
                    client.message = new Message(user.getId(), "", roomName, MsgType.INVITE);
                    SelectionKey key = client.socketChannel.keyFor(selector);
                    key.interestOps(SelectionKey.OP_WRITE);
                    selector.wakeup();
                    return;
                }
            }
            message = new Message("[유저를 찾을 수 없습니다]", MsgType.INVITE_FAILED);
            selectionKey.interestOps(SelectionKey.OP_WRITE);
            selector.wakeup();
        }

        /* 상대가 초대를 수락하면 방에 있는 모든 이에게 메시지 보냄 */
        public void inviteSuccess(SelectionKey selectionKey) {
            String roomName = message.getData();
            String userId = message.getId();      // 초대한 사람의 id
            String targetId = user.getId();       // 초대받은 사람의 id

            List<Client> clientList = findClient(roomName);
            for(Client client : clientList) {
                String data = "[" + userId + " 님이 " + targetId + " 님을 초대하셨습니다]";
                client.message = new Message(data, MsgType.INVITE);
                SelectionKey key = client.socketChannel.keyFor(selector);
                key.interestOps(SelectionKey.OP_WRITE);
            }
            room = findRoom(roomName);
            message.setData(roomName);
            message.setMsgType(MsgType.INVITE_SUCCESS);

            selectionKey.interestOps(SelectionKey.OP_WRITE);
            selector.wakeup();
        }

        /* 상대가 초대 거부시, 초대한 사람에게 따로 메시지를 보냄 */
        public void inviteFailed(SelectionKey selectionKey) {
            String userId = message.getId();      // 초대한 사람의 id
            String roomName = message.getData();
            for(Client client : connections) {
                if(client.user.getId().equals(userId) && client.room.getName().equals(roomName)) {
                    client.message = new Message(user.getId(), "", roomName, MsgType.INVITE_FAILED);
                    SelectionKey key = client.socketChannel.keyFor(selector);
                    key.interestOps(SelectionKey.OP_WRITE);
                    selector.wakeup();
                    return;
                }
            }
        }

        /* 클라이언트가 강제종료 되었을 때 다른 클라이언트에게 알림 */
        public void sendErrorMsg() {
            String roomName = room.getName();
            List<Client> clientList = findClient(roomName);

            if(!roomName.equals(Room.LOBBY) &&
                    (clientList == null || clientList.size() == 0)) {
                rooms.remove(findRoom(roomName));
            } else {
                for(Client client : clientList) {
                    String id = user.getId();
                    String data = "[" + id + "님의 접속이 끊어졌습니다]";
                    client.message = new Message(user.getId(), "", data, MsgType.DISCONNECT);
                    SelectionKey selectionKey = client.socketChannel.keyFor(selector);
                    selectionKey.interestOps(SelectionKey.OP_WRITE);
                }
                selector.wakeup();
            }
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

        /* roomName 이름의 방에 있는 클라이언트 리스트를 리턴 */
        public List<Client> findClient(String roomName) {
            List<Client> clients = new Vector<Client>();
            for(Client client : connections) {
                Room room = client.room;
                if(room != null && room.getName().equals(roomName))
                    clients.add(client);
            }
            return clients;
        }

        /* roomName 방에 있는 다른 유저들이 방 목록, 유저 목록 새로고침하게 만듬 (현재(this) 클라이언트는 제외) */
        public void refresh(String roomName) {
            List<Client> clientList = findClient(Room.LOBBY);
            if(clientList != null)
                for(Client client : clientList) {
                    if(client == this)
                        continue;
                    client.message = new Message(MsgType.REFRESH);
                    SelectionKey key = client.socketChannel.keyFor(selector);
                    key.interestOps(SelectionKey.OP_WRITE);
                }
        }
    }

    /************************************************ JavaFx UI ************************************************/
    Stage primaryStage;
    @FXML TextArea txtDisplay;
    @FXML Button btnConn;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        users.add(new User(User.MASTER, "master"));
        rooms.add(new Room(Room.LOBBY, User.MASTER));
        users.add(new User("yoo", "1111"));
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setOnCloseRequest(e -> stopServer());
    }

    public void displayText(String text) {
        txtDisplay.appendText(text + "\n");
    }

    public void handleBtnAction(ActionEvent event) {
        if(btnConn.getText().equals("start"))
            startServer();
        else if(btnConn.getText().equals("stop"))
            stopServer();
    }
}
