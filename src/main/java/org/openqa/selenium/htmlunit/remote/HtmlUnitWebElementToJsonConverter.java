package org.openqa.selenium.htmlunit.remote;

import java.util.Map;

import org.openqa.selenium.htmlunit.HtmlUnitWebElement;
import org.openqa.selenium.remote.Dialect;
import org.openqa.selenium.remote.WebElementToJsonConverter;

public class HtmlUnitWebElementToJsonConverter extends WebElementToJsonConverter {
    @Override
    public Object apply(Object arg) {
        if (arg instanceof HtmlUnitWebElement) {
            return Map.of(Dialect.W3C.getEncodedElementKey(), String.valueOf(((HtmlUnitWebElement) arg).getId()));
        }
        return super.apply(arg);
    }
}
