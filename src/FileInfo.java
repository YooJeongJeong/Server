import java.io.Serializable;
import java.text.DecimalFormat;

public class FileInfo implements Serializable{
    static final long serialVersionUID = 1L;

    private String name;
    private String size;

    FileInfo(String name, long size) {
        this.name = name;
        this.size = convertSize(size);
    }

    public String convertSize(long size) {
        float kb = 1024;
        float mb = 1024 * kb;
        float gb = 1024 * mb;

        DecimalFormat df = new DecimalFormat("0.00");
        return df.format(size/gb) + " GB";
    }
}