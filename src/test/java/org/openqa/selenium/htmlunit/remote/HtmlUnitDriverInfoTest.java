package org.openqa.selenium.htmlunit.remote;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.htmlunit.options.HtmlUnitDriverOptions;
import org.openqa.selenium.remote.Browser;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;

public class HtmlUnitDriverInfoTest {
    
    @Test
    public void testNormalizationOfStandardCapabilities() {
        DesiredCapabilities desired =
                // set [browserName] and [browserVersion]
                new DesiredCapabilities(Browser.HTMLUNIT.browserName(), Browser.EDGE.browserName(), null);
        
        Capabilities capabilities = HtmlUnitDriverInfo.normalizeBrowserVersion(desired);
        assertEquals(Browser.HTMLUNIT.browserName(), capabilities.getBrowserName());
        assertEquals(null, capabilities.getCapability(CapabilityType.BROWSER_VERSION));
        assertEquals(Browser.EDGE.browserName(), capabilities.getCapability(HtmlUnitDriverOptions.BROWSER_VERSION));
    }

    @Test
    public void testNormalizationOfVendorSpecificCapabilities() {
        DesiredCapabilities desired =
                // set [browserName]
                new DesiredCapabilities(Browser.HTMLUNIT.browserName(), null, null);
        
        // set [garg:browserVersion]
        desired.setCapability(HtmlUnitDriverOptions.BROWSER_VERSION, Browser.FIREFOX.browserName());
        
        Capabilities capabilities = HtmlUnitDriverInfo.normalizeBrowserVersion(desired);
        assertEquals(Browser.HTMLUNIT.browserName(), capabilities.getBrowserName());
        assertEquals(null, capabilities.getCapability(CapabilityType.BROWSER_VERSION));
        assertEquals(Browser.FIREFOX.browserName(), capabilities.getCapability(HtmlUnitDriverOptions.BROWSER_VERSION));
    }

    @Test
    public void testVendorSpecificBrowserVersionOverride() {
        DesiredCapabilities desired =
                // set [browserName] and [browserVersion]
                new DesiredCapabilities(Browser.HTMLUNIT.browserName(), Browser.IE.browserName(), null);
        
        // set [garg:browserVersion]
        desired.setCapability(HtmlUnitDriverOptions.BROWSER_VERSION, Browser.CHROME.browserName());
        
        Capabilities capabilities = HtmlUnitDriverInfo.normalizeBrowserVersion(desired);
        assertEquals(Browser.HTMLUNIT.browserName(), capabilities.getBrowserName());
        assertEquals(null, capabilities.getCapability(CapabilityType.BROWSER_VERSION));
        assertEquals(Browser.CHROME.browserName(), capabilities.getCapability(HtmlUnitDriverOptions.BROWSER_VERSION));
    }

}
