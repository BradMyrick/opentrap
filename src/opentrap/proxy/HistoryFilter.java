package opentrap.proxy;

import opentrap.core.Bytes;
import opentrap.core.LogEntry;
import opentrap.core.Scope;

import java.io.Serializable;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The display filter applied to a history or site-map table.
 *
 * <p>It holds two independent searches, matching how the commercial tools split
 * the job:
 *
 * <ul>
 *   <li>{@link #urlSearch} is the fast one. It matches the URL only, so typing in
 *       the quick box beside the table narrows thousands of rows instantly.
 *   <li>{@link #searchTerm} is the thorough one, set in the filter popup. It
 *       searches the full text of both the request and the response, so it finds
 *       a value buried in a header or a body, at the cost of being slower.
 * </ul>
 *
 * <p>Everything else is a structural filter: scope, response presence, MIME
 * type, status band and file extension.
 */
public class HistoryFilter implements Serializable {
    private static final long serialVersionUID = 2L;

    // Structural filters.
    public boolean inScopeOnly = false;
    public boolean hideWithoutResponses = false;
    public boolean parameterisedOnly = false;
    public boolean showHtml = true, showScript = true, showXml = true, showJson = true;
    public boolean showCss = true, showImages = true, showOtherText = true, showOtherBinary = true;
    public boolean show2xx = true, show3xx = true, show4xx = true, show5xx = true;
    public String extShowOnly = "";   // comma-separated, empty = any
    public String extHide = "";       // comma-separated, empty = none

    // Fast URL search (the quick box).
    public String urlSearch = "";

    // Thorough full-text search (the filter popup).
    public String searchTerm = "";
    public boolean searchRegex = false;
    public boolean searchCaseSensitive = false;
    public boolean searchNegative = false;

    public transient Scope scope;

    // Cached compiled regex, rebuilt when the term or its flags change.
    private transient Pattern compiled;
    private transient String compiledFor;
    private transient boolean compiledCase;

    public boolean test(LogEntry e) {
        // Fast URL search first: it rejects the most rows for the least work.
        if (urlSearch != null && !urlSearch.isBlank()) {
            String url = e.url() == null ? "" : e.url();
            if (!url.toLowerCase(Locale.ROOT).contains(urlSearch.toLowerCase(Locale.ROOT))) return false;
        }

        if (inScopeOnly && (scope == null || !scope.isInScope(e))) return false;
        if (hideWithoutResponses && e.response() == null) return false;
        if (parameterisedOnly && (e.request() == null || !e.request().hasParameters())) return false;

        int sc = e.status();
        if (sc >= 200 && sc < 300 && !show2xx) return false;
        if (sc >= 300 && sc < 400 && !show3xx) return false;
        if (sc >= 400 && sc < 500 && !show4xx) return false;
        if (sc >= 500 && sc < 600 && !show5xx) return false;

        if (e.response() != null) {
            String m = e.mimeType();
            if (m.equals("HTML") && !showHtml) return false;
            if (m.equals("script") && !showScript) return false;
            if (m.equals("XML") && !showXml) return false;
            if (m.equals("JSON") && !showJson) return false;
            if (m.equals("CSS") && !showCss) return false;
            if (m.equals("images") && !showImages) return false;
            if (m.equals("other text") && !showOtherText) return false;
            if (m.equals("other binary") && !showOtherBinary) return false;
        }

        String ext = e.request() == null ? "" : e.request().fileExtension();
        if (!extShowOnly.isBlank() && !extListContains(extShowOnly, ext)) return false;
        if (!extHide.isBlank() && extListContains(extHide, ext)) return false;

        // Thorough full-text search last: it is the most expensive, and the
        // cheaper filters above have already discarded most rows.
        if (searchTerm != null && !searchTerm.isBlank()) {
            boolean hit = fullTextMatch(e);
            if (searchNegative) hit = !hit;
            if (!hit) return false;
        }
        return true;
    }

    private boolean fullTextMatch(LogEntry e) {
        String hay = (e.request() == null ? "" : Bytes.str(e.request().raw()))
                   + "\n" + (e.response() == null ? "" : Bytes.str(e.response().raw()));
        if (searchRegex) {
            Pattern p = pattern();
            return p != null && p.matcher(hay).find();
        }
        if (searchCaseSensitive) return hay.contains(searchTerm);
        return hay.toLowerCase(Locale.ROOT).contains(searchTerm.toLowerCase(Locale.ROOT));
    }

    private Pattern pattern() {
        if (compiled == null || !searchTerm.equals(compiledFor) || compiledCase != searchCaseSensitive) {
            try {
                compiled = Pattern.compile(searchTerm,
                        searchCaseSensitive ? 0 : Pattern.CASE_INSENSITIVE);
            } catch (Exception ex) {
                compiled = null;
            }
            compiledFor = searchTerm;
            compiledCase = searchCaseSensitive;
        }
        return compiled;
    }

    private static boolean extListContains(String list, String ext) {
        if (ext == null) ext = "";
        for (String part : list.split("[,\\s]+")) {
            String p = part.trim().toLowerCase(Locale.ROOT);
            if (p.startsWith(".")) p = p.substring(1);
            if (!p.isEmpty() && p.equals(ext.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** True when no structural filter and no search is active. */
    public boolean isEverything() {
        return !inScopeOnly && !hideWithoutResponses && !parameterisedOnly
                && showHtml && showScript && showXml && showJson && showCss && showImages
                && showOtherText && showOtherBinary && show2xx && show3xx && show4xx && show5xx
                && extShowOnly.isBlank() && extHide.isBlank()
                && (searchTerm == null || searchTerm.isBlank());
    }

    /** A one-line description of the active structural filter, for the bar. */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        if (inScopeOnly) sb.append("in-scope only; ");
        if (hideWithoutResponses) sb.append("hiding items without responses; ");
        if (parameterisedOnly) sb.append("parameterised only; ");
        if (!allMimeShown()) sb.append("hiding some content types; ");
        if (!allStatusShown()) sb.append("hiding some status codes; ");
        if (!extShowOnly.isBlank()) sb.append("only extensions ").append(extShowOnly).append("; ");
        if (!extHide.isBlank()) sb.append("hiding extensions ").append(extHide).append("; ");
        if (searchTerm != null && !searchTerm.isBlank())
            sb.append(searchNegative ? "not matching \"" : "matching \"").append(searchTerm).append("\"; ");
        if (sb.length() == 0) return "showing all items";
        return sb.substring(0, sb.length() - 2);
    }

    private boolean allMimeShown() {
        return showHtml && showScript && showXml && showJson && showCss
                && showImages && showOtherText && showOtherBinary;
    }

    private boolean allStatusShown() {
        return show2xx && show3xx && show4xx && show5xx;
    }

    public void showAll() {
        inScopeOnly = hideWithoutResponses = parameterisedOnly = false;
        showHtml = showScript = showXml = showJson = showCss = showImages = showOtherText = showOtherBinary = true;
        show2xx = show3xx = show4xx = show5xx = true;
        extShowOnly = extHide = "";
        searchTerm = "";
    }
}
