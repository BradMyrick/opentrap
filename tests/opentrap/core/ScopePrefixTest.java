package opentrap.core;

import opentrap.testkit.Expect;
import opentrap.testkit.Harness;

/**
 * Prefix scope rules: a URL prefix matches everything under it, subdomains
 * only when asked, a bare host matches any scheme and port, and the site
 * map's "remove from scope" either drops the rule or adds an exclusion.
 */
public final class ScopePrefixTest {

    private static final HttpService HTTPS = new HttpService("example.com", 443, true);
    private static final HttpService HTTP = new HttpService("example.com", 80, false);
    private static final HttpService SUB = new HttpService("api.example.com", 443, true);
    private static final HttpService OTHER = new HttpService("example.net", 443, true);

    public void run() {
        Harness.suite("scope prefixes");

        Harness.test("a prefix covers the path under it on that scheme and host", () -> {
            Scope s = new Scope();
            s.include().add(Scope.Rule.prefix("https://example.com/app", false));
            Expect.isTrue(s.isInScope(HTTPS, "/app"), "the prefix itself");
            Expect.isTrue(s.isInScope(HTTPS, "/app/login"), "below it");
            Expect.isTrue(s.isInScope(HTTPS, "/application"), "a plain string prefix");
            Expect.isFalse(s.isInScope(HTTPS, "/admin"), "another path");
            Expect.isFalse(s.isInScope(HTTP, "/app"), "another scheme");
            Expect.isFalse(s.isInScope(SUB, "/app"), "a subdomain without the option");
            Expect.isFalse(s.isInScope(OTHER, "/app"), "another host");
        });

        Harness.test("include subdomains widens the host match", () -> {
            Scope s = new Scope();
            s.include().add(Scope.Rule.prefix("https://example.com", true));
            Expect.isTrue(s.isInScope(SUB, "/"), "api.example.com");
            Expect.isTrue(s.isInScope(HTTPS, "/"), "the host itself");
            Expect.isFalse(s.isInScope(new HttpService("notexample.com", 443, true), "/"), "not a suffix trick");
        });

        Harness.test("a bare host from the scan wizard matches any scheme and port", () -> {
            Scope s = new Scope();
            s.includeHost(HTTPS);
            Expect.isTrue(s.isInScope(HTTPS, "/"), "https");
            Expect.isTrue(s.isInScope(HTTP, "/x"), "http");
            Expect.isTrue(s.isInScope(new HttpService("example.com", 8443, true), "/"), "another port");
            Expect.isFalse(s.isInScope(SUB, "/"), "not subdomains");
        });

        Harness.test("prefixes are normalised and never added twice", () -> {
            Expect.equal(Scope.normalisePrefix(" Example.COM/App "), "https://example.com/App", "scheme added, host lowered, path kept");
            Expect.equal(Scope.normalisePrefix("http://example.com:80/"), "http://example.com", "default port and bare slash dropped");
            Expect.equal(Scope.normalisePrefix("https://example.com:8443/a"), "https://example.com:8443/a", "other port kept");
            Expect.isNull(Scope.normalisePrefix("not a url"), "plain words are not a prefix");
            Scope s = new Scope();
            s.includeUrl(HTTPS, "/app", false);
            s.includeUrl(HTTPS, "/app", false);
            Expect.equal(s.include().size(), 1, "one rule");
            Expect.equal(s.include().get(0).prefix, "https://example.com/app", "as a prefix");
        });

        Harness.test("remove from scope drops an exact rule, else excludes", () -> {
            Scope s = new Scope();
            s.includeUrl(HTTPS, "/", true);
            s.includeUrl(HTTPS, "/app", false);
            s.removeUrl(HTTPS, "/app", false);
            Expect.equal(s.include().size(), 1, "the exact rule went");
            Expect.isTrue(s.isInScope(HTTPS, "/app"), "still in scope through the host rule");
            s.removeUrl(HTTPS, "/app", false);
            Expect.isFalse(s.isInScope(HTTPS, "/app"), "now excluded");
            Expect.isTrue(s.isInScope(HTTPS, "/other"), "the rest of the host stays");
        });

        Harness.test("rules describe themselves", () -> {
            Expect.equal(Scope.Rule.prefix("https://example.com", true).describe(), "https://example.com", "a prefix");
            Expect.equal(new Scope.Rule("any", "", "", "logout").describe(), "file matches logout", "an advanced rule");
        });
    }
}
