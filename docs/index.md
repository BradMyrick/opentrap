---
layout: default
title: OpenTRAP
---

<section class="hero"><div class="wrap">
  <div class="htext">
    <h1>OpenTRAP</h1>
    <div class="sub">A free, open-source alternative to Burp Suite</div>
    <p class="lead">Capture, inspect, replay and tamper with the traffic between a browser and a web application, then crawl and audit it for real vulnerabilities. One self-contained download, no licence gate, powered by the OWASP ZAP engine.</p>
    <div class="cta">
      <a class="btn btn-primary" href="https://github.com/opentrap/opentrap/releases/latest/download/opentrap-x86_64.appimage">&#8681;&nbsp; Download for Linux</a>
      <a class="btn btn-ghost" href="https://github.com/opentrap/opentrap/releases/latest">Go straight to releases &rarr;</a>
    </div>
  </div>
  <div class="hshot">
    <a class="shot" href="images/dashboard.png"><img src="images/dashboard.png" alt="OpenTRAP dashboard with a crawl finished, an active audit running, and the live table of the most serious vulnerabilities found"></a>
  </div>
</div></section>

<section class="band" id="features"><div class="wrap">
  <h2>Everything in one window</h2>
  <p class="lead">A proxy-first workspace with the whole toolset one click away, built around a detection engine that holds up.</p>
  <div class="grid">
    <a class="shot" href="images/proxy.png"><img src="images/proxy.png" alt="OpenTRAP Proxy tab with interception and the built-in Chromium browser routed through it"></a>
    <ul class="checks">
      <li><span class="ic">{% include check.html %}</span><span><b>A complete proxy-first suite</b>Intercept, HTTP history, repeater, intruder, sequencer, decoder, comparer, an out-of-band collaborator and a full traffic logger.</span></li>
      <li><span class="ic">{% include check.html %}</span><span><b>Scanning that holds up</b>Crawl and audit for real vulnerabilities on the proven OWASP ZAP engine, with findings in a clear issue view and the evidence highlighted.</span></li>
      <li><span class="ic">{% include check.html %}</span><span><b>Guided from the start</b>Open or create a project at launch, then drive crawl-and-audit scans through a step-by-step wizard.</span></li>
      <li><span class="ic">{% include check.html %}</span><span><b>One self-contained file</b>Every release is a single Linux AppImage with its own runtime and the scan engine built in. Download it, run it, scan.</span></li>
      <li><span class="ic">{% include check.html %}</span><span><b>Genuinely open</b>Licensed under the GNU AGPL v3.0, so it stays open and hackable, with no licence gate on its core capabilities.</span></li>
    </ul>
  </div>
</div></section>

<section class="pad" id="screenshots"><div class="wrap">
  <div class="section-head">
    <h2>See it in action</h2>
    <p>Every tool lives in one window. Click any screenshot for the full-size image.</p>
  </div>
  <div class="gallery">
    <a class="card" href="images/target.png"><img src="images/target.png" alt="Target tab: site map, request list and issues with a full advisory"><div class="cap"><b>Target</b><span>Site map and scope on the left, every request in the middle, every issue on the right with its advisory, request and response beside it.</span></div></a>
    <a class="card" href="images/proxy.png"><img src="images/proxy.png" alt="Proxy tab with interception and the built-in browser"><div class="cap"><b>Proxy &amp; built-in browser</b><span>One click opens a bundled Chromium, already routed through the proxy and trusting its certificate authority. No certificate to install.</span></div></a>
    <a class="card" href="images/collaborator.png"><img src="images/collaborator.png" alt="Collaborator tab with captured out-of-band interactions"><div class="cap"><b>Collaborator</b><span>Generate a payload and watch the out-of-band DNS and HTTP callbacks arrive, each on record, so blind vulnerabilities are proven, not guessed.</span></div></a>
    <a class="card" href="images/intruder.png"><img src="images/intruder.png" alt="Intruder tab running a sniper attack with the live results table"><div class="cap"><b>Intruder</b><span>Mark positions, pick an attack type, drive a payload set through them. The live table shows status, length and timing per request.</span></div></a>
    <a class="card" href="images/repeater.png"><img src="images/repeater.png" alt="Repeater tab with request and response in numbered tabs"><div class="cap"><b>Repeater</b><span>Edit and reissue a request, one numbered tab at a time, with the full response beside it. The quickest way to probe an endpoint by hand.</span></div></a>
    <a class="card" href="images/logger.png"><img src="images/logger.png" alt="Logger tab with a filterable table of every request"><div class="cap"><b>Logger</b><span>A complete, filterable record of every request OpenTRAP and the scan engine make, tagged by the tool that sent it. Nothing happens behind your back.</span></div></a>
    <a class="card" href="images/decoder.png"><img src="images/decoder.png" alt="Decoder tab with a transform pipeline"><div class="cap"><b>Decoder</b><span>Build a pipeline of URL, Base64, hex and hash transforms in either direction, or let smart decode work out the encoding for you.</span></div></a>
    <a class="card" href="images/about.png"><img src="images/about.png" alt="About OpenTRAP dialog"><div class="cap"><b>About</b><span>The build, the scan engine in use and where it comes from, the runtime and the licence, stated plainly. OpenTRAP is open about what is inside it.</span></div></a>
  </div>
</div></section>

<section class="pad soft" id="get-started"><div class="wrap">
  <div class="section-head">
    <h2>Get started</h2>
    <p>One file. No Java to install, no parts missing: the runtime and the scan engine are inside it and set themselves up on first use.</p>
  </div>
  <div class="steps">
    <p style="text-align:center;margin-bottom:24px"><a class="btn btn-primary" href="https://github.com/opentrap/opentrap/releases/latest/download/opentrap-x86_64.appimage">&#8681;&nbsp; Download the latest AppImage</a></p>
    <p>Or fetch it from a terminal:</p>
    <pre><code>wget https://github.com/opentrap/opentrap/releases/latest/download/opentrap-x86_64.appimage</code></pre>
    <p>make it executable:</p>
    <pre><code>chmod +x opentrap-x86_64.appimage</code></pre>
    <p>and run it:</p>
    <pre><code>./opentrap-x86_64.appimage</code></pre>
    <p>You can also double-click the downloaded file in your file manager. The optional testing browser is an in-app download, offered only when you want it, so the release stays small.</p>
  </div>
</div></section>

<section class="pad"><div class="wrap">
  <div class="section-head"><h2>Frequently asked</h2></div>
  <div class="faq">
    <div class="q"><b>Is there a free, open-source alternative to Burp Suite?</b><span>Yes. OpenTRAP offers the intercepting-proxy workflow Burp users know, with Proxy, Repeater, Intruder, Collaborator, Sequencer, Decoder and Comparer, under the AGPL-3.0 with no paid tier.</span></div>
    <div class="q"><b>How does OpenTRAP relate to OWASP ZAP?</b><span>It uses the OWASP ZAP engine for crawling and scanning and puts a familiar, recognisable interface on top: ZAP's detection in a proxy-first workspace.</span></div>
    <div class="q"><b>Which platforms does it run on?</b><span>A single self-contained Linux AppImage today, with the Java runtime and the scan engine inside the file.</span></div>
    <div class="q"><b>Is it really free?</b><span>Yes, released under the GNU AGPL v3.0, with every capability open and nothing reserved for a paid edition.</span></div>
  </div>
</div></section>

<section class="pad soft" id="software-freedom"><div class="wrap">
  <div class="section-head">
    <h2>Supporting software freedom</h2>
    <p>OpenTRAP is free and open source software. If you find it useful, consider supporting the organisations that make free and open source software possible.</p>
  </div>
  <ul class="orgs">
    <li><img src="images/orgs/fsf.svg" alt=""><div><a href="https://www.fsf.org/"><b>Free Software Foundation</b></a><div class="d">Steward of the GNU Project and the GPL family of licences.</div></div></li>
    <li><img src="images/orgs/osi.svg" alt=""><div><a href="https://opensource.org/"><b>Open Source Initiative</b></a><div class="d">Maintains the Open Source Definition and approves open source licences.</div></div></li>
    <li><img src="images/orgs/sfc.svg" alt=""><div><a href="https://sfconservancy.org/"><b>Software Freedom Conservancy</b></a><div class="d">Legal and fiscal home for free software projects, and the main body enforcing the GPL.</div></div></li>
    <li><img src="images/orgs/fsfe.svg" alt=""><div><a href="https://fsfe.org/"><b>Free Software Foundation Europe</b></a><div class="d">Policy and advocacy for software freedom in Europe.</div></div></li>
    <li><img src="images/orgs/eff.svg" alt=""><div><a href="https://www.eff.org/"><b>Electronic Frontier Foundation</b></a><div class="d">Digital rights, including software freedom, DRM and right to repair.</div></div></li>
  </ul>
</div></section>
