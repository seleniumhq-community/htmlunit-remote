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

import static java.net.HttpURLConnection.HTTP_OK;
import static org.openqa.selenium.json.Json.JSON_UTF_8;
import static org.openqa.selenium.remote.http.Contents.fromJson;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.htmlunit.WebDriverTestCase;
import org.openqa.selenium.htmlunit.options.HtmlUnitDriverOptions;
import org.openqa.selenium.htmlunit.options.HtmlUnitOptionNames;
import org.openqa.selenium.json.TypeToken;
import org.openqa.selenium.remote.CommandCodec;
import org.openqa.selenium.remote.Dialect;
import org.openqa.selenium.remote.NewSessionPayload;
import org.openqa.selenium.remote.SessionId;
import org.openqa.selenium.remote.http.HttpHeader;
import org.openqa.selenium.remote.http.HttpMethod;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;

public class RemoteWebDriverTestCase extends WebDriverTestCase {
    protected static final CommandCodec<HttpRequest> commandCodec;
    protected static final String HTMLUNIT_HOME = "https://www.htmlunit.org/";
    protected static final String HTMLUNIT_HISTORY = "https://www.htmlunit.org/history.html";
    
    protected static final Type MAP_OF_OBJECTS = new TypeToken<Map<String, Object>>() {}.getType();
    
    protected String sessionId;
    
    static {
        commandCodec = Dialect.W3C.getCommandCodec();
    }

    @Before
    public void setUp() {
        sessionId = createDriverSession();
    }
    
    @After
    public void cleanUp() {
        if (sessionId != null) {
            try {
                closeDriverSession();
            } catch (NoSuchSessionException e) {
                // nothing to do here
            }
            sessionId = null;
        }
    }
    
    @Override
    protected WebDriver getWebDriver() {
        return HtmlUnitDriverServer.getDriverSession(sessionId);
    }
    
    protected SessionId sessionId() {
        return new SessionId(sessionId);
    }
    
    private String createDriverSession() {
        // permit JavaScript errors to accommodate 'selenium.dev' site
        HtmlUnitDriverOptions capabilities = new HtmlUnitDriverOptions();
        capabilities.setCapability(HtmlUnitOptionNames.optThrowExceptionOnScriptError, false);
        HttpResponse response = HtmlUnitDriverServer.newSession(newSessionRequest(capabilities));
        assertEquals("Failed creating new session", HTTP_OK, response.getStatus());
        Map<String, Object> value = extractMap(response);
        Object sessionId = value.get("sessionId");
        if (sessionId instanceof String) {
            return (String) sessionId;
        }
        throw new AssertionError("Failed creating driver session");   
    }
    
    private static HttpRequest newSessionRequest(final Capabilities capabilities) {
        Capabilities caps = capabilities != null ? capabilities : new HtmlUnitDriverOptions();
        NewSessionPayload payload = NewSessionPayload.create(caps);
        HttpRequest request = new HttpRequest(HttpMethod.POST, "/session");
        request.setHeader(HttpHeader.ContentType.getName(), JSON_UTF_8);
        request.setContent(payload.getSupplier());
        return request;
    }
    
    private void closeDriverSession() throws NoSuchSessionException {
        List<String> windowHandles = getWindowHandles(sessionId);
        while ( ! windowHandles.isEmpty()) {
            HttpResponse response = HtmlUnitDriverServer.closeWindow(sessionId);
            assertEquals("Failed closing window", HTTP_OK, response.getStatus());
            windowHandles = extractListOfStrings(response);
        }
    }
    
    private List<String> getWindowHandles(final String sessionId) throws NoSuchSessionException {
        HttpResponse response = HtmlUnitDriverServer.getWindowHandles(sessionId);
        assertEquals("Failed getting window handles", HTTP_OK, response.getStatus());
        return extractListOfStrings(response);
    }
    
    protected <T> T extractValueOfType(final HttpResponse response, final Type type) {
        Map<String, T> content = fromJson(response, type);
        assertTrue("Failed finding response value", content.containsKey("value"));
        return content.get("value");
    }
    
    protected Object extractValue(final HttpResponse response) {
        Map<String, Object> content = fromJson(response, MAP_OF_OBJECTS);
        assertTrue("Failed finding response value", content.containsKey("value"));
        return content.get("value");
    }
    
    @SuppressWarnings("unchecked")
    protected Map<String, Object> extractMap(final HttpResponse response) {
        Object value = extractValue(response);
        assertNotNull("Value should be non-null", value);
        assertTrue("Value should be Map; was " + value.getClass().getName(), value instanceof Map);
        return (Map<String, Object>) value;
    }
    
    @SuppressWarnings("unchecked")
    protected List<String> extractListOfStrings(final HttpResponse response) {
        Object value = extractValue(response);
        assertNotNull("Value should be non-null", value);
        assertTrue("Value should be List; was " + value.getClass().getName(), value instanceof List);
        return (List<String>) value;
    }
    
    @SuppressWarnings("unchecked")
    protected List<Object> extractListOfObjects(final HttpResponse response) {
        Object value = extractValue(response);
        assertNotNull("Value should be non-null", value);
        assertTrue("Value should be List; was " + value.getClass().getName(), value instanceof List);
        return (List<Object>) value;
    }
    
    protected String extractString(final HttpResponse response) {
        Object value = extractValue(response);
        assertNotNull("Value should be non-null", value);
        assertTrue("Value should be String; was " + value.getClass().getName(), value instanceof String);
        return (String) value;
    }
    
    protected Boolean extractBoolean(final HttpResponse response) {
        Object value = extractValue(response);
        assertNotNull("Value should be non-null", value);
        assertTrue("Value should be Boolean; was " + value.getClass().getName(), value instanceof Boolean);
        return (Boolean) value;
    }
    
    protected Object toElement(final Object content) {
        return new JsonToHtmlUnitWebElementConverter((HtmlUnitDriver) getWebDriver()).apply(content);
    }
}
