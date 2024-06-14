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
import static org.junit.Assert.fail;
import static org.openqa.selenium.htmlunit.WebDriverTestCase.TestPage.SIMPLE;

import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.CommandPayload;
import org.openqa.selenium.remote.DriverCommand;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;

public class HtmlUnitDriverRemoteNavigationTest extends RemoteWebDriverTestCase {
    @Test
    public void shouldBeAbleToNavigateToUrl() {
        CommandPayload payload = DriverCommand.GET(HTMLUNIT_HOME);
        HttpRequest request = commandCodec.encode(new Command(sessionId(), payload));
        HttpResponse response = HtmlUnitDriverServer.navigateTo(request, sessionId);
        assertEquals("Failed navigating to HtmlUnit home", HTTP_OK, response.getStatus());
        assertEquals("Current URL", HTMLUNIT_HOME, getWebDriver().getCurrentUrl());
    }
    
    @Test
    public void shouldBeAbleToGetCurrentUrl() {
        getWebDriver().get(HTMLUNIT_HOME);
        HttpResponse response = HtmlUnitDriverServer.getCurrentUrl(sessionId);
        assertEquals("Failed getting current URL", HTTP_OK, response.getStatus());
        assertEquals("Current URL", HTMLUNIT_HOME, extractString(response));
    }
    
    @Test
    public void shouldBeAbleToNavigateBackAndForward() {
        getWebDriver().get(HTMLUNIT_HOME);
        getWebDriver().get(HTMLUNIT_HISTORY);
        assertEquals("Initial URL after setup", HTMLUNIT_HISTORY, getWebDriver().getCurrentUrl());
        HttpResponse response = HtmlUnitDriverServer.navigateBack(sessionId);
        assertEquals("Failed navigating back", HTTP_OK, response.getStatus());
        assertEquals("Current URL after navigating back", HTMLUNIT_HOME, getWebDriver().getCurrentUrl());
        response = HtmlUnitDriverServer.navigateForward(sessionId);
        assertEquals("Failed navigating forward", HTTP_OK, response.getStatus());
        assertEquals("Current URL after navigating forward", HTMLUNIT_HISTORY, getWebDriver().getCurrentUrl());
    }
    
    @Test(expected = StaleElementReferenceException.class)
    public void shouldBeAbleToRefreshSession() {
        getWebDriver().get(HTMLUNIT_HOME);
        WebElement bannerImage = getWebDriver().findElement(By.cssSelector("img[alt=HtmlUnit]"));
        HttpResponse response = HtmlUnitDriverServer.refreshSession(sessionId);
        assertEquals("Failed refreshing session", HTTP_OK, response.getStatus());
        bannerImage.getTagName();
        fail("Element not stale after refresh");
    }
    
    @Test
    public void shouldBeAbleToGetPageTitle() throws Exception {
        getWebDriver().get(testPage(SIMPLE));
        HttpResponse response = HtmlUnitDriverServer.getTitle(sessionId);
        assertEquals("Failed getting page title", HTTP_OK, response.getStatus());
        assertEquals("Page title", "page title", extractString(response));
    }
}