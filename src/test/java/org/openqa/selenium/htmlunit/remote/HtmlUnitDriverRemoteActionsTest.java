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

import java.time.Duration;

import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.htmlunit.HtmlUnitWebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.CommandPayload;
import org.openqa.selenium.remote.DriverCommand;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;

public class HtmlUnitDriverRemoteActionsTest extends RemoteWebDriverTestCase {
    @Test
    public void shouldBeAbleToProcessActions() throws Exception {
        getWebDriver().get("https://www.selenium.dev/selenium/web/mouse_interaction.html");
        HtmlUnitWebElement clickable = (HtmlUnitWebElement) getWebDriver().findElement(By.id("clickable"));
        Actions actions = new Actions(getWebDriver())
                .moveToElement(clickable)
                .pause(Duration.ofSeconds(1))
                .click()
                .pause(Duration.ofSeconds(1))
                .sendKeys("abc");
        CommandPayload payload = DriverCommand.ACTIONS(actions.getSequences());
        HttpRequest request = commandCodec.encode(new Command(sessionId(), payload));
        HttpResponse response = HtmlUnitDriverServer.performActions(request, sessionId);
        assertEquals("Failed performing actions", HTTP_OK, response.getStatus());
        assertEquals("Target field value mismatch", "abc", clickable.getAttribute("value"));
    }

    @Test
    public void shouldBeAbleToReleaseActions() {
        HttpResponse response = HtmlUnitDriverServer.releaseActions(sessionId);
        assertEquals("Failed releasing actions", HTTP_OK, response.getStatus());
    }
}