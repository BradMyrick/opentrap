package opentrap.core;

import java.io.Serializable;
import java.util.Date;

public class Alert implements Serializable {
    private static final long serialVersionUID = 1L;
    public final Date time = new Date();
    public final String tool;
    public final String message;
    public int repeats = 1;

    public Alert(String tool, String message) {
        this.tool = tool == null ? "suite" : tool;
        this.message = message == null ? "" : message;
    }
}
