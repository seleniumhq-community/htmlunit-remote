package org.openqa.selenium.htmlunit.remote;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.Date;

import org.openqa.selenium.Cookie;
import org.openqa.selenium.json.JsonInput;

public class CookieCoercer {

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
    
    private static class Builder extends Cookie.Builder {
        
        private String name;
        private String value;

        public Builder() {
            super(null, null);
        }
        
        public void name(final String name) {
            this.name = name;
        }
        
        public void value(final String value) {
            this.value = value;
        }
        
        @Override
        public Cookie build() {
            Cookie cookie = super.build();
            return new Cookie(name, value, cookie.getDomain(), cookie.getPath(), cookie.getExpiry(),
                    cookie.isSecure(), cookie.isHttpOnly(), cookie.getSameSite());
          }
    }

}
