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

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof  User) {
            User user = (User) obj;
            return id.equals(user.id) && pw.equals(user.pw);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return id.hashCode() + pw.hashCode();
    }
}
