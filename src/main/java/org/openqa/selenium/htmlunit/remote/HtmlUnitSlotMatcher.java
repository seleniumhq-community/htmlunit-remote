package org.openqa.selenium.htmlunit.remote;

import java.io.Serializable;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.grid.data.SlotMatcher;
import org.openqa.selenium.remote.Browser;

public class HtmlUnitSlotMatcher implements SlotMatcher, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public boolean matches(Capabilities stereotype, Capabilities capabilities) {

        if (capabilities.asMap().isEmpty()) {
            return false;
        }

        if (!managedDownloadsEnabled(stereotype, capabilities)) {
            return false;
        }
        
        return Browser.HTMLUNIT.is(capabilities);
    }

    private Boolean managedDownloadsEnabled(Capabilities stereotype, Capabilities capabilities) {
        if (!Boolean.parseBoolean(String.valueOf(capabilities.getCapability("se:downloadsEnabled")))) {
            return true;
        }
        return Boolean.parseBoolean(String.valueOf(stereotype.getCapability("se:downloadsEnabled")));
    }
}
