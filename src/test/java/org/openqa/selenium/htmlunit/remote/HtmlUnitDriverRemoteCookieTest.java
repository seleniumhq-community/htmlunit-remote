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
import java.lang.reflect.Type;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.json.TypeToken;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.CommandPayload;
import org.openqa.selenium.remote.DriverCommand;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;

public class HtmlUnitDriverRemoteCookieTest extends RemoteWebDriverTestCase {
    protected static final Type MAP_OF_COOKIES = new TypeToken<Map<String, CookieCoercer>>() {}.getType();
    protected static final Type MAP_OF_LIST_OF_COOKIES = new TypeToken<Map<String, List<CookieCoercer>>>() {}.getType();
    
    @Test
    public void shouldBeAbleToGetAllCookies() {
        setupCookies();
        HttpResponse response = server.getAllCookies(sessionId);
        assertEquals("Failed getting all cookies", HTTP_OK, response.getStatus());
        Set<Cookie> cookies = extractCookies(response);
        Set<Cookie> expect = Set.of(new Cookie("cookie1", "value1"),
                                    new Cookie("cookie2", "value2"),
                                    new Cookie("cookie3", "value3"));
        assertEquals("Count of cookies", 3, cookies.size());
        assertEquals("Cookie collection", expect, cookies);
    }
    
    @Test
    public void shouldBeAbleToGetNamedCookie() {
        setupCookies();
        HttpResponse response = server.getNamedCookie(sessionId, "cookie2");
        assertEquals("Failed getting named cookie 'cookie2'", HTTP_OK, response.getStatus());
        assertEquals("Cookie mismatch", new Cookie("cookie2", "value2"), extractCookie(response));
    }
    
    @Test
    public void shouldBeAbleToAddCookie() {
        setupCookies();
        Date date = new Date(System.currentTimeMillis() + 60_000);
        Cookie cookie = new Cookie.Builder("cookie4", "value4").isSecure(true)
                .isHttpOnly(true).expiresOn(date).build();
        CommandPayload payload = DriverCommand.ADD_COOKIE(cookie);
        HttpRequest request = commandCodec.encode(new Command(sessionId(), payload));
        HttpResponse response = server.addCookie(request, sessionId);
        assertEquals("Failed adding named cookie 'cookie4'", HTTP_OK, response.getStatus());
        assertEquals("Cookie mismatch", cookie, getWebDriver().manage().getCookieNamed("cookie4"));
    }
    
    @Test
    public void shouldBeAbleToDeleteCookie() {
        setupCookies();
        HttpResponse response = server.deleteNamedCookie(sessionId, "cookie2");
        assertEquals("Failed deleting named cookie 'cookie2'", HTTP_OK, response.getStatus());
        Set<Cookie> cookies = getWebDriver().manage().getCookies();
        Set<Cookie> expect = Set.of(new Cookie("cookie1", "value1"), new Cookie("cookie3", "value3"));
        assertEquals("Count of cookies", 2, cookies.size());
        assertEquals("Cookie collection", expect, cookies);
    }
    
    @Test
    public void shouldBeAbleToDeleteAllCookies() {
        setupCookies();
        HttpResponse response = server.deleteAllCookies(sessionId);
        assertEquals("Failed deleting all cookies", HTTP_OK, response.getStatus());
        assertEquals("Count of cookies", Set.of(), getWebDriver().manage().getCookies());
    }
    
    private void setupCookies() {
        getWebDriver().get(HTMLUNIT_HOME);
        getWebDriver().manage().addCookie(new Cookie("cookie1", "value1"));
        getWebDriver().manage().addCookie(new Cookie("cookie2", "value2"));
        getWebDriver().manage().addCookie(new Cookie("cookie3", "value3"));
    }
    
    private Cookie extractCookie(final HttpResponse response) {
        return extractValueOfType(response, MAP_OF_COOKIES);
    }
    
    private Set<Cookie> extractCookies(final HttpResponse response) {
        List<Cookie> cookies = extractValueOfType(response, MAP_OF_LIST_OF_COOKIES);
        return Set.copyOf(cookies);
    }
    
}