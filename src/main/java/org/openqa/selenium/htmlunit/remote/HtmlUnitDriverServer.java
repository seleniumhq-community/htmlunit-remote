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

import java.io.UncheckedIOException;
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
import java.util.logging.Logger;

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
import org.openqa.selenium.grid.TemplateGridServerCommand.Handlers;
import org.openqa.selenium.grid.server.BaseServerOptions;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.htmlunit.options.HtmlUnitDriverOptions;
import org.openqa.selenium.htmlunit.remote.ActionsCoercer.ActionsWrapper;
import org.openqa.selenium.json.TypeToken;
import org.openqa.selenium.netty.server.NettyServer;
import org.openqa.selenium.remote.ErrorCodec;
import org.openqa.selenium.remote.NewSessionPayload;
import org.openqa.selenium.remote.http.HttpHandler;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;
import org.openqa.selenium.remote.http.Route;

/**
 * This class implements a server that supports remote execution of {@link HtmlUnitDriver}.
 */
public class HtmlUnitDriverServer extends NettyServer {
    
    public HtmlUnitDriverServer(BaseServerOptions options) {
        super(options, createHandlers().httpHandler);
    }

    private static final Type MAP_OF_LONGS = new TypeToken<Map<String, Long>>() {}.getType();
    private static final Type MAP_OF_OBJECTS = new TypeToken<Map<String, Object>>() {}.getType();
    private static final Type MAP_OF_STRINGS = new TypeToken<Map<String, String>>() {}.getType();
    private static final Type MAP_OF_INTEGERS = new TypeToken<Map<String, Integer>>() {}.getType();
    private static final Type MAP_OF_ACTIONS = new TypeToken<Map<String, ActionsCoercer>>() {}.getType();
    private static final Type MAP_OF_COOKIES = new TypeToken<Map<String, CookieCoercer>>() {}.getType();
    
    private static final Logger LOG = Logger.getLogger(HtmlUnitDriverServer.class.getName());

    private static Map<String, HtmlUnitDriver> driverMap = new ConcurrentHashMap<>();
    
    /**
     * Define the handlers for the routes supported by this server.
     * 
     * @return {@link Handlers} object
     */
    protected static Handlers createHandlers() {
        return new Handlers(Route.combine(
            post("/session").to(() -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return newSession(req);
                }
            }),
            delete("/session/{sessionId}").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return deleteSession(sessionIdFrom(params));
                }
            }),
            get("/session/{sessionId}/timeouts").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return getTimeouts(sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/timeouts").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return setTimeouts(req, sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/url").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return navigateTo(req, sessionIdFrom(params));
                }
            }),
            get("/session/{sessionId}/url").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return getCurrentUrl(sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/back").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return navigateBack(sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/forward").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return navigateForward(sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/refresh").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return refreshSession(sessionIdFrom(params));
                }
            }),
            get("/session/{sessionId}/title").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return getTitle(sessionIdFrom(params));
                }
            }),
            get("/session/{sessionId}/window").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return getWindowHandle(sessionIdFrom(params));
                }
            }),
            delete("/session/{sessionId}/window").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return closeWindow(sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/window").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return switchToWindow(req, sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/window/new").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return switchToNewWindow(req, sessionIdFrom(params));
                }
            }),
            get("/session/{sessionId}/window/handles").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return getWindowHandles(sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/frame").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return switchToFrame(req, sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/frame/parent").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return switchToParentFrame(sessionIdFrom(params));
                }
            }),
            get("/session/{sessionId}/window/rect").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return getWindowRect(sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/window/rect").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return setWindowRect(req, sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/window/maximize").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return maximizeWindow(sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/window/minimize")
                    .to(() -> req -> errorForException(new UnsupportedCommandException("Cannot minimize window"))),
            post("/session/{sessionId}/window/fullscreen").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return fullscreenWindow(sessionIdFrom(params));
                }
            }),
            get("/session/{sessionId}/element/active").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return getActiveElement(sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/element").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return findElement(req, sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/elements").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return findElements(req, sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/element/{elementId}/element").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return findElementFromElement(req, sessionIdFrom(params), elementIdFrom(params));
                }
            }),
            post("/session/{sessionId}/element/{elementId}/elements").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return findElementsFromElement(req, sessionIdFrom(params), elementIdFrom(params));
                }
            }),
            get("/session/{sessionId}/element/{elementId}/selected").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return isElementSelected(sessionIdFrom(params), elementIdFrom(params));
                }
            }),
            get("/session/{sessionId}/element/{elementId}/attribute/{name}").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return getElementDomAttribute(sessionIdFrom(params), elementIdFrom(params), nameFrom(params));
                }
            }),
            get("/session/{sessionId}/element/{elementId}/property/{name}").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return getElementDomProperty(sessionIdFrom(params), elementIdFrom(params), nameFrom(params));
                }
            }),
            get("/session/{sessionId}/element/{elementId}/css/{name}").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return getElementCssValue(sessionIdFrom(params), elementIdFrom(params), nameFrom(params));
                }
            }),
            get("/session/{sessionId}/element/{elementId}/text").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return getElementText(sessionIdFrom(params), elementIdFrom(params));
                }
            }),
            get("/session/{sessionId}/element/{elementId}/name").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return getElementTagName(sessionIdFrom(params), elementIdFrom(params));
                }
            }),
            get("/session/{sessionId}/element/{elementId}/rect").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return getElementRect(sessionIdFrom(params), elementIdFrom(params));
                }
            }),
            get("/session/{sessionId}/element/{elementId}/enabled").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return isElementEnabled(sessionIdFrom(params), elementIdFrom(params));
                }
            }),
            post("/session/{sessionId}/element/{elementId}/click").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return elementClick(sessionIdFrom(params), elementIdFrom(params));
                }
            }),
            post("/session/{sessionId}/element/{elementId}/clear").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return elementClear(sessionIdFrom(params), elementIdFrom(params));
                }
            }),
            post("/session/{sessionId}/element/{elementId}/value").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return elementSendKeys(req, sessionIdFrom(params), elementIdFrom(params));
                }
            }),
            get("/session/{sessionId}/source").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return getPageSource(sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/execute/sync").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return executeScript(req, sessionIdFrom(params), false);
                }
            }),
            post("/session/{sessionId}/execute/async").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return executeScript(req, sessionIdFrom(params), true);
                }
            }),
            get("/session/{sessionId}/cookie").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return getAllCookies(sessionIdFrom(params));
                }
            }),
            get("/session/{sessionId}/cookie/{name}").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return getNamedCookie(sessionIdFrom(params), nameFrom(params));
                }
            }),
            post("/session/{sessionId}/cookie").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return addCookie(req, sessionIdFrom(params));
                }
            }),
            delete("/session/{sessionId}/cookie/{name}").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return deleteNamedCookie(sessionIdFrom(params), nameFrom(params));
                }
            }),
            delete("/session/{sessionId)/cookie").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return deleteAllCookies(sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/actions").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return performActions(req, sessionIdFrom(params));
                }
            }),
            delete("/session/{sessionId}/actions").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return releaseActions(sessionIdFrom(params));
                }
                
            }),
            post("/session/{sessionId}/alert/dismiss").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return dismissAlert(sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/alert/accept").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return acceptAlert(sessionIdFrom(params));
                }
            }),
            get("/session/{sessionId}/alert/text").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return getAlertText(sessionIdFrom(params));
                }
            }),
            post("/session/{sessionId}/alert/text").to(params -> new HttpHandler() {
                @Override
                public HttpResponse execute(final HttpRequest req) throws UncheckedIOException {
                    return sendAlertText(req, sessionIdFrom(params));
                }
            }),
            get("/session/{sessionId}/screenshot")
                    .to(() -> req -> errorForException(new UnsupportedCommandException("Cannot take screenshot"))),
            get("/session/{sessionId}/element/{elementId}/screenshot")
                    .to(() -> req -> errorForException(new UnsupportedCommandException("Cannot take element screenshot"))),
            get("/status").to(() -> req -> successWithData(Map.of("ready", true, "message", "HtmlUnitDriverServer is ready."))),
            get("/readyz").to(() -> req -> new HttpResponse().setStatus(HTTP_NO_CONTENT))),
            null
        );
    }
    
    /**
     * Handle remote 'New Session' request :: POST '/session'
     * 
     * @param req request with {@link NewSessionPayload}
     * @return response:<ul>
     *     <li><b>200</b> - success with data:<ul>
     *         <li><b>sessionId</b> : session ID</li>
     *         <li><b>capabilities</b> : session capabilities</li></ul></li>
     *     <li><b>404</b> - session not created</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#new-session">New Session</a>
     */
    public static HttpResponse newSession(final HttpRequest req) {
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

    /**
     * Handle remote 'Delete Session' request :: DELETE '/session/{sessionId}'
     * 
     * @param sessionId target session ID
     * @return response:<ul>
     *     <li><b>200</b> - success</li>
     *     <li><b>404</b> - invalid session ID</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#delete-session">Delete Session</a>
     */
    public static HttpResponse deleteSession(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.quit();
        driverMap.remove(sessionId);
        return successWithData(null);
    }
    
    /**
     * Handle remote 'Get Timeouts' request :: GET '/session/{sessionId}/timeouts'
     * 
     * @param sessionId target session ID
     * @return response:<ul>
     *     <li><b>200</b> - success with data:<ul>
     *         <li><b>script</b> : script timeout (mS)</li>
     *         <li><b>pageLoad</b> : page load timeout (mS)</li>
     *         <li><b>implicit</b> : implicit timeout (mS)</li></ul></li>
     *     <li><b>404</b> - invalid session ID</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#get-timeouts">Get Timeouts</a>
     */
    public static HttpResponse getTimeouts(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithTimeouts(driver);
    }

    /**
     * Handle remote 'Set Timeouts' request :: POST '/session/{sessionId}/timeouts'
     * 
     * @param req request with payload:<ul>
     *     <li><b>script</b> : script timeout (mS)</li>
     *     <li><b>pageLoad</b> : page load timeout (mS)</li>
     *     <li><b>implicit</b> : implicit timeout (mS)</li></ul>
     * @param sessionId target session ID
     * @return response:<ul>
     *     <li><b>200</b> - success with data:<ul>
     *         <li><b>script</b> : script timeout (mS)</li>
     *         <li><b>pageLoad</b> : page load timeout (mS)</li>
     *         <li><b>implicit</b> : implicit timeout (mS)</li></ul></li>
     *     <li><b>404</b> - invalid session ID</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#set-timeouts">Set Timeouts</a>
     */
    public static HttpResponse setTimeouts(final HttpRequest req, final String sessionId) {
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
    
    /**
     * Handle remote 'Navigate To' request :: POST '/session/{session id}/url'
     * 
     * @param req request with payload:<ul>
     *     <li><b>url</b> : target URL</li></ul>
     * @param sessionId target session ID
     * @return response:<ul>
     *     <li><b>200</b> - success</li>
     *     <li><b>404</b> - invalid session ID</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#navigate-to">Navigate To</a>
     */
    public static HttpResponse navigateTo(final HttpRequest req, final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Map<String, String> content = fromJson(req, MAP_OF_STRINGS);
        driver.get(content.get("url"));
        return successWithData(null);
    }

    /**
     * Handle remote 'Get Current URL' request :: GET '/session/{session id}/url'
     * 
     * @param sessionId target session ID
     * @return response:<ul>
     *     <li><b>200</b> - success with data:<ul>
     *         <li><b>value</b> : current URL</li></ul></li>
     *     <li><b>404</b> - invalid session ID</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#get-current-url">Get Current URL</a>
     */
    public static HttpResponse getCurrentUrl(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.getCurrentUrl());
    }

    /**
     * Handle remote 'Back' request :: POST '/session/{session id}/back'
     * 
     * @param sessionId target session ID
     * @return response:<ul>
     *     <li><b>200</b> - success</li>
     *     <li><b>404</b> - invalid session ID</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#back">Back</a>
     */
    public static HttpResponse navigateBack(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.navigate().back();
        return successWithData(null);
    }

    /**
     * Handle remote 'Forward' command :: POST '/session/{session id}/forward'
     * 
     * @param sessionId target session ID
     * @return response:<ul>
     *     <li><b>200</b> - success</li>
     *     <li><b>404</b> - invalid session ID</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#forward">Forward</a>
     */
    public static HttpResponse navigateForward(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.navigate().forward();
        return successWithData(null);
    }

    /**
     * Handle remote 'Refresh' request :: POST '/session/{session id}/refresh'
     * 
     * @param sessionId target session ID
     * @return response:<ul>
     *     <li><b>200</b> - success</li>
     *     <li><b>404</b> - invalid session ID</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#refresh">Refresh</a>
     */
    public static HttpResponse refreshSession(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.navigate().refresh();;
        return successWithData(null);
    }

    /**
     * Handle remote 'Get Title' request :: GET '/session/{session id}/title'
     * 
     * @param sessionId target session ID
     * @return response:<ul>
     *     <li><b>200</b> - success with data:<ul>
     *         <li><b>value</b> : current page title</li></ul></li>
     *     <li><b>404</b> - invalid session ID</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#refresh">Refresh</a>
     */
    public static HttpResponse getTitle(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.getTitle());
    }

    /**
     * Handle remote 'Get Window Handle' request :: GET '/session/{session id}/window'
     * 
     * @param sessionId target session ID
     * @return response:<ul>
     *     <li><b>200</b> - success with data:<ul>
     *         <li><b>value</b> : current window handle</li></ul></li>
     *     <li><b>404</b> - invalid session ID</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#get-window-handle">Get Window Handle</a>
     */
    public static HttpResponse getWindowHandle(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.getWindowHandle());
    }

    /**
     * Handle remote 'Close Window' request :: DELETE '/session/{session id}/window'
     * 
     * @param sessionId target session ID
     * @return response:<ul>
     *     <li><b>200</b> - success with data:<ul>
     *         <li><b>value</b> : list of window handles</li></ul></li>
     *     <li><b>404</b> - invalid session ID</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#close-window">Close Window</a>
     */
    public static HttpResponse closeWindow(final String sessionId) {
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

    /**
     * Handle remote 'Switch To Window' request :: POST '/session/{session id}/window'
     * 
     * @param req request with payload:<ul>
     *     <li><b>handle</b> : target window handle</li></ul>
     * @param sessionId target session ID
     * @return response:<ul>
     *     <li><b>200</b> - success</li>
     *     <li><b>404</b> - invalid session ID</li>
     *     <li><b>404</b> - no such window</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#switch-to-window">Switch To Window</a>
     */
    public static HttpResponse switchToWindow(final HttpRequest req, final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Map<String, String> content = fromJson(req, MAP_OF_STRINGS);
        try {
            driver.switchTo().window(content.get("handle"));
        } catch (NoSuchWindowException e) {
            return errorForException(e);
        }
        
        return successWithData(null);
    }

    /**
     * Handle remote 'New Window' request :: POST '/session/{sessionId}/window/new'
     * 
     * @param req request with payload:<ul>
     *     <li><b>type</b> : new window type [window, tab]</li></ul>
     * @param sessionId target session ID
     * @return response:<ul>
     *     <li><b>200</b> - success with data:<ul>
     *         <li><b>handle</b> : new window handle</li>
     *         <li><b>type</b> : new window type [window, tab]</li></ul></li>
     *     <li><b>400</b> - invalid argument</li>
     *     <li><b>404</b> - invalid session ID</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#new-window">New Window</a>
     */
    public static HttpResponse switchToNewWindow(final HttpRequest req, final String sessionId) {
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

    /**
     * Handle remote 'Get Window Handles' request :: GET '/session/{session id}/window/handles'
     * 
     * @param sessionId target session ID
     * @return response:<ul>
     *     <li><b>200</b> - success with data:<ul>
     *         <li><b>value</b> : list of window handles</li></ul></li>
     *     <li><b>404</b> - invalid session ID</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#get-window-handles">Get Window Handles</a>
     */
    public static HttpResponse getWindowHandles(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.getWindowHandles());
    }

    /**
     * Handle remote 'Switch To Frame' request :: POST '/session/{session id}/frame'
     * 
     * @param req request with payload:<ul>
     *     <li><b>id</b> : {@code null}, index, or element reference</li></ul>
     * @param sessionId target session ID
     * @return response:<ul>
     *     <li><b>200</b> - success</li>
     *     <li><b>404</b> - invalid session ID</li>
     *     <li><b>404</b> - no such window</li>
     *     <li><b>404</b> - no such frame</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#switch-to-frame">Switch To Frame</a>
     */
    public static HttpResponse switchToFrame(final HttpRequest req, final String sessionId) {
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

    /**
     * Handle remote 'Switch To Parent Frame' command :: POST '/session/{session id}/frame/parent'
     * 
     * @param sessionId target session ID
     * @return response:<ul>
     *     <li><b>200</b> - success</li>
     *     <li><b>404</b> - invalid session ID</li>
     *     <li><b>404</b> - no such window</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#switch-to-parent-frame">Switch To Parent Frame</a>
     */
    public static HttpResponse switchToParentFrame(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        
        try {
            driver.switchTo().parentFrame();
        } catch (NoSuchWindowException e) {
            return errorForException(e);
        }
        
        return successWithData(null);
    }

    /**
     * Handle remote 'Get Window Rect' command :: GET '/session/{session id}/window/rect'
     * 
     * @param sessionId target session ID
     * @return response:<ul>
     *     <li><b>200</b> - success with data:<ul>
     *         <li><b>width</b> : width of current window</li>
     *         <li><b>height</b> : height of current window</li>
     *         <li><b>x</b> : current window 'X' coordinate</li>
     *         <li><b>y</b> : current window 'Y' coordinate</li></ul></li>
     *     <li><b>404</b> - invalid session ID</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#get-window-rect">Get Window Rect</a>
     */
    public static HttpResponse getWindowRect(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Dimension size = driver.manage().window().getSize();
        Point posn = driver.manage().window().getPosition();
        return successWithData(Map.of("width", size.width, "height", size.height, "x", posn.x, "y", posn.y));
    }

    /**
     * Handle remote 'Set Window Rect' command :: POST '/session/{session id}/window/rect'
     * 
     * @param req request with payload:<ul>
     *     <li><b>width</b> : target width for current window</li>
     *     <li><b>height</b> : target height for current window</li>
     *     <li><b>x</b> : target 'X' coordinate for current window</li>
     *     <li><b>y</b> : target 'Y' coordinate for current window</li></ul>
     * @param sessionId target session ID
     * @return response:<ul>
     *     <li><b>200</b> - success with data:<ul>
     *         <li><b>width</b> : width of current window</li>
     *         <li><b>height</b> : height of current window</li>
     *         <li><b>x</b> : current window 'X' coordinate</li>
     *         <li><b>y</b> : current window 'Y' coordinate</li></ul></li>
     *     <li><b>400</b> - invalid argument</li>
     *     <li><b>404</b> - invalid session ID</li></ul>
     * @see <a href="https://www.w3.org/TR/webdriver/#set-window-rect">Set Window Rect</a>
     */
    public static HttpResponse setWindowRect(final HttpRequest req, final String sessionId) {
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

    /**
     * 
     * @param sessionId
     * @return
     */
    public static HttpResponse maximizeWindow(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.manage().window().maximize();
        return successWithWindowRect(driver);
    }

    /**
     * 
     * @param sessionId
     * @return
     */
    public static HttpResponse fullscreenWindow(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.manage().window().fullscreen();
        return successWithWindowRect(driver);
    }

    /**
     * 
     * @param sessionId
     * @return
     */
    public static HttpResponse getActiveElement(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        try {
            return successWithWebElement(driver.switchTo().activeElement());
        } catch (NoSuchElementException e) {
            return errorForException(e);
        }
    }

    /**
     * 
     * @param req
     * @param sessionId
     * @return
     */
    public static HttpResponse findElement(final HttpRequest req, final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return findElement(req, driver);
    }

    /**
     * 
     * @param req
     * @param sessionId
     * @return
     */
    public static HttpResponse findElements(final HttpRequest req, final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return findElements(req, driver);
    }

    /**
     * 
     * @param req
     * @param sessionId
     * @param elementId
     * @return
     */
    public static HttpResponse findElementFromElement(final HttpRequest req, final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return findElement(req, driver.toWebElement(elementId));
    }

    /**
     * 
     * @param req
     * @param sessionId
     * @param elementId
     * @return
     */
    public static HttpResponse findElementsFromElement(final HttpRequest req, final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return findElements(req, driver.toWebElement(elementId));
    }
    
    /**
     * 
     * @param req
     * @param context
     * @return
     */
    private static HttpResponse findElement(final HttpRequest req, final SearchContext context) {
        By locator = locatorFrom(req);
        try {
            return successWithWebElement(context.findElement(locator));
        } catch (NoSuchElementException e) {
            return errorForException(e);
        }
    }
    
    /**
     * 
     * @param req
     * @param context
     * @return
     */
    private static HttpResponse findElements(final HttpRequest req, final SearchContext context) {
        By locator = locatorFrom(req);
        return successWithWebElement(context.findElements(locator));
    }

    /**
     * 
     * @param sessionId
     * @param elementId
     * @return
     */
    public static HttpResponse isElementSelected(final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.toWebElement(elementId).isSelected());
    }

    /**
     * 
     * @param sessionId
     * @param elementId
     * @param attrName
     * @return
     */
    public static HttpResponse getElementDomAttribute(final String sessionId, final String elementId, final String attrName) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.toWebElement(elementId).getDomAttribute(attrName));
    }

    /**
     * 
     * @param sessionId
     * @param elementId
     * @param propName
     * @return
     */
    public static HttpResponse getElementDomProperty(final String sessionId, final String elementId, final String propName) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.toWebElement(elementId).getDomProperty(propName));
    }

    /**
     * 
     * @param sessionId
     * @param elementId
     * @param propName
     * @return
     */
    public static HttpResponse getElementCssValue(final String sessionId, final String elementId, final String propName) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.toWebElement(elementId).getCssValue(propName));
    }

    /**
     * 
     * @param sessionId
     * @param elementId
     * @return
     */
    public static HttpResponse getElementText(final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.toWebElement(elementId).getText());
    }

    /**
     * 
     * @param sessionId
     * @param elementId
     * @return
     */
    public static HttpResponse getElementTagName(final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.toWebElement(elementId).getTagName());
    }

    /**
     * 
     * @param sessionId
     * @param elementId
     * @return
     */
    public static HttpResponse getElementRect(final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Rectangle rect = driver.toWebElement(elementId).getRect();
        return successWithData(Map.of("width", rect.width, "height", rect.height, "x", rect.x, "y", rect.y));
    }

    /**
     * 
     * @param sessionId
     * @param elementId
     * @return
     */
    public static HttpResponse isElementEnabled(final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.toWebElement(elementId).isEnabled());
    }

    /**
     * 
     * @param sessionId
     * @param elementId
     * @return
     */
    public static HttpResponse elementClick(final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.toWebElement(elementId).click();
        return successWithData(null);
    }

    /**
     * 
     * @param sessionId
     * @param elementId
     * @return
     */
    public static HttpResponse elementClear(final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.toWebElement(elementId).clear();
        return successWithData(null);
    }

    /**
     * 
     * @param req
     * @param sessionId
     * @param elementId
     * @return
     */
    public static HttpResponse elementSendKeys(final HttpRequest req, final String sessionId, final String elementId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Map<String, Object> content = fromJson(req, MAP_OF_OBJECTS);
        String keysToSend = content.get("text").toString();
        driver.toWebElement(elementId).sendKeys(keysToSend);
        return successWithData(null);
    }

    /**
     * 
     * @param sessionId
     * @return
     */
    public static HttpResponse getPageSource(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.getPageSource());
    }

    /**
     * 
     * @param req
     * @param sessionId
     * @param async
     * @return
     */
    public static HttpResponse executeScript(final HttpRequest req, final String sessionId, final boolean async) {
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

    /**
     * 
     * @param sessionId
     * @return
     */
    public static HttpResponse getAllCookies(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.manage().getCookies());
    }

    /**
     * 
     * @param sessionId
     * @param cookieName
     * @return
     */
    public static HttpResponse getNamedCookie(final String sessionId, final String cookieName) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.manage().getCookieNamed(cookieName));
    }

    /**
     * 
     * @param req
     * @param sessionId
     * @return
     */
    public static HttpResponse addCookie(final HttpRequest req, final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Map<String, Cookie> content = fromJson(req, MAP_OF_COOKIES);
        driver.manage().addCookie(content.get("cookie"));
        return successWithData(null);
    }

    /**
     * 
     * @param sessionId
     * @param cookieName
     * @return
     */
    public static HttpResponse deleteNamedCookie(final String sessionId, final String cookieName) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.manage().deleteCookieNamed(cookieName);
        return successWithData(null);
    }

    /**
     * 
     * @param sessionId
     * @return
     */
    public static HttpResponse deleteAllCookies(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.manage().deleteAllCookies();
        return successWithData(null);
    }

    /**
     * 
     * @param req
     * @param sessionId
     * @return
     */
    public static HttpResponse performActions(final HttpRequest req, final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Map<String, ActionsWrapper> content = fromJson(req, MAP_OF_ACTIONS);
        content.get("actions").resolveOrigins(driver).build().perform();
        return successWithData(null);
    }

    /**
     * 
     * @param sessionId
     * @return
     */
    public static HttpResponse releaseActions(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.resetInputState();
        return successWithData(null);
    }

    /**
     * 
     * @param sessionId
     * @return
     */
    public static HttpResponse dismissAlert(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.getAlert().dismiss();
        return successWithData(null);
    }

    /**
     * 
     * @param sessionId
     * @return
     */
    public static HttpResponse acceptAlert(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        driver.getAlert().accept();
        return successWithData(null);
    }

    /**
     * 
     * @param sessionId
     * @return
     */
    public static HttpResponse getAlertText(final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        return successWithData(driver.getAlert().getText());
    }

    /**
     * 
     * @param req
     * @param sessionId
     * @return
     */
    public static HttpResponse sendAlertText(final HttpRequest req, final String sessionId) {
        HtmlUnitDriver driver = getDriverSession(sessionId);
        Map<String, String> content = fromJson(req, MAP_OF_OBJECTS);
        driver.getAlert().sendKeys(content.get("text"));
        return successWithData(null);
    }

    /**
     * 
     * @param params
     * @return
     */
    private static String sessionIdFrom(final Map<String, String> params) {
        return params.get("sessionId");
    }

    /**
     * 
     * @param params
     * @return
     */
    private static String elementIdFrom(final Map<String, String> params) {
        return params.get("elementId");
    }
    
    /**
     * 
     * @param params
     * @return
     */
    private static String nameFrom(final Map<String, String> params) {
        return params.get("name");
    }
    
    /**
     * 
     * @param req
     * @return
     */
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
    
    /**
     * 
     * @return
     */
    static int getDriverCount() {
        return driverMap.size();
    }

    /**
     * 
     * @param sessionId
     * @return
     */
    static HtmlUnitDriver getDriverSession(final String sessionId) {
        HtmlUnitDriver driver = driverMap.get(sessionId);
        if (driver == null) {
            throw new NoSuchSessionException("Failed finding session with identifier: " + sessionId);
        }
        return driver;
    }
    
    /**
     * 
     * @param data
     * @return
     */
    private static HttpResponse successWithData(final Object data) {
        Map<String, Object> content = new HashMap<>();
        content.put("value", data);
        return new HttpResponse().setContent(asJson(content));
    }
    
    /**
     * 
     * @param data
     * @return
     */
    private static HttpResponse successWithWebElement(final Object data) {
        return successWithData(new HtmlUnitWebElementToJsonConverter().apply(data));
    }
    
    /**
     * 
     * @param driver
     * @return
     */
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
    
    /**
     * 
     * @param driver
     * @return
     */
    private static HttpResponse successWithTimeouts(final WebDriver driver) {
        Timeouts timeouts = driver.manage().timeouts();
        Map<String, Object> data = Map.of(
                "script", timeouts.getScriptTimeout().toMillis(),
                "pageLoad", timeouts.getPageLoadTimeout().toMillis(),
                "implicit", timeouts.getImplicitWaitTimeout().toMillis());
        return successWithData(data);
    }
    
    /**
     * 
     * @param e
     * @return
     */
    private static HttpResponse errorForException(final Throwable e) {
        ErrorCodec codec = ErrorCodec.createDefault();
        return new HttpResponse()
                .setStatus(codec.getHttpStatusCode(e))
                .setContent(asJson(codec.encode(e)));
    }
}
