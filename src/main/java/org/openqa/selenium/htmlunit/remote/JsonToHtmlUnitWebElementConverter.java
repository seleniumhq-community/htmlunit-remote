package org.openqa.selenium.htmlunit.remote;

import java.lang.reflect.Field;
import java.util.Map;

import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.interactions.Interaction;
import org.openqa.selenium.remote.Dialect;
import org.openqa.selenium.remote.JsonToWebElementConverter;
import org.openqa.selenium.remote.RemoteWebDriver;

public class JsonToHtmlUnitWebElementConverter extends JsonToWebElementConverter {

    private HtmlUnitDriver driver;

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
        }
        
        return super.apply(result);
    }
}
