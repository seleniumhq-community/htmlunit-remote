package org.openqa.selenium.htmlunit.remote;

import org.openqa.selenium.Capabilities;
import org.openqa.selenium.grid.data.DefaultSlotMatcher;
import org.openqa.selenium.remote.Browser;

public class HtmlUnitSlotMatcher extends DefaultSlotMatcher {

    private static final long serialVersionUID = 1L;

    @Override
    public boolean matches(Capabilities stereotype, Capabilities capabilities) {

        if (capabilities.asMap().isEmpty()) {
            return false;
        }

        if (!managedDownloadsEnabled(stereotype, capabilities)) {
            return false;
        }
        
        if (Browser.HTMLUNIT.is(stereotype) && Browser.HTMLUNIT.is(capabilities)) {
            return true;
        }
        
        return super.matches(stereotype, capabilities);
    }

    private Boolean managedDownloadsEnabled(Capabilities stereotype, Capabilities capabilities) {
        if (!Boolean.parseBoolean(String.valueOf(capabilities.getCapability("se:downloadsEnabled")))) {
            return true;
        }
        return Boolean.parseBoolean(String.valueOf(stereotype.getCapability("se:downloadsEnabled")));
    }
}
