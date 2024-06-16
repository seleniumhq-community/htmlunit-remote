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

import java.util.Map;

import org.junit.Test;
import org.openqa.selenium.remote.Command;
import org.openqa.selenium.remote.CommandPayload;
import org.openqa.selenium.remote.DriverCommand;
import org.openqa.selenium.remote.http.HttpRequest;
import org.openqa.selenium.remote.http.HttpResponse;

public class HtmlUnitDriverRemoteTimeoutTest extends RemoteWebDriverTestCase {
    @Test
    public void shouldBeAbleToGetTimeouts() {
        HttpResponse response = HtmlUnitDriverServer.getTimeouts(sessionId);
        assertEquals("Failed getting timeouts", HTTP_OK, response.getStatus());
        Map<String, Object> timeouts = extractMap(response);
        assertEquals("Script timeout", 0L, timeouts.get("script"));
        assertEquals("Page load timeout", 0L, timeouts.get("pageLoad"));
        assertEquals("Implicit timeout", 0L, timeouts.get("implicit"));
    }
    
    @Test
    public void shouldBeAbleToSetTimeouts() {
        CommandPayload payload = new CommandPayload(DriverCommand.SET_TIMEOUT,
                Map.of("script", 7L, "pageLoad", 15L, "implicit", 31L));
        HttpRequest request = commandCodec.encode(new Command(sessionId(), payload));
        HttpResponse response = HtmlUnitDriverServer.setTimeouts(request, sessionId);
        assertEquals("Failed setting timeouts", HTTP_OK, response.getStatus());
        Map<String, Object> timeouts = extractMap(response);
        assertEquals("Script timeout", 7L, timeouts.get("script"));
        assertEquals("Page load timeout", 15L, timeouts.get("pageLoad"));
        assertEquals("Implicit timeout", 31L, timeouts.get("implicit"));
    }
}