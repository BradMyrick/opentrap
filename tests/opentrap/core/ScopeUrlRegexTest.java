package opentrap.core;

import java.util.regex.Pattern;

import opentrap.testkit.Expect;
import opentrap.testkit.Harness;

/**
 * The scope handed to the scan engine is a set of whole-URL regexes built from
 * the suite's rules. The engine matches them with {@code matches()} over the
 * full URL, so that is what is checked here: a URL the rule covers must match,
 * and one it does not must not.
 */
public final class ScopeUrlRegexTest {

    private static boolean hits(Scope.Rule r, String url) {
        String re = r.urlRegex();
        return re != null && Pattern.compile(re).matcher(url).matches();
    }

    public void run() {
        Harness.suite("Scope rules as engine URL regexes");

        Harness.test("a host prefix covers the host on either default port, not other hosts", () -> {
            Scope.Rule r = Scope.Rule.prefix("http://testaspnet.vulnweb.com", false);
            Expect.isTrue(hits(r, "http://testaspnet.vulnweb.com/"), "the root");
            Expect.isTrue(hits(r, "http://testaspnet.vulnweb.com/ReadNews.aspx?id=2"), "a page with a query");
            Expect.isTrue(hits(r, "http://testaspnet.vulnweb.com:80/x"), "the default port spelled out");
            Expect.isFalse(hits(r, "https://testaspnet.vulnweb.com/"), "another scheme");
            Expect.isFalse(hits(r, "http://example.com/"), "another host");
            Expect.isFalse(hits(r, "http://testaspnet.vulnweb.com.evil.example/"), "a host that starts the same");
            Expect.isFalse(hits(r, "http://www.testaspnet.vulnweb.com/"), "a subdomain when not asked for");
        });

        Harness.test("include subdomains admits them and a path prefix stays a prefix", () -> {
            Scope.Rule r = Scope.Rule.prefix("https://example.com/app", true);
            Expect.isTrue(hits(r, "https://example.com/app"), "the prefix itself");
            Expect.isTrue(hits(r, "https://example.com/app/login"), "below it");
            Expect.isTrue(hits(r, "https://example.com/application"), "a plain string prefix");
            Expect.isTrue(hits(r, "https://api.example.com/app/x"), "a subdomain");
            Expect.isFalse(hits(r, "https://example.com/admin"), "another path");
            Expect.isFalse(hits(r, "https://example.com/"), "the root");
        });

        Harness.test("a scheme-less host prefix takes any scheme and port", () -> {
            Scope s = new Scope();
            s.includeHost(HttpService.parse("https://shop.example:8443/"));
            Scope.Rule r = s.include().get(0);
            Expect.isTrue(hits(r, "https://shop.example:8443/cart"), "the port it was seen on");
            Expect.isTrue(hits(r, "http://shop.example/"), "plain http");
            Expect.isFalse(hits(r, "http://shop.example.evil/"), "another host");
        });

        Harness.test("the default logout excludes match anywhere in the path, on any host", () -> {
            Scope s = new Scope();
            Scope.Rule logout = s.exclude().get(0);
            Expect.isTrue(hits(logout, "http://testaspnet.vulnweb.com/logout.aspx"), "a logout page");
            Expect.isTrue(hits(logout, "https://a.example/account/Logout?next=/"), "deeper, other case, with a query");
            Expect.isFalse(hits(logout, "http://testaspnet.vulnweb.com/login.aspx"), "a login page");
            Expect.isFalse(hits(logout, "http://logout.example/"), "the word in the host only");
        });

        Harness.test("an advanced rule keeps a literal host and an anchored file pattern", () -> {
            Scope.Rule r = new Scope.Rule("https", "^\\Qexample.com\\E$", "", "^/admin");
            Expect.isTrue(hits(r, "https://example.com/admin/users"), "under the path");
            Expect.isFalse(hits(r, "https://example.com/x/admin"), "the path elsewhere, anchored");
            Expect.isFalse(hits(r, "http://example.com/admin"), "the wrong scheme");
            Expect.isFalse(hits(r, "https://other.example/admin"), "another host");
        });

        Harness.test("a disabled rule yields nothing", () -> {
            Scope.Rule r = Scope.Rule.prefix("https://example.com", false);
            r.enabled = false;
            Expect.isNull(r.urlRegex(), "regex of a disabled rule");
        });
    }
}
