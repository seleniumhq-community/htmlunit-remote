package org.openqa.selenium.htmlunit.remote;

import java.lang.reflect.Field;
import java.util.Map;

import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.htmlunit.HtmlUnitWebElement;
import org.openqa.selenium.interactions.Interaction;
import org.openqa.selenium.remote.Dialect;
import org.openqa.selenium.remote.JsonToWebElementConverter;
import org.openqa.selenium.remote.RemoteWebDriver;

/**
 * Converts <b>JSON</b> representations of web elements as defined by the WebDriver wire protocol into
 * corresponding {@link HtmlUnitWebElement} objects. This class will recursively convert Lists and Maps
 * to catch nested references.
 *
 * @see <a href="https://github.com/SeleniumHQ/selenium/wiki/JsonWireProtocol#webelement-json-object">
 *     WebDriver JSON Wire Protocol</a>
 */
public class JsonToHtmlUnitWebElementConverter extends JsonToWebElementConverter {

    private HtmlUnitDriver driver;

    /**
     * Constructor for the <b>JSON</b>-to-{@link HtmlUnitWebElement} converter.
     * 
     * @param driver driver associated with encoded web elements
     */
    public JsonToHtmlUnitWebElementConverter(HtmlUnitDriver driver) {
        super((RemoteWebDriver) null);
        this.driver = driver;
    }

    @Override
    public Object apply(Object result) {
        if (result instanceof Map<?, ?>) {
            Map<?, ?> resultAsMap = (Map<?, ?>) result;
            String elementId = String.valueOf(resultAsMap.get(Dialect.W3C.getEncodedElementKey()));
            if (elementId != null) {
                return driver.toWebElement(elementId);
            }
        }
        
        if (result instanceof Interaction) {
            try {
                Field originField = result.getClass().getDeclaredField("origin");
                originField.setAccessible(true);
                Object originObj = originField.get(result);
                Field originObjectField = originObj.getClass().getDeclaredField("originObject");
                originObjectField.setAccessible(true);
                Object resolved = apply(originObjectField.get(originObj));
                originObjectField.set(originObj, resolved);
            } catch (Exception e) {
                // nothing to do here
            }
            return result;
        }
        
        return super.apply(result);
    }
}
