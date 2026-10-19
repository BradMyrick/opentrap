package opentrap.proxy;

import java.io.Serializable;

/** Configuration for one listening port. */
public class ProxyListener implements Serializable {
    private static final long serialVersionUID = 1L;

    /** The port the proxy listens on unless told otherwise: the conventional 8080. */
    public static final int DEFAULT_PORT = 8080;

    public boolean running = true;
    public int port = DEFAULT_PORT;
    public boolean loopbackOnly = true;
    public boolean invisible = false;
    public String redirectHost = "";
    public int redirectPort = 0;

    public ProxyListener() {}

    public ProxyListener(int port) { this.port = port; }

    public String redirectLabel() {
        if (redirectHost == null || redirectHost.isBlank()) return "";
        return redirectHost + (redirectPort > 0 ? ":" + redirectPort : "");
    }
}
