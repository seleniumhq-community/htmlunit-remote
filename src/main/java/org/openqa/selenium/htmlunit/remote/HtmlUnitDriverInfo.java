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
import static org.openqa.selenium.remote.CapabilityType.BROWSER_NAME;

import java.util.Optional;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.ImmutableCapabilities;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverInfo;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.htmlunit.options.HtmlUnitDriverOptions;
import org.openqa.selenium.remote.CapabilityType;

import com.google.auto.service.AutoService;
import com.google.common.annotations.VisibleForTesting;

/**
 * Describes an {@link HtmlUnitDriver} instance. This allows services to query the system at
 * run time and offer <b>HtmlUnitDriver</b> instances should they be available.
 */
@AutoService(WebDriverInfo.class)
public class HtmlUnitDriverInfo implements WebDriverInfo {

    /**
     * Default constructor
     */
    public HtmlUnitDriverInfo() { }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return "HtmlUnit";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Capabilities getCanonicalCapabilities() {
        return new ImmutableCapabilities(BROWSER_NAME, HTMLUNIT.browserName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSupporting(final Capabilities capabilities) {
        return HTMLUNIT.is(capabilities);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSupportingCdp() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSupportingBiDi() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isPresent() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMaximumSimultaneousSessions() {
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<WebDriver> createDriver(final Capabilities capabilities) throws SessionNotCreatedException {
        if (!isAvailable()) {
            return Optional.empty();
        }
        
        Capabilities normalized = normalizeBrowserVersion(capabilities);
        return Optional.of(new HtmlUnitDriver(new HtmlUnitDriverOptions().merge(normalized)));
    }

    /**
     * The following implementation ensures grid-compatible handling of
     * the [browserVersion] capability. If the standard [browserVersion]
     * capability is defined, it's removed. If the [garg:browserVersion]
     * is undefined, the original value of [browserVersion] is adopted.
     * 
     * @param capabilities {@link Capabilities} object to normalize
     * @return grid-compatible capabilities with normalized [browserVersion]
     */
    @VisibleForTesting
    static MutableCapabilities normalizeBrowserVersion(final Capabilities capabilities) {
        // create mutable Capabilities object
        MutableCapabilities mutable = new MutableCapabilities(capabilities);
        // get standard [browserVersion] if non-null
        Optional.ofNullable(mutable.getBrowserVersion())
            // filter out if value is empty
            .filter(versionVal -> !versionVal.isEmpty())
            // if value exists
            .ifPresent(versionVal -> {
                // if vendor-specific browser version is undefined
                if (mutable.getCapability(HtmlUnitDriverOptions.BROWSER_VERSION) == null) {
                    // set value of vendor-specific browser version
                    mutable.setCapability(HtmlUnitDriverOptions.BROWSER_VERSION, versionVal);
                }
                // remove standard [browserVersion] capability
                mutable.setCapability(CapabilityType.BROWSER_VERSION, (String) null);
            }
        );
        return mutable;
    }
}
