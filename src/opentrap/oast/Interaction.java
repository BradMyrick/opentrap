package opentrap.oast;

/**
 * One out-of-band interaction recorded by the collaborator server: a DNS, HTTP
 * or SMTP callback that a target made to a payload hostname we planted.
 *
 * <p>The presence of an interaction is the proof. If a target resolves or
 * requests a hostname that only our scanner knew about, the target acted on our
 * input in a way that reached the network, which is exactly what a blind
 * vulnerability looks like.
 */
public final class Interaction {

    private final String protocol;      // dns, http, smtp, ...
    private final String fullId;        // the full subdomain that was contacted
    private final String remoteAddress; // who made the callback
    private final String timestamp;     // server-supplied time
    private final String rawRequest;    // the raw request, query or conversation
    private final String rawResponse;   // what the server sent back (http, smtp)
    private final String queryType;     // DNS record type (A, AAAA, ...), if any
    private final long receivedAt;      // when we polled it, for ordering

    public Interaction(String protocol, String fullId, String remoteAddress,
                       String timestamp, String rawRequest, String rawResponse, String queryType) {
        this.protocol = protocol == null ? "" : protocol;
        this.fullId = fullId == null ? "" : fullId;
        this.remoteAddress = remoteAddress == null ? "" : remoteAddress;
        this.timestamp = timestamp == null ? "" : timestamp;
        this.rawRequest = rawRequest == null ? "" : rawRequest;
        this.rawResponse = rawResponse == null ? "" : rawResponse;
        this.queryType = queryType == null ? "" : queryType;
        this.receivedAt = System.currentTimeMillis();
    }

    public String protocol()      { return protocol; }
    public String fullId()        { return fullId; }
    public String remoteAddress() { return remoteAddress; }
    public String timestamp()     { return timestamp; }
    public String rawRequest()    { return rawRequest; }
    public String rawResponse()   { return rawResponse; }
    public String queryType()     { return queryType; }
    public long receivedAt()      { return receivedAt; }

    public boolean isDns()  { return "dns".equalsIgnoreCase(protocol); }
    public boolean isHttp() { return "http".equalsIgnoreCase(protocol); }
    public boolean isSmtp() { return "smtp".equalsIgnoreCase(protocol); }

    /**
     * The per-probe nonce: the part of the contacted hostname after the shared
     * correlation id. A check that planted a payload matches on this to know
     * the callback was its own.
     */
    public String nonce(String correlationId) {
        String host = fullId;
        int dot = host.indexOf('.');
        if (dot > 0) host = host.substring(0, dot);
        if (correlationId != null && host.startsWith(correlationId)) {
            return host.substring(correlationId.length());
        }
        return host;
    }

    @Override public String toString() {
        return protocol.toUpperCase() + " from " + remoteAddress + " -> " + fullId;
    }
}
