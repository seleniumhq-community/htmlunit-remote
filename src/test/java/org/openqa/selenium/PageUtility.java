package org.openqa.selenium;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;

public class PageUtility {
    
    private PageUtility() {
        
    }
    
    /**
     * Get the content of the name resource
     * 
     * @param resource resource filename
     * @return resource file content
     */
    public static String getResource(final String resource) {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        try {
            try (InputStream is = classLoader.getResourceAsStream(resource)) {
                return (is != null) ? new String(is.readAllBytes(), UTF_8) : null;
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to load '" + resource + "'", e);
        }
    }
}
