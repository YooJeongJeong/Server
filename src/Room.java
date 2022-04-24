import java.io.Serializable;
import java.util.List;
import java.util.Vector;

public class Room implements Serializable {
    static final long serialVersionUID = 1L;

    static final String LOBBY = "lobby";

    private String name;
    private String owner;
    private int status;

    transient private List<Client> connections = new Vector<Client>();    // 현재 이 방에 접속중인 클라이언트

    Room(String name, String owner) {
        this.name = name;
        this.owner = owner;
    }

    public void addClient(Client client) {
        connections.add(client);
        refresh();
    }

    public void removeClient(Client client) {
        connections.remove(client);
        refresh();
    }

    public void refresh() {
        status = connections.size();
    }

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public List<Client> getConnections() {
        return connections;
    }

    public int getStatus() {
        return status;
    }
}
