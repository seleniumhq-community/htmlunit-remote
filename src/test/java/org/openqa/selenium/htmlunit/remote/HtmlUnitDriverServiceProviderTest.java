package org.openqa.selenium.htmlunit.remote;

import static org.junit.Assert.assertTrue;

import java.util.ServiceLoader;

import org.junit.Test;
import org.openqa.selenium.remote.WebDriverInfo;
import org.openqa.selenium.remote.service.DriverService.Builder;

/**
 * The intent of this test class is to detect instances where the fully qualified names of
 * service provider interface definitions change (move from one package to another). Such
 * migrations will cause compilation failures in the implementation classes, which require
 * updates to the 'import' declaration in implementing classes, but the corresponding SPI
 * declaration files will retain the former names of the relocated interfaces.
 * 
 *  For example, relocation of the <b>WebDriverInfo</b> interface:
 *  
 * <blockquote>
 *   {@code org.openqa.selenium.WebDriverInfo} &rarr; {@code org.openqa.selenium.remote.WebDriverInfo}
 * </blockquote>
 * 
 * ... caused compilation failures in classes that refer to this interface. However, the SPI
 * declaration file:
 * 
 * <blockquote>
 *   {@code META-INF/services/org.openqa.selenium.WebDriverInfo}
 * </blockquote>
 * 
 * ... continued to be included in the resources of the <b>HtmlUnit Remote</b> JAR without
 * any complaint, even though the resulting library was incompatible with the every release
 * of <b>Selenium Grid</b> since version {@code 4.40.0}
 */
public class HtmlUnitDriverServiceProviderTest {

    @Test
    public void testLoadingHtmlUnitDriverInfo() {
        ServiceLoader<WebDriverInfo> loader = ServiceLoader.load(WebDriverInfo.class);
        boolean found = loader.stream()
                .anyMatch(provider -> provider.type().equals(HtmlUnitDriverInfo.class));
        assertTrue("HtmlUnitDriverInfo was not found by ServiceLoader", found);
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testLoadingHtmlUnitDriverService_Builder() {
        ServiceLoader<Builder> loader = ServiceLoader.load(Builder.class);
        boolean found = loader.stream()
                .anyMatch(provider -> provider.type().equals(HtmlUnitDriverService.Builder.class));
        assertTrue("HtmlUnitDriverService.Builder was not found by ServiceLoader", found);
    }
    
}
