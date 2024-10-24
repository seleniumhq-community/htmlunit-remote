package org.openqa.selenium.htmlunit.remote;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.Date;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.json.JsonInput;

/**
 * This class performs the coercion from <b>JSON</b> to the encoded {@link Cookie} object.
 */
public class CookieCoercer {

    /**
     * Decode the specified input into the corresponding cookie.
     * 
     * @param input encoded {@link JsonInput} object
     * @return decoded {@link Cookie} object
     */
    public static Cookie fromJson(JsonInput input) {
        Builder builder = new Builder();

        input.beginObject();
        while (input.hasNext()) {
            switch (input.nextName()) {
            case "name":
                builder.name(input.read(String.class));
                break;
            case "value":
                builder.value(input.read(String.class));
                break;
            case "domain":
                builder.domain(input.read(String.class));
                break;
            case "path":
                builder.path(input.read(String.class));
                break;
            case "secure":
                builder.isSecure(input.read(Boolean.class));
                break;
            case "httpOnly":
                builder.isHttpOnly(input.read(Boolean.class));
                break;
            case "sameSite":
                builder.sameSite(input.read(String.class));
                break;
            case "expiry":
                builder.expiresOn(new Date(SECONDS.toMillis(input.read(Long.class))));
                break;
            default:
                input.skipValue();
            }
        }

        input.endObject();
        return builder.build();
    }
    
    /**
     * This is a builder class used by the {@link CookieCoercer} class to decode encoded {@link Cookie} objects.
     */
    private static class Builder extends Cookie.Builder {
        
        private String name;
        private String value;

        public Builder() {
            super(null, null);
        }
        
        /**
         * Set the name for this cookie.
         * 
         * @param name cookie name
         */
        public void name(final String name) {
            this.name = name;
        }
        
        /**
         * Set the value for this cookie.
         * 
         * @param value cookie value
         */
        public void value(final String value) {
            this.value = value;
        }
        
        /**
         * Create the cookie specified by the properties defined in this builder.
         */
        @Override
        public Cookie build() {
            Cookie cookie = super.build();
            return new Cookie(name, value, cookie.getDomain(), cookie.getPath(), cookie.getExpiry(),
                    cookie.isSecure(), cookie.isHttpOnly(), cookie.getSameSite());
        }
    }
}
