import java.io.Serializable;

public class User implements Serializable {
    static final long serialVersionUID = 1L;

    static final String MASTER = "master";

    private String id;
    private String pw;

    User(String id, String pw) {
        this.id = id;
        this.pw = pw;
    }

    public String getId() {
        return id;
    }

    public String getPw() {
        return pw;
    }
}
