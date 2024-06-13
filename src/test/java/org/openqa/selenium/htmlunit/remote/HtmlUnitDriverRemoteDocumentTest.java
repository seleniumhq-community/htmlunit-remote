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
import static org.openqa.selenium.htmlunit.WebDriverTestCase.TestPage.SIMPLE;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.CommandPayload;
import org.openqa.selenium.remote.DriverCommand;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class HtmlUnitDriverRemoteDocumentTest extends RemoteWebDriverTestCase {
    @Test
    public void shouldBeAbleToGetPageSource() throws Exception {
    	getWebDriver().get(testPage(SIMPLE));
        HttpResponse response = server.getPageSource(sessionId);
        assertEquals("Failed getting page source", HTTP_OK, response.getStatus());
        verifyXmlEquals("Page source", getFileContent("SimplePage.html"), extractString(response));
    }
    
    @Test
    public void shouldBeAbleToExecuteScript() throws Exception {
    	getWebDriver().get(testPage(HOME));
        final String selector = "input#checkbox";
        CommandPayload payload = DriverCommand.EXECUTE_SCRIPT("return document.querySelector(arguments[0]);", List.of(selector));
        HttpRequest request = commandCodec.encode(new Command(sessionId(), payload));
        HttpResponse response = server.executeScript(request, sessionId, false);
        assertEquals("Failed executing string", HTTP_OK, response.getStatus());
        WebElement element = (WebElement) toElement(extractMap(response));
        assertEquals("Script result mismatch", getWebDriver().findElement(By.cssSelector(selector)), element);
    }
    
    @Test
    public void shouldBeAbleToExecuteAsyncScript() throws Exception {
    	getWebDriver().get(testPage(HOME));
        final String selector = "input#checkbox";
        CommandPayload payload = DriverCommand.EXECUTE_SCRIPT("arguments[1](document.querySelector(arguments[0]));", List.of(selector));
        HttpRequest request = commandCodec.encode(new Command(sessionId(), payload));
        HttpResponse response = server.executeScript(request, sessionId, true);
        assertEquals("Failed executing string", HTTP_OK, response.getStatus());
        WebElement element = (WebElement) toElement(extractMap(response));
        assertEquals("Script result mismatch", getWebDriver().findElement(By.cssSelector(selector)), element);
    }
    
    private void verifyXmlEquals(final String message, final String expect, final String actual)
            throws SAXException, IOException, ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setIgnoringElementContentWhitespace(true);
        dbf.setIgnoringComments(true);
        DocumentBuilder db = dbf.newDocumentBuilder();

        Document expectDoc = db.parse(new ByteArrayInputStream(expect.getBytes()));
        expectDoc.normalizeDocument();

        Document actualDoc = db.parse(new ByteArrayInputStream(expect.getBytes()));
        actualDoc.normalizeDocument();

        assertTrue(message, expectDoc.isEqualNode(actualDoc));
    }
}