import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

public class ServerController implements Initializable {

    Selector selector;
    ServerSocketChannel serverSocketChannel;
    List<Client> connections = new Vector<Client>();    // 연결된 클라이언트
    List<User> users = new Vector<User>();              // 회원가입된 유저
    List<Room> rooms = new Vector<Room>();              // 전체 방 정보

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

                    String message = null;
                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    for(SelectionKey selectionKey : selectedKeys) {
                        if(selectionKey.isAcceptable()){
                            accept(selectionKey);
                        }
                        else if(selectionKey.isReadable()) {
                            Client client = (Client)selectionKey.attachment();
                            message = client.receive(selectionKey);
                        }
                        else if(selectionKey.isWritable()) {
                            Client client = (Client)selectionKey.attachment();
                            message = client.send(selectionKey);
                        }
                        selectedKeys.remove(selectionKey);
                    }
                    if(message != null) {
                        String finalMessage = message;
                        Platform.runLater(() -> displayText(finalMessage));
                    }
                } catch (Exception e) {
                    if(serverSocketChannel.isOpen())
                        stopServer();
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
            for(Client client : connections) {
                client.socketChannel.close();
                connections.remove(client);
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

    public void accept(SelectionKey selectionKey) {
        try {
            SocketChannel socketChannel = serverSocketChannel.accept();

            String message = "[연결 수락: " + socketChannel.getRemoteAddress() + ": " +
                    Thread.currentThread().getName() + "]";
            Platform.runLater(() -> displayText(message));

            Client client = new Client(socketChannel, selector, connections, rooms, users);
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

    Stage primaryStage;

    @FXML TextArea txtDisplay;
    @FXML Button btnConn;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        rooms.add(new Room(Room.LOBBY, User.MASTER));
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
