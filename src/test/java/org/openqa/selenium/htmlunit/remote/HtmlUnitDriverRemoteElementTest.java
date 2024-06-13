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
import static org.openqa.selenium.htmlunit.WebDriverTestCase.TestPage.HOME;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.htmlunit.HtmlUnitWebElement;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.CommandPayload;
import org.openqa.selenium.remote.Dialect;
import org.openqa.selenium.remote.DriverCommand;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;

public class HtmlUnitDriverRemoteElementTest extends RemoteWebDriverTestCase {
    @Test
    public void shouldBeAbleToGetActiveElement() {
        HttpResponse response = server.getActiveElement(sessionId);
        assertEquals("Failed maximizing window", HTTP_OK, response.getStatus());
        Map<String, Object> activeElement = extractMap(response);
        assertEquals("Active element", Map.of(Dialect.W3C.getEncodedElementKey(), "1"), activeElement);
    }
    
    @Test
    public void shouldBeAbleToFindElement() throws Exception {
        getWebDriver().get(testPage(HOME));
        CommandPayload payload = DriverCommand.FIND_ELEMENT("css selector", "p#para-1");
        HttpRequest request = commandCodec.encode(new Command(sessionId(), payload));
        HttpResponse response = server.findElement(request, sessionId);
        assertEquals("Failed finding element", HTTP_OK, response.getStatus());
        WebElement element = (WebElement) toElement(extractMap(response));
        assertEquals("Element ID", "para-1", element.getAttribute("id"));
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void shouldBeAbleToFindElements() throws Exception {
        getWebDriver().get(testPage(HOME));
        CommandPayload payload = DriverCommand.FIND_ELEMENTS("css selector", "p");
        HttpRequest request = commandCodec.encode(new Command(sessionId(), payload));
        HttpResponse response = server.findElements(request, sessionId);
        assertEquals("Failed finding elements", HTTP_OK, response.getStatus());
        List<WebElement> elements = (List<WebElement>) toElement(extractListOfObjects(response));
        assertEquals("Element count", 4, elements.size());
        assertEquals("Element #1 ID", "para-1", elements.get(0).getAttribute("id"));
        assertEquals("Element #2 ID", "para-2", elements.get(1).getAttribute("id"));
        assertEquals("Element #3 ID", "para-3", elements.get(2).getAttribute("id"));
        assertEquals("Element #4 ID", "hidden-para", elements.get(3).getAttribute("id"));
    }
    
    @Test
    public void shouldBeAbleToFindElementFromElement() throws Exception {
        getWebDriver().get(testPage(HOME));
        HtmlUnitWebElement parent = (HtmlUnitWebElement) getWebDriver().findElement(By.cssSelector("tr#t0-r0"));
        String elementId = String.valueOf(parent.getId());
        CommandPayload payload = DriverCommand.FIND_CHILD_ELEMENT(elementId, "css selector", "td");
        HttpRequest request = commandCodec.encode(new Command(sessionId(), payload));
        HttpResponse response = server.findElementFromElement(request, sessionId, elementId);
        assertEquals("Failed finding element from element", HTTP_OK, response.getStatus());
        WebElement element = (WebElement) toElement(extractMap(response));
        assertEquals("Element ID", "t0-r0-c0", element.getAttribute("id"));
    }
    
    @Test
    @SuppressWarnings("unchecked")
    public void shouldBeAbleToFindElementsFromElement() throws Exception {
        getWebDriver().get(testPage(HOME));
        HtmlUnitWebElement parent = (HtmlUnitWebElement) getWebDriver().findElement(By.cssSelector("tr#t0-r0"));
        String elementId = String.valueOf(parent.getId());
        CommandPayload payload = DriverCommand.FIND_CHILD_ELEMENT(elementId, "css selector", "td");
        HttpRequest request = commandCodec.encode(new Command(sessionId(), payload));
        HttpResponse response = server.findElementsFromElement(request, sessionId, elementId);
        assertEquals("Failed finding elements from element", HTTP_OK, response.getStatus());
        List<WebElement> elements = (List<WebElement>) toElement(extractListOfObjects(response));
        assertEquals("Element count", 2, elements.size());
        assertEquals("Element #1 ID", "t0-r0-c0", elements.get(0).getAttribute("id"));
        assertEquals("Element #2 ID", "t0-r0-c1", elements.get(1).getAttribute("id"));
    }
    
    @Test
    public void shouldBeAbleToCheckIsElementSelected() throws Exception {
        getWebDriver().get(testPage(HOME));
        HtmlUnitWebElement element = (HtmlUnitWebElement) getWebDriver().findElement(By.cssSelector("input#checkbox"));
        HttpResponse response = server.isElementSelected(sessionId, String.valueOf(element.getId()));
        assertEquals("Failed checking is element selected", HTTP_OK, response.getStatus());
        assertFalse("Element should not be selected", extractBoolean(response));
        element.click();
        response = server.isElementSelected(sessionId, String.valueOf(element.getId()));
        assertEquals("Failed checking is element selected", HTTP_OK, response.getStatus());
        assertTrue("Element should be select", extractBoolean(response));
    }    
    
    @Test
    public void shouldBeAbleToGetElementAttribute() throws Exception {
        getWebDriver().get(testPage(HOME));
        HtmlUnitWebElement element = (HtmlUnitWebElement) getWebDriver().findElement(By.cssSelector("input#input-field"));
        element.clear();
        element.sendKeys("Hautelook");
        HttpResponse response = server.getElementDomAttribute(sessionId, String.valueOf(element.getId()), "value");
        assertEquals("Failed getting element attribute", HTTP_OK, response.getStatus());
        assertEquals("Element attribute", "Nordstrom", extractString(response));
    }
    
    @Test
    public void shouldBeAbleToGetElementProperty() throws Exception {
        getWebDriver().get(testPage(HOME));
        HtmlUnitWebElement element = (HtmlUnitWebElement) getWebDriver().findElement(By.cssSelector("input#input-field"));
        element.clear();
        element.sendKeys("Hautelook");
        HttpResponse response = server.getElementDomProperty(sessionId, String.valueOf(element.getId()), "value");
        assertEquals("Failed getting element property", HTTP_OK, response.getStatus());
        assertEquals("Element property", "Hautelook", extractString(response));
    }
    
    @Test
    public void shouldBeAbleToGetElementCssValue() throws Exception {
        getWebDriver().get(testPage(HOME));
        HtmlUnitWebElement element = (HtmlUnitWebElement) getWebDriver().findElement(By.cssSelector("table#t1"));
        HttpResponse response = server.getElementCssValue(sessionId, String.valueOf(element.getId()), "border-color");
        assertEquals("Failed getting element CSS value", HTTP_OK, response.getStatus());
        assertEquals("Element CSS value", "rgba(0, 0, 0, 1)", extractString(response));
    }
    
    @Test
    public void shouldBeAbleToGetElementText() throws Exception {
        getWebDriver().get(testPage(HOME));
        HtmlUnitWebElement element = (HtmlUnitWebElement) getWebDriver().findElement(By.cssSelector("p#para-2"));
        HttpResponse response = server.getElementText(sessionId, String.valueOf(element.getId()));
        assertEquals("Failed getting element text", HTTP_OK, response.getStatus());
        assertEquals("Element CSS value", "This is paragraph two.", extractString(response));
    }
    
    @Test
    public void shouldBeAbleToGetElementTagName() throws Exception {
        getWebDriver().get(testPage(HOME));
        HtmlUnitWebElement element = (HtmlUnitWebElement) getWebDriver().findElement(By.id("form-div"));
        HttpResponse response = server.getElementTagName(sessionId, String.valueOf(element.getId()));
        assertEquals("Failed getting element tag name", HTTP_OK, response.getStatus());
        assertEquals("Element tag name", "div", extractString(response));
    }
    
    @Test
    public void shouldBeAbleToGetElementRect() throws Exception {
        getWebDriver().get(testPage(HOME));
        HtmlUnitWebElement element = (HtmlUnitWebElement) getWebDriver().findElement(By.cssSelector("td#t0-r0-c0"));
        HttpResponse response = server.getElementRect(sessionId, String.valueOf(element.getId()));
        assertEquals("Failed getting element rect", HTTP_OK, response.getStatus());
        Map<String, Object> elementRect = extractMap(response);
        assertEquals("Element width", 1256L, elementRect.get("width"));
        assertEquals("Element height", 72L, elementRect.get("height"));
        assertEquals("Element 'x' position", 5L, elementRect.get("x"));
        assertEquals("Element 'y' position", 5L, elementRect.get("y"));
    }
    
    @Test
    public void shouldBeAbleToCheckIsElementEnabled() throws Exception {
        getWebDriver().get(testPage(HOME));
        HtmlUnitWebElement element = (HtmlUnitWebElement) getWebDriver().findElement(By.cssSelector("input#checkbox"));
        HttpResponse response = server.isElementEnabled(sessionId, String.valueOf(element.getId()));
        assertEquals("Failed checking is element enabled", HTTP_OK, response.getStatus());
        assertTrue("Element should be enabled", extractBoolean(response));
    }
    
    @Test
    public void shouldBeAbleToClickElement() throws Exception {
        getWebDriver().get(testPage(HOME));
        HtmlUnitWebElement element = (HtmlUnitWebElement) getWebDriver().findElement(By.cssSelector("input#checkbox"));
        assertFalse("Element should not be selected", element.isSelected());
        HttpResponse response = server.elementClick(sessionId, String.valueOf(element.getId()));
        assertEquals("Failed clicking element", HTTP_OK, response.getStatus());
        assertTrue("Element should be select", element.isSelected());
    }
    
    @Test
    public void shouldBeAbleToClearElement() throws Exception {
        getWebDriver().get(testPage(HOME));
        HtmlUnitWebElement element = (HtmlUnitWebElement) getWebDriver().findElement(By.cssSelector("input#input-field"));
        assertEquals("Initial field value", "Nordstrom", element.getDomProperty("value"));
        HttpResponse response = server.elementClear(sessionId, String.valueOf(element.getId()));
        assertEquals("Failed clearing element", HTTP_OK, response.getStatus());
        assertEquals("Element should be clear", "", element.getDomProperty("value"));
    }
    
    @Test
    public void shouldBeAbleToSendKeysToElement() throws Exception {
        getWebDriver().get(testPage(HOME));
        HtmlUnitWebElement element = (HtmlUnitWebElement) getWebDriver().findElement(By.cssSelector("input#input-field"));
        assertEquals("Initial field value", "Nordstrom", element.getDomProperty("value"));
        String elementId = String.valueOf(element.getId());
        CommandPayload payload = DriverCommand.SEND_KEYS_TO_ELEMENT(elementId, new CharSequence[]{ " Rack" });
        HttpRequest request = commandCodec.encode(new Command(sessionId(), payload));
        HttpResponse response = server.elementSendKeys(request, sessionId, elementId);
        assertEquals("Failed sending keys to element", HTTP_OK, response.getStatus());
        assertEquals("Field value mismatch", "Nordstrom Rack", element.getDomProperty("value"));
    }
}