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

package org.openqa.selenium.htmlunit.remote;

import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.util.stream.Collectors.toList;
import static org.openqa.selenium.remote.http.Contents.asJson;
import static org.openqa.selenium.remote.http.Contents.fromJson;
import static org.openqa.selenium.remote.http.Route.delete;
import static org.openqa.selenium.remote.http.Route.get;
import static org.openqa.selenium.remote.http.Route.post;
import static org.openqa.selenium.grid.config.StandardGridRoles.HTTPD_ROLE;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openqa.selenium.BuildInfo;
import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.InvalidArgumentException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.NoSuchFrameException;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.Point;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.UnsupportedCommandException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriver.Timeouts;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WindowType;
import org.openqa.selenium.cli.CliCommand;
import org.openqa.selenium.grid.TemplateGridServerCommand;
import org.openqa.selenium.grid.config.Config;
import org.openqa.selenium.grid.config.MapConfig;
import org.openqa.selenium.grid.config.Role;
import org.openqa.selenium.grid.server.Server;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.htmlunit.options.HtmlUnitDriverOptions;
import org.openqa.selenium.htmlunit.remote.ActionsCoercer.ActionsWrapper;
import org.openqa.selenium.internal.Require;
import org.openqa.selenium.json.TypeToken;
import org.openqa.selenium.remote.ErrorCodec;
import org.openqa.selenium.remote.NewSessionPayload;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;
import org.openqa.selenium.remote.http.Route;
import com.google.auto.service.AutoService;

@AutoService(CliCommand.class)
public class HtmlUnitDriverServer extends TemplateGridServerCommand {
    
    public static final Role DRIVER_BINARY_ROLE = Role.of("driver-binary");
    
    private static final Type MAP_OF_LONGS = new TypeToken<Map<String, Long>>() {}.getType();
    private static final Type MAP_OF_OBJECTS = new TypeToken<Map<String, Object>>() {}.getType();
    private static final Type MAP_OF_STRINGS = new TypeToken<Map<String, String>>() {}.getType();
    private static final Type MAP_OF_INTEGERS = new TypeToken<Map<String, Integer>>() {}.getType();
    private static final Type MAP_OF_ACTIONS = new TypeToken<Map<String, ActionsCoercer>>() {}.getType();
    private static final Type MAP_OF_COOKIES = new TypeToken<Map<String, CookieCoercer>>() {}.getType();
    
    private static final Logger LOG = Logger.getLogger(HtmlUnitDriverServer.class.getName());

    private Map<String, HtmlUnitDriver> driverMap = new ConcurrentHashMap<>();
    
    @Override
    public String getName() {
        return DRIVER_BINARY_ROLE.getRoleName();
    }

    @Override
    public String getDescription() {
        return "Adds this server as a RemoteWebDriver in the Selenium Grid";
    }

    @Override
    public Set<Role> getConfigurableRoles() {
        return Set.of(HTTPD_ROLE, DRIVER_BINARY_ROLE);
    }

    @Override
    public Set<Object> getFlagObjects() {
        return Collections.emptySet();
    }
    
    @Override
    protected Handlers createHandlers(final Config config) {
        return new Handlers(Route.combine(
                post("/session").to(() -> new NewSession(this)),
                delete("/session/{sessionId}").to(params -> new DeleteSession(this, sessionIdFrom(params))),
                get("/session/{sessionId}/timeouts").to(params -> new GetTimeouts(this, sessionIdFrom(params))),
                post("/session/{sessionId}/timeouts").to(params -> new SetTimeouts(this, sessionIdFrom(params))),
                post("/session/{sessionId}/url").to(params -> new NavigateTo(this, sessionIdFrom(params))),
                get("/session/{sessionId}/url").to(params -> new GetCurrentUrl(this, sessionIdFrom(params))),
                post("/session/{sessionId}/back").to(params -> new NavigateBack(this, sessionIdFrom(params))),
                post("/session/{sessionId}/forward").to(params -> new NavigateForward(this, sessionIdFrom(params))),
                post("/session/{sessionId}/refresh").to(params -> new RefreshSession(this, sessionIdFrom(params))),
                get("/session/{sessionId}/title").to(params -> new GetTitle(this, sessionIdFrom(params))),
                get("/session/{sessionId}/window").to(params -> new GetWindowHandle(this, sessionIdFrom(params))),
                delete("/session/{sessionId}/window").to(params -> new CloseWindow(this, sessionIdFrom(params))),
                post("/session/{sessionId}/window").to(params -> new SwitchToWindow(this, sessionIdFrom(params))),
                post("/session/{sessionId}/window/new").to(params -> new SwitchToNewWindow(this, sessionIdFrom(params))),
                get("/session/{sessionId}/window/handles").to(params -> new GetWindowHandles(this, sessionIdFrom(params))),
                post("/session/{sessionId}/frame").to(params -> new SwitchToFrame(this, sessionIdFrom(params))),
                post("/session/{sessionId}/frame/parent").to(params -> new SwitchToParentFrame(this, sessionIdFrom(params))),
                get("/session/{sessionId}/window/rect").to(params -> new GetWindowRect(this, sessionIdFrom(params))),
                post("/session/{sessionId}/window/rect").to(params -> new SetWindowRect(this, sessionIdFrom(params))),
                post("/session/{sessionId}/window/maximize").to(params -> new MaximizeWindow(this, sessionIdFrom(params))),
                post("/session/{sessionId}/window/minimize")
                        .to(() -> req -> errorForException(new UnsupportedCommandException("Cannot minimize window"))),
                post("/session/{sessionId}/window/fullscreen").to(params -> new FullscreenWindow(this, sessionIdFrom(params))),
                get("/session/{sessionId}/element/active").to(params -> new GetActiveElement(this, sessionIdFrom(params))),
                post("/session/{sessionId}/element").to(params -> new FindElement(this, sessionIdFrom(params))),
                post("/session/{sessionId}/elements").to(params -> new FindElements(this, sessionIdFrom(params))),
                post("/session/{sessionId}/element/{elementId}/element")
                        .to(params -> new FindElementFromElement(this, sessionIdFrom(params), elementIdFrom(params))),
                post("/session/{sessionId}/element/{elementId}/elements")
                        .to(params -> new FindElementsFromElement(this, sessionIdFrom(params), elementIdFrom(params))),
                get("/session/{sessionId}/element/{elementId}/selected")
                        .to(params -> new IsElementSelected(this, sessionIdFrom(params), elementIdFrom(params))),
                get("/session/{sessionId}/element/{elementId}/attribute/{name}")
                        .to(params -> new GetElementDomAttribute(this, sessionIdFrom(params), elementIdFrom(params), nameFrom(params))),
                get("/session/{sessionId}/element/{elementId}/property/{name}")
                        .to(params -> new GetElementDomProperty(this, sessionIdFrom(params), elementIdFrom(params), nameFrom(params))),
                get("/session/{sessionId}/element/{elementId}/css/{name}")
                        .to(params -> new GetElementCssValue(this, sessionIdFrom(params), elementIdFrom(params), nameFrom(params))),
                get("/session/{sessionId}/element/{elementId}/text")
                        .to(params -> new GetElementText(this, sessionIdFrom(params), elementIdFrom(params))),
                get("/session/{sessionId}/element/{elementId}/name")
                        .to(params -> new GetElementTagName(this, sessionIdFrom(params), elementIdFrom(params))),
                get("/session/{sessionId}/element/{elementId}/rect")
                        .to(params -> new GetElementRect(this, sessionIdFrom(params), elementIdFrom(params))),
                get("/session/{sessionId}/element/{elementId}/enabled")
                        .to(params -> new IsElementEnabled(this, sessionIdFrom(params), elementIdFrom(params))),
                post("/session/{sessionId}/element/{elementId}/click")
                        .to(params -> new ElementClick(this, sessionIdFrom(params), elementIdFrom(params))),
                post("/session/{sessionId}/element/{elementId}/clear")
                        .to(params -> new ElementClear(this, sessionIdFrom(params), elementIdFrom(params))),
                post("/session/{sessionId}/element/{elementId}/value")
                        .to(params -> new ElementSendKeys(this, sessionIdFrom(params), elementIdFrom(params))),
                get("/session/{sessionId}/source").to(params -> new GetPageSource(this, sessionIdFrom(params))),
                post("/session/{sessionId}/execute/sync").to(params -> new ExecuteScript(this, sessionIdFrom(params))),
                post("/session/{sessionId}/execute/async").to(params -> new ExecuteAsyncScript(this, sessionIdFrom(params))),
                get("/session/{sessionId}/cookie").to(params -> new GetAllCookies(this, sessionIdFrom(params))),
                get("/session/{sessionId}/cookie/{name}")
                        .to(params -> new GetNamedCookie(this, sessionIdFrom(params), nameFrom(params))),
                post("/session/{sessionId}/cookie").to(params -> new AddCookie(this, sessionIdFrom(params))),
                delete("/session/{sessionId}/cookie/{name}")
                        .to(params -> new DeleteNamedCookie(this, sessionIdFrom(params), nameFrom(params))),
                delete("/session/{sessionId)/cookie").to(params -> new DeleteAllCookies(this, sessionIdFrom(params))),
                post("/session/{sessionId}/actions").to(params -> new PerformActions(this, sessionIdFrom(params))),
                delete("/session/{sessionId}/actions").to(params -> new ReleaseActions(this, sessionIdFrom(params))),
                post("/session/{sessionId}/alert/dismiss").to(params -> new DismissAlert(this, sessionIdFrom(params))),
                post("/session/{sessionId}/alert/accept").to(params -> new AcceptAlert(this, sessionIdFrom(params))),
                get("/session/{sessionId}/alert/text").to(params -> new GetAlertText(this, sessionIdFrom(params))),
                post("/session/{sessionId}/alert/text").to(params -> new SendAlertText(this, sessionIdFrom(params))),
                get("/session/{sessionId}/screenshot")
                        .to(() -> req -> errorForException(new UnsupportedCommandException("Cannot take screenshot"))),
                get("/session/{sessionId}/element/{elementId}/screenshot")
                        .to(() -> req -> errorForException(new UnsupportedCommandException("Cannot take element screenshot"))),
                get("/status").to(() -> req -> successWithData(Map.of("ready", true, "message", "HtmlUnitDriverServer is ready."))),
                get("/readyz").to(() -> req -> new HttpResponse().setStatus(HTTP_NO_CONTENT))),
                null);
    }

    @Override
    protected String getSystemPropertiesConfigPrefix() {
        return "webdriver.htmlunit";
    }

    @Override
    protected Config getDefaultConfig() {
        return new MapConfig(Collections.emptyMap());
    }

    @Override
    protected void execute(final Config config) {
        Require.nonNull("Config", config);

        config.get("server", "max-threads")
                .ifPresent(value -> LOG.log(Level.WARNING,
                        () -> "Support for max-threads flag is deprecated. The intent of the flag is to"
                                + " set the thread pool size in the Distributor. Please use"
                                + " newsession-threadpool-size flag instead."));

        Server<?> server = asServer(config).start();

        BuildInfo info = new BuildInfo();
        LOG.info(String.format("Started Selenium Driver Binary %s (revision %s): %s", info.getReleaseLabel(),
                info.getBuildRevision(), server.getUrl()));
    }

    public HttpResponse newSession(final HttpRequest req) {
        List<Capabilities> capsList;
        Map<String, Object> content = fromJson(req, MAP_OF_OBJECTS);
        try(NewSessionPayload payload = NewSessionPayload.create(content)) {
            capsList = payload.stream().collect(toList());
        }
        
        HtmlUnitDriverOptions options = new HtmlUnitDriverOptions(capsList.get(0));
        
        String sessionId = UUID.randomUUID().toString();
        HtmlUnitDriver driver = new HtmlUnitDriver(options);
        
        driverMap.put(sessionId, driver);
        Map<String, Object> data = Map.of(
                    "sessionId", sessionId,
                    "capabilities", driver.getCapabilities());
        return successWithData(data);
    }

    public HttpResponse deleteSession(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.quit();
        driverMap.remove(sessionId);
        return successWithData(null);
    }
    
    public HttpResponse getTimeouts(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithTimeouts(driver);
    }

    public HttpResponse setTimeouts(final HttpRequest req, final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Timeouts timeouts = driver.manage().timeouts();
        
        Map<String, Long> content = fromJson(req, MAP_OF_LONGS);
        for (Entry<String, Long> entry : content.entrySet()) {
            try {
                switch(entry.getKey()) {
                case "script":
                    timeouts.scriptTimeout(Duration.ofMillis(entry.getValue()));
                    break;
                case "pageLoad":
                    timeouts.pageLoadTimeout(Duration.ofMillis(entry.getValue()));
                    break;
                case "implicit":
                    timeouts.implicitlyWait(Duration.ofMillis(entry.getValue()));
                    break;
                }
            } catch (InvalidArgumentException e) {
                return errorForException(e);
            }
        }
        
        return successWithTimeouts(driver);
    }
    
    public HttpResponse navigateTo(final HttpRequest req, final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Map<String, String> content = fromJson(req, MAP_OF_STRINGS);
        driver.get(content.get("url"));
        return successWithData(null);
    }

    public HttpResponse getCurrentUrl(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.getCurrentUrl());
    }

    public HttpResponse navigateBack(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.navigate().back();
        return successWithData(null);
    }

    public HttpResponse navigateForward(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.navigate().forward();
        return successWithData(null);
    }

    public HttpResponse refreshSession(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.navigate().refresh();;
        return successWithData(null);
    }

    public HttpResponse getTitle(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.getTitle());
    }

    public HttpResponse getWindowHandle(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.getWindowHandle());
    }

    public HttpResponse closeWindow(final String sessionId) {
        Set<String> data;
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.close();
        
        try {
            driver.getWebClient();
            data = driver.getWindowHandles();
        } catch (NoSuchSessionException eaten) {
            driverMap.remove(sessionId);
            data = Collections.emptySet();
        }
        
        return successWithData(data);
    }

    public HttpResponse switchToWindow(final HttpRequest req, final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Map<String, String> content = fromJson(req, MAP_OF_STRINGS);
        try {
            driver.switchTo().window(content.get("handle"));
        } catch (NoSuchWindowException e) {
            return errorForException(e);
        }
        
        return successWithData(null);
    }

    public HttpResponse switchToNewWindow(final HttpRequest req, final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Map<String, String> content = fromJson(req, MAP_OF_STRINGS);
        WindowType type = WindowType.fromString(content.get("type"));
        if (type == null) {
            return errorForException(new InvalidArgumentException(
                    "Invalid window type: " + String.valueOf(content.get("type"))));
        }
        driver.switchTo().newWindow(type);
        Map<String, Object> data = Map.of("handle", driver.getWindowHandle(), "type", type.toString());
        return successWithData(data);
    }

    public HttpResponse getWindowHandles(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.getWindowHandles());
    }

    public HttpResponse switchToFrame(final HttpRequest req, final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Map<String, Object> content = fromJson(req, MAP_OF_OBJECTS);
        Object frameId = new JsonToHtmlUnitWebElementConverter(driver).apply(content.get("id"));
        
        try {
            if (frameId == null) {
                driver.switchTo().defaultContent();
            } else if (frameId instanceof Long) {
                driver.switchTo().frame(((Long) frameId).intValue());
            } else if (frameId instanceof String) {
                driver.switchTo().frame((String) frameId);
            } else if (frameId instanceof WebElement) {
                driver.switchTo().frame((WebElement) frameId);
            } else {
                throw new NoSuchFrameException("Frame ID must be 'null', Integer, String, or WebElement; "
                                                + "was: " + frameId.getClass().getName());
            }
        } catch (NoSuchWindowException | NoSuchFrameException e) {
            return errorForException(e);
        }
        
        return successWithData(null);
    }

    public HttpResponse switchToParentFrame(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        
        try {
            driver.switchTo().parentFrame();
        } catch (NoSuchWindowException e) {
            return errorForException(e);
        }
        
        return successWithData(null);
    }

    public HttpResponse getWindowRect(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Dimension size = driver.manage().window().getSize();
        Point posn = driver.manage().window().getPosition();
        return successWithData(Map.of("width", size.width, "height", size.height, "x", posn.x, "y", posn.y));
    }

    public HttpResponse setWindowRect(final HttpRequest req, final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Map<String, Integer> content = fromJson(req, MAP_OF_INTEGERS);

        boolean didSet = false;
        Integer width = content.get("width");
        Integer height = content.get("height");
        Integer x = content.get("x");
        Integer y = content.get("y");

        if (width != null && height != null) {
            didSet = true;
            driver.manage().window().setSize(new Dimension(width, height));
        }

        if (x != null && y != null) {
            didSet = true;
            driver.manage().window().setPosition(new Point(x, y));
        }

        if (!didSet) {
            return errorForException(new InvalidArgumentException("SetWindowRect must specify size and/or position"));
        }
        
        return successWithWindowRect(driver);
    }

    public HttpResponse maximizeWindow(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.manage().window().maximize();
        return successWithWindowRect(driver);
    }

    public HttpResponse fullscreenWindow(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.manage().window().fullscreen();
        return successWithWindowRect(driver);
    }

    public HttpResponse getActiveElement(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        try {
            return successWithWebElement(driver.switchTo().activeElement());
        } catch (NoSuchElementException e) {
            return errorForException(e);
        }
    }

    public HttpResponse findElement(final HttpRequest req, final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return findElement(req, driver);
    }

    public HttpResponse findElements(final HttpRequest req, final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return findElements(req, driver);
    }

    public HttpResponse findElementFromElement(final HttpRequest req, final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return findElement(req, driver.toWebElement(elementId));
    }

    public HttpResponse findElementsFromElement(final HttpRequest req, final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return findElements(req, driver.toWebElement(elementId));
    }
    
    private HttpResponse findElement(final HttpRequest req, final SearchContext context) {
        By locator = locatorFrom(req);
        try {
            return successWithWebElement(context.findElement(locator));
        } catch (NoSuchElementException e) {
            return errorForException(e);
        }
    }
    
    private HttpResponse findElements(final HttpRequest req, final SearchContext context) {
        By locator = locatorFrom(req);
        return successWithWebElement(context.findElements(locator));
    }

    public HttpResponse isElementSelected(final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.toWebElement(elementId).isSelected());
    }

    public HttpResponse getElementDomAttribute(final String sessionId, final String elementId, final String attrName) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.toWebElement(elementId).getDomAttribute(attrName));
    }

    public HttpResponse getElementDomProperty(final String sessionId, final String elementId, final String propName) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.toWebElement(elementId).getDomProperty(propName));
    }

    public HttpResponse getElementCssValue(final String sessionId, final String elementId, final String propName) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.toWebElement(elementId).getCssValue(propName));
    }

    public HttpResponse getElementText(final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.toWebElement(elementId).getText());
    }

    public HttpResponse getElementTagName(final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.toWebElement(elementId).getTagName());
    }

    public HttpResponse getElementRect(final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Rectangle rect = driver.toWebElement(elementId).getRect();
        return successWithData(Map.of("width", rect.width, "height", rect.height, "x", rect.x, "y", rect.y));
    }

    public HttpResponse isElementEnabled(final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.toWebElement(elementId).isEnabled());
    }

    public HttpResponse elementClick(final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.toWebElement(elementId).click();
        return successWithData(null);
    }

    public HttpResponse elementClear(final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.toWebElement(elementId).clear();
        return successWithData(null);
    }

    public HttpResponse elementSendKeys(final HttpRequest req, final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Map<String, Object> content = fromJson(req, MAP_OF_OBJECTS);
        String keysToSend = content.get("text").toString();
        driver.toWebElement(elementId).sendKeys(keysToSend);
        return successWithData(null);
    }

    public HttpResponse getPageSource(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.getPageSource());
    }

    public HttpResponse executeScript(final HttpRequest req, final String sessionId, final boolean async) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Map<String, Object> content = fromJson(req, MAP_OF_OBJECTS);
        Object script = content.get("script");
        Object args = new JsonToHtmlUnitWebElementConverter(driver).apply(content.get("args"));
        
        String errorMessage;
        if (script instanceof String) {
            if (args instanceof List) {
                if (async) {
                    return successWithWebElement(driver.executeAsyncScript((String) script, ((List<?>) args).toArray()));
                } else {
                    return successWithWebElement(driver.executeScript((String) script, ((List<?>) args).toArray()));
                }
            } else {
                errorMessage = "Property 'args' is not a List";
            }
        } else {
            errorMessage = "Property 'script' is not a String";
        }
        return errorForException(new InvalidArgumentException(errorMessage));
    }

    public HttpResponse getAllCookies(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.manage().getCookies());
    }

    public HttpResponse getNamedCookie(final String sessionId, final String cookieName) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.manage().getCookieNamed(cookieName));
    }

    public HttpResponse addCookie(final HttpRequest req, final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Map<String, Cookie> content = fromJson(req, MAP_OF_COOKIES);
        driver.manage().addCookie(content.get("cookie"));
        return successWithData(null);
    }

    public HttpResponse deleteNamedCookie(final String sessionId, final String cookieName) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.manage().deleteCookieNamed(cookieName);
        return successWithData(null);
    }

    public HttpResponse deleteAllCookies(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.manage().deleteAllCookies();
        return successWithData(null);
    }

    public HttpResponse performActions(final HttpRequest req, final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Map<String, ActionsWrapper> content = fromJson(req, MAP_OF_ACTIONS);
        content.get("actions").resolveOrigins(driver).build().perform();
        return successWithData(null);
    }

    public HttpResponse releaseActions(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.resetInputState();
        return successWithData(null);
    }

    public HttpResponse dismissAlert(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.getAlert().dismiss();
        return successWithData(null);
    }

    public HttpResponse acceptAlert(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.getAlert().accept();
        return successWithData(null);
    }

    public HttpResponse getAlertText(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.getAlert().getText());
    }

    public HttpResponse sendAlertText(final HttpRequest req, final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Map<String, String> content = fromJson(req, MAP_OF_OBJECTS);
        driver.getAlert().sendKeys(content.get("text"));
        return successWithData(null);
    }

    private static String sessionIdFrom(final Map<String, String> params) {
        return params.get("sessionId");
    }

    private static String elementIdFrom(final Map<String, String> params) {
        return params.get("elementId");
    }
    
    private static String nameFrom(final Map<String, String> params) {
        return params.get("name");
    }
    
    private static By locatorFrom(final HttpRequest req) {
        Map<String, String> content = fromJson(req, MAP_OF_STRINGS);
        String value = content.get("value");
        switch (content.get("using")) {
        case "css selector":
            return By.cssSelector(value);
        case "link text":
            return By.linkText(value);
        case "partial link text":
            return By.partialLinkText(value);
        case "tag name":
            return By.tagName(value);
        case "xpath":
            return By.xpath(value);
        }
        return null;
    }

    HtmlUnitDriver getDriverSession(final String sessionId) {
        HtmlUnitDriver driver = driverMap.get(sessionId);
        if (driver == null) {
            throw new NoSuchSessionException("Failed finding session with identifier: " + sessionId);
        }
        return driver;
    }
    
    private static HttpResponse successWithData(final Object data) {
        Map<String, Object> content = new HashMap<>();
        content.put("value", data);
        return new HttpResponse().setContent(asJson(content));
    }
    
    private static HttpResponse successWithWebElement(final Object data) {
        return successWithData(new HtmlUnitWebElementToJsonConverter().apply(data));
    }
    
    private static HttpResponse successWithWindowRect(final WebDriver driver) {
        Dimension newSize = driver.manage().window().getSize();
        Point newPosition = driver.manage().window().getPosition();
        Map<String, Integer> data = Map.of(
                "width", newSize.getWidth(),
                "height", newSize.getHeight(),
                "x", newPosition.getX(),
                "y", newPosition.getY());
        return successWithData(data);
    }
    
    private HttpResponse successWithTimeouts(final WebDriver driver) {
        Timeouts timeouts = driver.manage().timeouts();
        Map<String, Object> data = Map.of(
                "script", timeouts.getScriptTimeout().toMillis(),
                "pageLoad", timeouts.getPageLoadTimeout().toMillis(),
                "implicit", timeouts.getImplicitWaitTimeout().toMillis());
        return successWithData(data);
    }
    
    private static HttpResponse errorForException(final Throwable e) {
        ErrorCodec codec = ErrorCodec.createDefault();
        return new HttpResponse()
                .setStatus(codec.getHttpStatusCode(e))
                .setContent(asJson(codec.encode(e)));
    }

}
