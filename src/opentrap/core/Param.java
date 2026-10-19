package opentrap.core;

import java.io.Serializable;

/** A single request parameter with its byte offsets in the message. */
public final class Param implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type { URL, BODY, COOKIE, MULTIPART, XML, JSON, HEADER }

    private final String name;
    private final String value;
    private final Type type;
    private final int nameStart, nameEnd, valueStart, valueEnd;

    public Param(String name, String value, Type type,
                 int nameStart, int nameEnd, int valueStart, int valueEnd) {
        this.name = name; this.value = value; this.type = type;
        this.nameStart = nameStart; this.nameEnd = nameEnd;
        this.valueStart = valueStart; this.valueEnd = valueEnd;
    }

    public String name()   { return name; }
    public String value()  { return value; }
    public Type type()     { return type; }
    public int nameStart() { return nameStart; }
    public int nameEnd()   { return nameEnd; }
    public int valueStart(){ return valueStart; }
    public int valueEnd()  { return valueEnd; }

    public String typeLabel() {
        return switch (type) {
            case URL -> "URL param";
            case BODY -> "body param";
            case COOKIE -> "cookie";
            case MULTIPART -> "multipart param";
            case XML -> "XML parameter";
            case JSON -> "JSON parameter";
            case HEADER -> "HTTP header";
        };
    }

    @Override public String toString() { return typeLabel() + " " + name; }
}
