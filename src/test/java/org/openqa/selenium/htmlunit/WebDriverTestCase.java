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
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.htmlunit.BrowserVersion;
import org.htmlunit.WebClient;
import org.htmlunit.WebClientOptions;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ComparisonFailure;
import org.openqa.selenium.Alert;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.NoAlertPresentException;
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

    /** Timeout used when waiting for successful bind. */
    public static final int BIND_TIMEOUT = 1000;

    private static Server<?> server;
    
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

	@BeforeClass
	public static void setup() {
		server = new NettyServer(defaultOptions(), createHandlers().httpHandler);
		server.start();
	}
	
	public static Handlers createHandlers() {
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
	
	public static String testPage(final TestPage page) {
		try {
			return server.getUrl().toURI().resolve(page.path).toString();
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
		server.stop();
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

    protected void resizeIfNeeded(final WebDriver driver) {
        final Dimension size = driver.manage().window().getSize();
        if (size.getWidth() != 1272 || size.getHeight() != 768) {
            // only resize if needed because it may be quite expensive
            driver.manage().window().setSize(new Dimension(1272, 768));
        }
    }

    /**
     * Verifies the captured alerts.
     * @param func actual string producer
     * @param expected the expected string
     * @throws Exception in case of failure
     */
    protected void verifyAlerts(final Supplier<String> func, final String expected) throws Exception {
        verifyAlerts(func, expected, DEFAULT_WAIT_TIME);
    }

    /**
     * Verifies the captured alerts.
     * @param func actual string producer
     * @param expected the expected string
     * @param maxWaitTime the maximum time to wait to get the alerts (in millis)
     * @throws Exception in case of failure
     */
    protected void verifyAlerts(final Supplier<String> func, final String expected,
            final long maxWaitTime) throws Exception {
        final long maxWait = System.currentTimeMillis() + maxWaitTime;

        String actual = null;
        while (System.currentTimeMillis() < maxWait) {
            actual = func.get();

            if (StringUtils.equals(expected, actual)) {
                break;
            }

            Thread.sleep(50);
        }

        assertEquals(expected, actual);
    }

    /**
     * Verifies the captured alerts.
     * @param driver the driver instance
     * @param expectedAlerts the expected alerts
     * @throws Exception in case of failure
     */
    protected void verifyAlerts(final WebDriver driver, final String... expectedAlerts) throws Exception {
        verifyAlerts(DEFAULT_WAIT_TIME, driver, expectedAlerts);
    }

    /**
     * Verifies the captured alerts.
     *
     * @param maxWaitTime the maximum time to wait for the expected alert to be found
     * @param driver the driver instance
     * @param expectedAlerts the expected alerts
     * @throws Exception in case of failure
     */
    protected void verifyAlerts(final long maxWaitTime, final WebDriver driver, final String... expectedAlerts)
            throws Exception {
        final List<String> actualAlerts = getCollectedAlerts(maxWaitTime, driver, expectedAlerts.length);

        assertEquals(expectedAlerts.length, actualAlerts.size());
        assertEquals(expectedAlerts, actualAlerts);
    }

    /**
     * Gets the alerts collected by the driver.
     * Note: it currently works only if no new page has been loaded in the window
     * @param driver the driver
     * @return the collected alerts
     * @throws Exception in case of problem
     */
    protected List<String> getCollectedAlerts(final WebDriver driver) throws Exception {
        return getCollectedAlerts(driver, getExpectedAlerts().length);
    }

    /**
     * Gets the alerts collected by the driver.
     * Note: it currently works only if no new page has been loaded in the window
     * @param driver the driver
     * @param alertsLength the expected length of Alerts
     * @return the collected alerts
     * @throws Exception in case of problem
     */
    protected List<String> getCollectedAlerts(final WebDriver driver, final int alertsLength) throws Exception {
        return getCollectedAlerts(DEFAULT_WAIT_TIME, driver, alertsLength);
    }

    /**
     * Gets the alerts collected by the driver.
     * Note: it currently works only if no new page has been loaded in the window
     * @param maxWaitTime the maximum time to wait to get the alerts (in millis)
     * @param driver the driver
     * @param alertsLength the expected length of Alerts
     * @return the collected alerts
     * @throws Exception in case of problem
     */
    protected List<String> getCollectedAlerts(final long maxWaitTime, final WebDriver driver, final int alertsLength)
            throws Exception {
        final List<String> collectedAlerts = new ArrayList<>();

        long maxWait = System.currentTimeMillis() + maxWaitTime;

        while (collectedAlerts.size() < alertsLength && System.currentTimeMillis() < maxWait) {
            try {
                final Alert alert = driver.switchTo().alert();
                final String text = alert.getText();

                collectedAlerts.add(text);
                alert.accept();

                // handling of alerts requires some time
                // at least for tests with many alerts we have to take this into account
                maxWait += 100;
            }
            catch (final NoAlertPresentException e) {
                Thread.sleep(10);
            }
        }

        return collectedAlerts;
    }

    /**
     * Asserts the current title is equal to the expectation string.
     * @param webdriver the driver in use
     * @param expected the expected object
     * @throws Exception in case of failure
     */
    protected void assertTitle(final WebDriver webdriver, final String expected) throws Exception {
        final long maxWait = System.currentTimeMillis() + DEFAULT_WAIT_TIME;

        while (true) {
            final String title = webdriver.getTitle();
            try {
                assertEquals(expected, title);
                return;
            }
            catch (final ComparisonFailure e) {
                if (expected.length() <= title.length()
                        || System.currentTimeMillis() > maxWait) {
                    throw e;
                }
                Thread.sleep(10);
            }
        }
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
    
    private static BaseServerOptions defaultOptions() {
        return new BaseServerOptions(
            new MapConfig(
                Map.of("server", Map.of("port", PortProber.findFreePort()))));
      }
}
