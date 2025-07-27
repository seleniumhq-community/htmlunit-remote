package org.openqa.selenium.htmlunit.remote;

import java.util.Map;

import org.openqa.selenium.WrapsElement;
import org.openqa.selenium.htmlunit.HtmlUnitWebElement;
import org.openqa.selenium.remote.Dialect;
import org.openqa.selenium.remote.WebElementToJsonConverter;

/**
 * Converts {@link HtmlUnitWebElement} objects, which may be {@link WrapsElement wrapped}, into their
 * <b>JSON</b> representation as defined by the WebDriver wire protocol. This class will recursively
 * convert Lists and Maps to catch nested references.
 *
 * @see <a href="https://github.com/SeleniumHQ/selenium/wiki/JsonWireProtocol#webelement-json-object">
 *     WebDriver JSON Wire Protocol</a>
 */
public class HtmlUnitWebElementToJsonConverter extends WebElementToJsonConverter {
    
    /**
     * Default constructor
     */
    public HtmlUnitWebElementToJsonConverter() { }
    
    @Override
    public Object apply(Object arg) {
        if (arg instanceof HtmlUnitWebElement) {
            return Map.of(Dialect.W3C.getEncodedElementKey(), String.valueOf(((HtmlUnitWebElement) arg).getId()));
        }
        return super.apply(arg);
    }
}
