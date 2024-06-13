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
import static org.openqa.selenium.htmlunit.WebDriverTestCase.TestPage.ALERTS;
import static org.openqa.selenium.support.ui.ExpectedConditions.alertIsPresent;
import java.time.Duration;
import org.junit.After;
import org.junit.Test;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.CommandPayload;
import org.openqa.selenium.remote.DriverCommand;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class HtmlUnitDriverRemoteAlertTest extends RemoteWebDriverTestCase {
    @After
    public void closeAlertIfPresent() {
        try {
            getWebDriver().switchTo().alert().dismiss();
        } catch (WebDriverException ignore) {
        }
    }

    @Test
    public void shouldBeAbleToDismissAlert() throws Exception {
    	getWebDriver().get(testPage(ALERTS));
        getWebDriver().findElement(By.id("alert-to-dismiss")).click();
        getWait().until(alertIsPresent());
        HttpResponse response = server.dismissAlert(sessionId);
        assertEquals("Failed dismissing alert", HTTP_OK, response.getStatus());
        
        // If we can perform any action, we're good to go
        assertEquals("Testing Alerts", getWebDriver().getTitle());
    }
    
    @Test
    public void shouldBeAbleToAcceptAlert() throws Exception {
    	getWebDriver().get(testPage(ALERTS));
        getWebDriver().findElement(By.id("alert-to-accept")).click();
        getWait().until(alertIsPresent());
        HttpResponse response = server.acceptAlert(sessionId);
        assertEquals("Failed accepting alert", HTTP_OK, response.getStatus());
        
        // If we can perform any action, we're good to go
        assertEquals("Testing Alerts", getWebDriver().getTitle());
    }
    
    @Test
    public void shouldBeAbleToGetAlertText() throws Exception {
    	getWebDriver().get(testPage(ALERTS));
        getWebDriver().findElement(By.id("alert-to-scrape")).click();
        Alert alert = getWait().until(alertIsPresent());
        HttpResponse response = server.getAlertText(sessionId);
        assertEquals("Failed getting alert text", HTTP_OK, response.getStatus());
        assertEquals("Scrape this alert!", extractString(response));
        alert.dismiss();
        
        // If we can perform any action, we're good to go
        assertEquals("Testing Alerts", getWebDriver().getTitle());
    }
    
    @Test
    public void shouldBeAbleToSendAlertText() throws Exception {
    	getWebDriver().get(testPage(ALERTS));
        getWebDriver().findElement(By.id("prompt")).click();
        Alert alert = getWait().until(alertIsPresent());
        CommandPayload payload = DriverCommand.SET_ALERT_VALUE("success");
        HttpRequest request = commandCodec.encode(new Command(sessionId(), payload));
        HttpResponse response = server.sendAlertText(request, sessionId);
        assertEquals("Failed getting alert text", HTTP_OK, response.getStatus());
        alert.accept();
        
        getWait().until(textInElementLocated(By.id("text"), "success"));
    }
    
    private WebDriverWait getWait() {
        return new WebDriverWait(getWebDriver(), Duration.ofSeconds(10));
    }
    
    private static ExpectedCondition<Boolean> textInElementLocated(final By locator, final String text) {
        return driver -> text.equals(driver.findElement(locator).getText());
    }
}