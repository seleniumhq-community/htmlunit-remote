package org.openqa.selenium.htmlunit.remote;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.openqa.selenium.By;
import org.openqa.selenium.InvalidArgumentException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.interactions.Action;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.interactions.Encodable;
import org.openqa.selenium.interactions.InputSource;
import org.openqa.selenium.interactions.Interaction;
import org.openqa.selenium.interactions.Interactive;
import org.openqa.selenium.interactions.KeyInput;
import org.openqa.selenium.interactions.Pause;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.PointerInput.Kind;
import org.openqa.selenium.interactions.PointerInput.Origin;
import org.openqa.selenium.interactions.PointerInput.PointerEventProperties;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.interactions.WheelInput;
import org.openqa.selenium.interactions.WheelInput.ScrollOrigin;
import org.openqa.selenium.internal.Require;
import org.openqa.selenium.json.Json;
import org.openqa.selenium.json.JsonInput;

/**
 * This class performs the coercion from <b>JSON</b> to the encoded {@link Actions} object.
 */
public class ActionsCoercer {
    
    /**
     * Default constructor
     */
    public ActionsCoercer() { }
    
    /**
     * Decode the specified input into the corresponding actions.
     * 
     * @param input encoded {@link JsonInput} object
     * @return decoded {@link Actions} object
     */
    @SuppressWarnings("unchecked")
    public static Actions fromJson(final JsonInput input) {
        // extract list of serialized sequences
        List<Map<String, Object>> sequences = input.read(Json.LIST_OF_MAPS_TYPE);
        
        List<InputSource> sources = new ArrayList<>();
        List<Iterator<Map<String, Object>>> iterators = new ArrayList<>();
        ActionsWrapper actionsWrapper = new ActionsWrapper(new WebDriverWrapper());
        
        // if list of sequences is empty, return now
        if (sequences.isEmpty()) return actionsWrapper;

        // build parallel lists of sources and action iterators
        for (Map<String, Object> sequence : sequences) {
            // get name of input source
            String id = sequence.get("id").toString();
            // resolve type of input source
            switch (sequence.get("type").toString()) {
            case "key":
                actionsWrapper.setActiveKeyboard(id);
                sources.add(actionsWrapper.getActiveKeyboard());
                break;
            case "pointer":
                // get pointer source parameters
                Map<String, Object> parameters = (Map<String, Object>) sequence.get("parameters");
                // resolve kind of pointer source
                Kind kind = Kind.valueOf(parameters.get("pointerType").toString().toUpperCase());
                actionsWrapper.setActivePointer(kind, id);
                sources.add(actionsWrapper.getActivePointer());
                break;
            case "wheel":
                actionsWrapper.setActiveWheel(id);
                sources.add(actionsWrapper.getActiveWheel());
                break;
            }
            
            // add actions iterator for this source
            iterators.add(((List<Map<String, Object>>) sequence.get("actions")).iterator());
        }

        Interaction[] interactionGroup = new Interaction[sources.size()];

        // while actions remain to process
        while (iterators.get(0).hasNext()) {
            // process next action from each source
            for (int i = 0; i < sources.size(); i++) {
                InputSource source = sources.get(i);
                Map<String, Object> action = iterators.get(i).next();
                switch (action.get("type").toString()) {
                case "pause":
                    interactionGroup[i] = pause(source, action);
                    break;
                case "keyDown":
                    interactionGroup[i] = keyDown(source, action);
                    break;
                case "keyUp":
                    interactionGroup[i] = keyUp(source, action);
                    break;
                case "pointerDown":
                    interactionGroup[i] = pointerDown(source, action);
                    break;
                case "pointerUp":
                    interactionGroup[i] = pointerUp(source, action);
                    break;
                case "pointerMove":
                    interactionGroup[i] = pointerMove(source, action);
                    break;
                case "scroll":
                    interactionGroup[i] = scroll(source, action);
                    break;
                default:
                    throw new InvalidArgumentException("Invalid action type: " + action.get("type"));
                }
            }
            // add current interaction group
            actionsWrapper.tick(interactionGroup);
        }
        
        return actionsWrapper;
    }
    
    /**
     * Decode an encoded {@code pause} action.
     * 
     * @param source input source device
     * @param action encoded {@link Pause} action 
     * @return decoded 'pause' {@link Interaction} object
     */
    public static Interaction pause(final InputSource source, final Map<String, Object> action) {
        Duration duration = Duration.ofMillis((Long) action.get("duration"));
        return new Pause(source, duration);
    }
    
    /**
     * Decode an encoded {@code keyDown} action.
     * 
     * @param source input source device
     * @param action encoded 'keyDown' <b>TypingInteraction</b> action 
     * @return decoded 'keyDown' {@link Interaction} object
     */
    public static Interaction keyDown(final InputSource source, final Map<String, Object> action) {
        String value = action.get("value").toString();
        return ((KeyInput) source).createKeyDown(value.codePointAt(0));
    }
    
    /**
     * Decode an encoded {@code keyUp} action.
     * 
     * @param source input source device
     * @param action encoded 'keyUp' <b>TypingInteraction</b> action 
     * @return decoded 'keyUp' {@link Interaction} object
     */
    public static Interaction keyUp(final InputSource source, final Map<String, Object> action) {
        String value = action.get("value").toString();
        return ((KeyInput) source).createKeyUp(value.codePointAt(0));
    }
    
    /**
     * Decode an encoded {@code pointerDown} action.
     * 
     * @param source input source device
     * @param action encoded 'pointerDown' <b>PointerPress</b> action 
     * @return decoded 'pointerDown' {@link Interaction} object
     */
    public static Interaction pointerDown(final InputSource source, final Map<String, Object> action) {
        int button = ((Number) action.get("button")).intValue();
        return ((PointerInput) source).createPointerDown(button, eventProperties(action));
    }

    /**
     * Decode an encoded {@code pointerUp} action.
     * 
     * @param source input source device
     * @param action encoded 'pointerUp' <b>PointerPress</b> action 
     * @return decoded 'pointerUp' {@link Interaction} object
     */
    public static Interaction pointerUp(final InputSource source, final Map<String, Object> action) {
        int button = ((Number) action.get("button")).intValue();
        return ((PointerInput) source).createPointerUp(button, eventProperties(action));
    }
    
    /**
     * Decode an encoded {@code pointerMove} action.
     * 
     * @param source input source device
     * @param action encoded <b>Move</b> action 
     * @return decoded 'pointerMove' {@link Interaction} object
     */
    public static Interaction pointerMove(final InputSource source, final Map<String, Object> action) {
        Duration duration = Duration.ofMillis((Long) action.get("duration")); 
        Origin origin = origin(action.get("origin"));
        int x = ((Number) action.get("x")).intValue();
        int y = ((Number) action.get("y")).intValue();
        return ((PointerInput) source).createPointerMove(duration, origin, x, y, eventProperties(action));
    }

    /**
     * Decode an encoded {@code scroll} action.
     * 
     * @param source input source device
     * @param action encoded <b>ScrollInteraction</b> action 
     * @return decoded 'scroll' {@link Interaction} object
     */
    public static Interaction scroll(final InputSource source, final Map<String, Object> action) {
        Duration duration = Duration.ofMillis((Long) action.get("duration")); 
        ScrollOrigin origin = scrollOrigin(action.get("origin"));
        int x = ((Number) action.get("x")).intValue();
        int y = ((Number) action.get("y")).intValue();
        return ((WheelInput) source).createScroll(x, y, 0, 0, duration, origin);
    }
    
    /**
     * Decode the specified raw properties into a pointer event properties object.
     * 
     * @param rawProperties raw properties map
     * @return decoded {@link PointerEventProperties} object
     */
    private static PointerEventProperties eventProperties(final Map<String, Object> rawProperties) {
        PointerEventProperties eventProperties = new PointerEventProperties();
        for (Entry<String, Object> rawProperty : rawProperties.entrySet()) {
            switch (rawProperty.getKey()) {
            case "width":
                eventProperties.setWidth(((Number) rawProperty.getValue()).floatValue());
                break;
            case "height":
                eventProperties.setHeight(((Number) rawProperty.getValue()).floatValue());
                break;
            case "pressure":
                eventProperties.setPressure(((Number) rawProperty.getValue()).floatValue());
                break;
            case "tangentialPressure":
                eventProperties.setTangentialPressure(((Number) rawProperty.getValue()).floatValue());
                break;
            case "tiltX":
                eventProperties.setTiltX(((Number) rawProperty.getValue()).intValue());
                break;
            case "tiltY":
                eventProperties.setTiltY(((Number) rawProperty.getValue()).intValue());
                break;
            case "twist":
                eventProperties.setTwist(((Number) rawProperty.getValue()).intValue());
                break;
            case "altitudeAngle":
                eventProperties.setAltitudeAngle(((Number) rawProperty.getValue()).floatValue());
                break;
            case "azimuthAngle":
                eventProperties.setAzimuthAngle(((Number) rawProperty.getValue()).floatValue());
                break;
            }
        }
        return eventProperties;
    }
    
    /**
     * Create a pointer origin object from the specified raw origin.
     * 
     * @param rawOrigin raw origin object
     * @return decoded {@link Origin} object
     */
    private static Origin origin(final Object rawOrigin) {
        try {
            Constructor<Origin> ctor = Origin.class.getDeclaredConstructor(Object.class);
            ctor.setAccessible(true);
            return ctor.newInstance(rawOrigin);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Failed deserializing [origin]", e);
        }
    }
    
    /**
     * Create a scroll origin object from the specified raw origin.
     * 
     * @param originObject raw origin object
     * @return decoded {@link ScrollOrigin} object
     */
    private static ScrollOrigin scrollOrigin(Object originObject) {
        try {
            Constructor<ScrollOrigin> ctor = ScrollOrigin.class.getDeclaredConstructor(Object.class, int.class, int.class);
            ctor.setAccessible(true);
            return ctor.newInstance(originObject, 0, 0);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Failed deserializing [scrollOrigin]", e);
        }
    }
    
    /**
     * This is a wrapper class for decoded {@link Actions} objects that facilitates element reference resolution.
     */
    static class ActionsWrapper extends Actions {
        private WebDriverWrapper driverWrapper;

        public ActionsWrapper(WebDriverWrapper driver) {
            super(driver);
            this.driverWrapper = driver;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Action build() {
            Require.nonNull("[ActionsWrapper] Action origins unresolved; call 'resolveOrigins' first", driverWrapper.driver);
            return super.build();
        }
        
        /**
         * Resolve origin element references of actions within this {@link Actions} object.
         *  
         * @param driver target {@link HtmlUnitDriver}
         * @return this {@link Actions} object
         */
        public Actions resolveOrigins(final HtmlUnitDriver driver) {
            this.driverWrapper.driver = Require.nonNull("Driver", driver);
            resolveInteractionOriginElements(driver);
            return this;
        }
        
        /**
         * Iterate over the actions of this {@link Actions} object, resolving origin element references.
         * 
         * @param driver target {@link HtmlUnitDriver}
         */
        private void resolveInteractionOriginElements(final HtmlUnitDriver driver) {
            final JsonToHtmlUnitWebElementConverter elementConverter = new JsonToHtmlUnitWebElementConverter(driver);
            getSequences().stream().map(this::actions).forEach(action -> elementConverter.apply(action));
        }
        
        /**
         * Obtain a reference to the list of actions within the specified sequence object.
         * 
         * @param sequence {@link Sequence} object
         * @return list of {@link Encodable} objects with the specified sequence
         */
        @SuppressWarnings("unchecked")
        private List<Encodable> actions(final Sequence sequence) {
            try {
                Field actions = Sequence.class.getDeclaredField("actions");
                actions.setAccessible(true);
                return (List<Encodable>) actions.get(sequence);
            } catch (Exception e) {
                throw new UnsupportedOperationException("Failed acquiring actions", e);
            }
        }
    }
    
    /**
     * This wrapper class provides placeholder objects for the drivers used by {@link ActionsCoercer} objects.
     */
    private static class WebDriverWrapper implements WebDriver, Interactive {
        private WebDriver driver;

        /**
         * {@inheritDoc}
         */
        @Override
        public void perform(Collection<Sequence> actions) {
            ((Interactive) driver).perform(actions);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void resetInputState() {
            ((Interactive) driver).resetInputState();
        }
        
        /**
         * {@inheritDoc}
         */
        @Override
        public void get(String url) {
            driver.get(url);
        }
        
        /**
         * {@inheritDoc}
         */
        @Override
        public String getCurrentUrl() {
            return driver.getCurrentUrl();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getTitle() {
            return driver.getTitle();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public List<WebElement> findElements(By by) {
            return driver.findElements(by);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public WebElement findElement(By by) {
            return driver.findElement(by);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getPageSource() {
            return driver.getPageSource();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() {
            driver.close();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void quit() {
            driver.quit();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Set<String> getWindowHandles() {
            return driver.getWindowHandles();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getWindowHandle() {
            return driver.getWindowHandle();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public TargetLocator switchTo() {
            return driver.switchTo();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Navigation navigate() {
            return driver.navigate();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Options manage() {
            return driver.manage();
        }
    }
}
