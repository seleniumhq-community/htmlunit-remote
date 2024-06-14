// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.selenium.htmlunit;

import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static org.openqa.selenium.remote.http.Route.get;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.htmlunit.BrowserVersion;
import org.htmlunit.WebClient;
import org.htmlunit.WebClientOptions;
import org.junit.After;
import org.junit.AfterClass;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.UnhandledAlertException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.grid.TemplateGridServerCommand.Handlers;
import org.openqa.selenium.grid.config.MapConfig;
import org.openqa.selenium.grid.server.BaseServerOptions;
import org.openqa.selenium.grid.server.Server;
import org.openqa.selenium.net.PortProber;
import org.openqa.selenium.netty.server.NettyServer;
import org.openqa.selenium.remote.Browser;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.http.Contents;
import org.openqa.selenium.remote.http.HttpResponse;
import org.openqa.selenium.remote.http.Route;

public abstract class WebDriverTestCase extends WebTestCase {

    private static final Log LOG = LogFactory.getLog(WebDriverTestCase.class);

    /** The driver cache. */
    protected static final Map<BrowserVersion, WebDriver> WEB_DRIVERS_ = new HashMap<>();
    private static final Executor EXECUTOR_POOL = Executors.newFixedThreadPool(4);

    private static class ServerHolder {
        private static Server<?> INSTANCE;
        
        static {
            INSTANCE = new NettyServer(defaultOptions(), createHandlers().httpHandler);
            INSTANCE.start();
            
            Runtime.getRuntime().addShutdownHook(
                    new Thread() {
                        @Override
                        public void run() {
                            INSTANCE.stop();
                        }
                    });
        }

        private static Handlers createHandlers() {
            return new Handlers(Route.combine(
                    get(TestPage.HOME.path).to(() -> req -> getPageFromResource("ExamplePage.html")),
                    get(TestPage.SIMPLE.path).to(() -> req -> getPageFromResource("SimplePage.html")),
                    get(TestPage.ALERTS.path).to(() -> req -> getPageFromResource("AlertsPage.html")),
                    get(TestPage.FRAME_A.path).to(() -> req -> getPageFromResource("Frame_A.html")),
                    get(TestPage.FRAME_B.path).to(() -> req -> getPageFromResource("Frame_B.html")),
                    get(TestPage.FRAME_C.path).to(() -> req -> getPageFromResource("Frame_C.html")),
                    get(TestPage.FRAME_D.path).to(() -> req -> getPageFromResource("Frame_D.html")),
                    get("/readyz").to(() -> req -> new HttpResponse().setStatus(HTTP_NO_CONTENT))),
                    null);
        }
        
        private static HttpResponse getPageFromResource(final String resource) {
            return new HttpResponse().setContent(Contents.utf8String(_getFileContent(resource)));
        }

    	private static BaseServerOptions defaultOptions() {
    		return new BaseServerOptions(new MapConfig(Map.of("server", Map.of("port", PortProber.findFreePort()))));
    	}
    }
    
    public enum TestPage {
        HOME("/"),
        SIMPLE("/simple"),
        ALERTS("/alerts"),
        FRAME_A("/frame-a"),
        FRAME_B("/frame-b"),
        FRAME_C("/frame-c"),
        FRAME_D("/frame-d");
        
        String path;

        TestPage(String path) {
            this.path = path;
        }
    }
    
    /**
     * The HtmlUnitDriver.
     */
    private HtmlUnitDriver webDriver_;

    /**
     * Configure the driver only once.
     * @return the driver
     */
    protected WebDriver getWebDriver() {
        final BrowserVersion browserVersion = BrowserVersion.BEST_SUPPORTED;
        WebDriver driver = WEB_DRIVERS_.get(browserVersion);
        if (driver == null) {
            try {
                driver = buildWebDriver();
            }
            catch (final IOException e) {
                throw new RuntimeException(e);
            }

            if (isWebClientCached()) {
                WEB_DRIVERS_.put(browserVersion, driver);
            }
        }
        return driver;
    }
    
    public static String testPage(final TestPage page) {
        try {
            return ServerHolder.INSTANCE.getUrl().toURI().resolve(page.path).toString();
        } catch (URISyntaxException e) {
            throw new AssertionError("Failed getting test page web server URI", e);
        }
    }

    /**
     * Closes the drivers.
     * @throws Exception If an error occurs
     */
    @AfterClass
    public static void shutDownAll() throws Exception {
        for (final WebDriver driver : WEB_DRIVERS_.values()) {
            driver.quit();
        }
        WEB_DRIVERS_.clear();
    }

    /**
     * Builds a new WebDriver instance.
     * @return the instance
     * @throws IOException in case of exception
     */
    protected WebDriver buildWebDriver() throws IOException {
        if (webDriver_ == null) {
            final DesiredCapabilities capabilities = new DesiredCapabilities();
            capabilities.setBrowserName(Browser.HTMLUNIT.browserName());
            capabilities.setVersion(getBrowserName(BrowserVersion.BEST_SUPPORTED));
            webDriver_ = new HtmlUnitDriver(capabilities) {
                @Override
                protected WebClient newWebClient(final BrowserVersion version) {
                    final WebClient webClient = super.newWebClient(version);
                    if (isWebClientCached()) {
                        webClient.getOptions().setHistorySizeLimit(0);
                    }

                    final Integer timeout = getWebClientTimeout();
                    if (timeout != null) {
                        webClient.getOptions().setTimeout(timeout.intValue());
                    }
                    return webClient;
                }
            };
            webDriver_.setExecutor(EXECUTOR_POOL);
        }
        return webDriver_;
    }

    private static String getBrowserName(final BrowserVersion browserVersion) {
        if (browserVersion == BrowserVersion.FIREFOX) {
            return browserVersion.getNickname() + '-' + browserVersion.getBrowserVersionNumeric();
        }
        if (browserVersion == BrowserVersion.FIREFOX_ESR) {
            return browserVersion.getNickname() + '-' + browserVersion.getBrowserVersionNumeric();
        }
        return browserVersion.getNickname();
    }

    /**
     * Release resources but DON'T close the browser if we are running with a real browser.
     * Note that HtmlUnitDriver is not cached by default, but that can be configured by {@link #isWebClientCached()}.
     */
    @After
    @Override
    public void releaseResources() {
        final List<String> unhandledAlerts = new ArrayList<>();
        if (webDriver_ != null) {
            UnhandledAlertException ex = null;
            do {
                ex = null;
                try {
                    // getTitle will do an implicit check for open alerts
                    webDriver_.getTitle();
                }
                catch (final NoSuchWindowException e) {
                    // ignore
                }
                catch (final NoSuchSessionException e) {
                    // ignore
                }
                catch (final UnhandledAlertException e) {
                    ex = e;
                    unhandledAlerts.add(e.getMessage());
                }
            }
            while (ex != null);
        }

        super.releaseResources();

        assertTrue("There are still unhandled alerts: " + String.join("; ", unhandledAlerts),
                        unhandledAlerts.isEmpty());
    }

    /**
     * Whether {@link WebClient} is cached or not, defaults to {@code false}.
     *
     * <p>This is needed to be {@code true} for huge test class, as we could run out of sockets.
     *
     * @return whether {@link WebClient} is cached or not
     */
    protected boolean isWebClientCached() {
        return false;
    }

    /**
     * Configure {@link WebClientOptions#getTimeout()}.
     *
     * @return null if unchanged otherwise the timeout as int
     */
    protected Integer getWebClientTimeout() {
        return null;
    }
}
