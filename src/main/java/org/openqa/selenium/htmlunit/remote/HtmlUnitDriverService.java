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

import static org.openqa.selenium.remote.Browser.HTMLUNIT;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.grid.config.MapConfig;
import org.openqa.selenium.grid.server.BaseServerOptions;
import org.openqa.selenium.grid.server.Server;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.htmlunit.options.HtmlUnitDriverOptions;
import org.openqa.selenium.remote.service.DriverService;

import com.google.auto.service.AutoService;

/**
 * Driver service for {@link HtmlUnitDriver} sessions.
 */
public class HtmlUnitDriverService extends DriverService {
    
    private volatile Server<?> sessionServer;
    
    /**
     * Get the session server for this driver service.
     * 
     * @return session server
     */
    public Server<?> getServer() {
        Server<?> localRef = sessionServer;
        if (localRef == null) {
            synchronized (HtmlUnitDriverService.class) {
                localRef = sessionServer;
                if (localRef == null) {                    
                    sessionServer = localRef = new HtmlUnitDriverServer(getOptions());
                }
            }
        }
        return localRef;
    }
    
    /**
     * Get the options for instantiating a new session server.
     * 
     * @return {@link BaseServerOptions} object
     */
    private BaseServerOptions getOptions() {
        return new BaseServerOptions(new MapConfig(Map.of("server", Map.of("port", getUrl().getPort()))));
    }
    
    /**
     * Constructor for driver service for {@link HtmlUnitDriver} sessions.
     * 
     * @param executable driver executable
     * @param port port to start the driver server on
     * @param timeout driver server launch timeout interval
     * @param args driver server launch arguments
     * @param environment driver server environment
     * @throws IOException if an I/O error occurs
     */
    protected HtmlUnitDriverService(final File executable, final int port, final Duration timeout,
            final List<String> args, final Map<String, String> environment) throws IOException {
        super(executable, port, timeout, args, environment);
    }

    /**
     * Get default options for {@link HtmlUnitDriver} sessions.
     */
    @Override
    public Capabilities getDefaultDriverOptions() {
        return new HtmlUnitDriverOptions();
    }

    /**
     * Configures and returns a new {@link HtmlUnitDriverService} using the default
     * configuration.
     *
     * @return A new HtmlUnitDriverService using the default configuration.
     */
    public static HtmlUnitDriverService createDefaultService() {
        return new Builder().build();
    }

    /**
     * Get driver name for {@link HtmlUnitDriver}.
     * 
     * @return driver name (<b>htmlunit</b>)
     */
    @Override
    public String getDriverName() {
        return HTMLUNIT.browserName();
    }
    
    /**
     * Get driver executable for {@link HtmlUnitDriver}
     * 
     * @return the current Java installation
     */
    @Override
    public String getExecutable() {
        // specify current Java installation as executable
        setExecutable(ProcessHandle.current().info().command().get());
        return super.getExecutable();
    }
    
    /**
     * Determine if the driver service session server is running.
     * 
     * @return {@code true} if the session server is initialized and running
     */
    @Override
    public boolean isRunning() {
        synchronized (HtmlUnitDriverService.class) {
            Server<?> localRef = getServer();
            return localRef != null && localRef.isStarted();
        }
    }
    
    /**
     * Start the driver service session server.
     */
    @Override
    public void start() throws IOException {
        synchronized (HtmlUnitDriverService.class) {
            if (isRunning()) return;
            getServer().start();
        }
    }
    
    /**
     * Stop the driver service session server.
     */
    @Override
    public void stop() {
    }
    
    /**
     * Close the driver service session server.
     */
    @Override
    public void close() {
        synchronized (HtmlUnitDriverService.class) {
            if (isRunning()) {
                getServer().stop();
                sessionServer = null;
            }
        }
        if (getOutputStream() instanceof FileOutputStream) {
            try {
                getOutputStream().close();
            } catch (IOException ignore) {
            }
        }
        super.close();
    }
    
    /**
     * Builder class for {@link HtmlUnitDriver} sessions.
     */
    @AutoService(DriverService.Builder.class)
    public static class Builder extends DriverService.Builder<HtmlUnitDriverService, HtmlUnitDriverService.Builder> {
        
        /**
         * Default constructor
         */
        public Builder() { }
        
        /**
         * {@inheritDoc}
         */
        @Override
        public int score(final Capabilities capabilities) {
            int score = 0;
            if (HTMLUNIT.is(capabilities.getBrowserName())) { score++; }
            if (capabilities.getCapability(HtmlUnitDriverOptions.HTMLUNIT_OPTIONS) != null) { score++; }
            return score;
        }

        /**
         * Load the system properties supported by {@link HtmlUnitDriver}.
         */
        @Override
        protected void loadSystemProperties() {
        }

        /**
         * Create the command line arguments list provided to {@link HtmlUnitDriver}.
         * 
         * @return list of command line arguments
         */
        @Override
        protected List<String> createArgs() {
            return List.of();
        }

        /**
         * Create a new driver service for {@link HtmlUnitDriver} sessions.
         *  
         * @param exe driver executable
         * @param port port to start the driver server on
         * @param timeout driver server launch timeout interval
         * @param args driver server launch arguments
         * @param environment driver server environment
         * @return new {@link HtmlUnitDriverService} with specified properties
         * @throws WebDriverException if an I/O error occurs
         */
        @Override
        protected HtmlUnitDriverService createDriverService(final File exe, final int port, final Duration timeout,
                final List<String> args, final Map<String, String> environment) {
            try {
                return new HtmlUnitDriverService(exe, port, timeout, args, environment);
            } catch (IOException e) {
                throw new WebDriverException(e);
            }
        }
    }
}
