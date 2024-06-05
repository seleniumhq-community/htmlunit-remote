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
import static org.openqa.selenium.htmlunit.remote.HtmlUnitDriverServer.DRIVER_BINARY_ROLE;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.grid.Bootstrap;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.htmlunit.options.BrowserVersionTrait;
import org.openqa.selenium.htmlunit.options.HtmlUnitDriverOptions;
import org.openqa.selenium.htmlunit.options.HtmlUnitOption;
import org.openqa.selenium.htmlunit.options.OptionEnum;
import org.openqa.selenium.remote.service.DriverService;

import com.google.auto.service.AutoService;
import com.nordstrom.common.file.PathUtils;
import com.nordstrom.common.jar.JarUtils;

public class HtmlUnitDriverService extends DriverService {
    
    protected HtmlUnitDriverService(final File executable, final int port, final Duration timeout,
            final List<String> args, final Map<String, String> environment) throws IOException {
        super(executable, port, timeout, args, environment);
    }

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

    @Override
    public String getDriverName() {
        return HTMLUNIT.browserName();
    }
    
    @Override
    public String getExecutable() {
        // if no executable is spec'd
        if (super.getExecutable() == null) {
            // specify current Java installation as executable
            String javaPath = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            setExecutable(PathUtils.findExecutableOnSystemPath(javaPath));
        }
        return super.getExecutable();
    }
    
    @Override
    public void start() throws IOException {
        getArgs().add("--port");
        getArgs().add(Integer.toString(getUrl().getPort()));
        super.start();
    }
    
    @AutoService(DriverService.Builder.class)
    public static class Builder extends DriverService.Builder<HtmlUnitDriverService, HtmlUnitDriverService.Builder> {
        
        private static final String[] DEPENDENCY_CONTEXTS = { "ch.qos.logback.core.Layout",
                "com.beust.jcommander.Strings", "com.google.common.base.Utf8", "com.nordstrom.common.jar.JarUtils",
                "dev.failsafe.Call", "graphql.Assert", "io.netty.buffer.ByteBuf", "io.netty.channel.Channel",
                "io.netty.handler.codec.Headers", "io.netty.handler.codec.http.Cookie", "io.netty.handler.ssl.SslUtils",
                "io.netty.resolver.NameResolver", "io.netty.util.Timer",
                "io.opentelemetry.api.incubator.events.EventLoggerProvider", "io.opentelemetry.api.logs.Logger",
                "io.opentelemetry.api.trace.Span", "io.opentelemetry.context.Scope",
                "io.opentelemetry.sdk.autoconfigure.ResourceConfiguration",
                "io.opentelemetry.sdk.autoconfigure.spi.Ordered", "io.opentelemetry.sdk.common.Clock",
                "io.opentelemetry.sdk.logs.LogLimits", "io.opentelemetry.sdk.metrics.View",
                "io.opentelemetry.sdk.OpenTelemetrySdk", "io.opentelemetry.sdk.trace.SdkSpan",
                "io.opentelemetry.semconv.SemanticAttributes", "javax.servlet.http.HttpServletResponse",
                "net.bytebuddy.matcher.ElementMatcher", "org.apache.commons.codec.Encoder",
                "org.apache.commons.exec.Executor", "org.apache.commons.io.IOUtils", "org.apache.commons.lang3.CharSet",
                "org.apache.commons.logging.Log", "org.apache.commons.net.io.Util", "org.apache.commons.text.WordUtils",
                "org.apache.http.client.HttpClient", "org.apache.http.entity.mime.content.ContentBody",
                "org.apache.http.entity.mime.MIME", "org.apache.http.HttpHost", "org.brotli.dec.Utils",
                "org.dataloader.DataLoader", "org.eclipse.jetty.http.Syntax", "org.eclipse.jetty.io.EndPoint",
                "org.eclipse.jetty.server.Server", "org.eclipse.jetty.util.IO", "org.htmlunit.corejs.javascript.Symbol",
                "org.htmlunit.cssparser.parser.CSSErrorHandler", "org.htmlunit.cyberneko.xerces.xni.XNIException",
                "org.htmlunit.jetty.client.Origin", "org.htmlunit.jetty.websocket.api.Session", "org.htmlunit.Version",
                "org.htmlunit.xpath.xml.utils.PrefixResolver", "org.openqa.selenium.By",
                "org.openqa.selenium.grid.Main", "org.openqa.selenium.htmlunit.HtmlUnitDriver",
                "org.openqa.selenium.io.Zip", "org.openqa.selenium.json.Json", "org.openqa.selenium.Keys",
                "org.openqa.selenium.manager.SeleniumManager", "org.openqa.selenium.net.Urls",
                "org.openqa.selenium.remote.http.Route", "org.openqa.selenium.remote.tracing.Tracer",
                "org.openqa.selenium.support.FindBy", "org.reactivestreams.Publisher", "org.slf4j.MDC",
                "org.slf4j.impl.StaticLoggerBinder", "org.zeromq.Utils" };

        private Boolean driverDebug;
        
        public Builder() {
            driverDebug = Boolean.getBoolean(HtmlUnitOption.DRIVER_DEBUG.name);
        }

        @Override
        public int score(final Capabilities capabilities) {
            int score = 0;

            if (HTMLUNIT.is(capabilities.getBrowserName())) {
                score++;
            }

            if (capabilities.getCapability(HtmlUnitDriverOptions.HTMLUNIT_OPTIONS) != null) {
                score++;
            }

            return score;
        }

        /**
         * Specifies whether to launch the driver binary with JDWP debugging.
         *
         * @param driverDebug whether to enable JDWP debugging
         * @return A self reference.
         */
        public Builder withDriverDebug(final Boolean driverDebug) {
          this.driverDebug = driverDebug;
          return this;
        }

        @Override
        protected void loadSystemProperties() {
        }

        @Override
        protected List<String> createArgs() {
            List<String> args = new ArrayList<>();
            
            List<String> contextPaths = JarUtils.getContextPaths(DEPENDENCY_CONTEXTS);
            String classPath = contextPaths.remove(0);
            
            args.add("-cp");
            args.add(classPath);
            
            if (driverDebug) {
                args.add(0, "-agentlib:jdwp=transport=dt_socket,server=y,address=8000");
            }
            
            addOptionProperties(args, HtmlUnitOption.values());
            addOptionProperties(args, BrowserVersionTrait.values());
            args.add(Bootstrap.class.getName());
            args.add("--ext");
            args.add(JarUtils.findJarPathFor(HtmlUnitDriver.class.getName()));
            args.add(DRIVER_BINARY_ROLE.getRoleName());
            
            return args;
        }

        @Override
        protected HtmlUnitDriverService createDriverService(final File exe, final int port, final Duration timeout,
                final List<String> args, final Map<String, String> environment) {
            try {
                return new HtmlUnitDriverService(exe, port, timeout, args, environment);
            } catch (IOException e) {
                throw new WebDriverException(e);
            }
        }
        
        private static <T extends OptionEnum> void addOptionProperties(final List<String> args, final T[] options) {
            for (OptionEnum option : options) {
                String name = option.getPropertyName();
                String value = System.getProperty(name);
                if (value != null) {
                    args.add(0, "-D" + name + "=" + value);
                }
            }
        }
    }
}
