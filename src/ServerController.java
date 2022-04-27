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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ResourceBundle;
import javafx.application.Platform;

import java.net.InetSocketAddress;
import java.util.*;

public class ServerController implements Initializable {
    Selector selector;
    ServerSocketChannel serverSocketChannel;
    List<Client> connections = new Vector<Client>();    // 연결된 클라이언트
    List<User> users = new Vector<User>();              // 회원가입된 유저 리스트
    List<Room> rooms = new Vector<Room>();              // 생성된 방 리스트

    public void startServer() {
        try {
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

        Thread thread = new Thread(() -> {
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
            if(selector != null && selector.isOpen())
                selector.close();

            Platform.runLater(() -> {
                displayText("[서버 멈춤]");
                btnConn.setText("start");
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void accept() {
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
            e.printStackTrace();
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
        String fileName;
        int fileByteSize;

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
                    case INFO:
                        doInfo(selectionKey);       break;
                    case MAKE:
                        doMakeRoom(selectionKey);   break;
                    case UPLOAD:
                        receiveFile();  break;
                    default:
                        Platform.runLater(()->displayText("[Error: unexpected message type]"));
                }

                String msg = "[" + message.getMsgType() + " 요청 처리: " + socketChannel.getRemoteAddress() + ": " +
                        Thread.currentThread().getName() + "]";
                Platform.runLater(()->displayText(msg));
            } catch (Exception e) {
                try {
                    e.printStackTrace();
                    connections.remove(this);
                    String msg = "[클라이언트 통신 안됨: " +
                            socketChannel.getRemoteAddress() + ": " +
                            Thread.currentThread().getName() + "]";
                    Platform.runLater(()->displayText(msg));
                    socketChannel.close();
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
                        socketChannel.close();
                        Platform.runLater(()->{displayText(message.getData());});
                        break;
                    default:
                        selectionKey.interestOps(SelectionKey.OP_READ);
                        selector.wakeup();
                }
            } catch (Exception e) {
                try {
                    e.printStackTrace();
                    String message = "[클라이언트 통신 안됨: " +
                            socketChannel.getRemoteAddress() + ": " +
                            Thread.currentThread().getName() + "]";
                    Platform.runLater(()->displayText(message));
                    connections.remove(this);
                    socketChannel.close();
                } catch(Exception e2) {}
            }
        }

        public void doLogin(SelectionKey selectionKey) {
            user = findUser(message.getId());
            if(user != null) {
                if(user.getPw().equals(message.getPw())) {
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
                String roomName = room.getName();
                List<Client> clientList = findClient(roomName);
                room = findRoom(Room.LOBBY);
                for(Client client : clientList) {
                    String data = "[" + user.getId() + " 님이 퇴장하셨습니다]";
                    client.message = new Message(user.getId(), "", data, MsgType.EXIT);
                    SelectionKey key = client.socketChannel.keyFor(selector);
                    key.interestOps(SelectionKey.OP_WRITE);
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

        public void receiveFile() {
            try {
                /* 파일 채널이 닫혀 있다면, 채널을 열고 파일을 쓸 준비를 한다 */
                if(fileChannel == null || !fileChannel.isOpen()) {
                    fileName = message.getData();
                    String filePath = "file" + File.separator + fileName;

                    Path path = Paths.get(filePath);
                    Files.createDirectories(path.getParent());

                    fileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);

                    String data = "[파일 업로드 시작 : " + fileName + "]";
                    Platform.runLater(() -> {displayText(data);});
                    return;
                }
                /* messgae의 data 값이 null이면 클라이언트가 파일 업로드를 끝냈다는 뜻이므로 채널을 닫는다 */
                if(message.getData() == null) {
                    String data = "[파일 업로드 완료 : " + fileName + "]";
                    Platform.runLater(() -> {displayText(data);});
                    fileChannel.close();
                    return;
                }
                String data = message.getData();
                Charset charset = Charset.defaultCharset();
                ByteBuffer byteBuffer = charset.encode(data);
                fileChannel.write(byteBuffer);
            } catch (Exception e) {
                Platform.runLater(() -> {displayText("[파일 쓰는 중 오류 발생]");});
                e.printStackTrace();
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
