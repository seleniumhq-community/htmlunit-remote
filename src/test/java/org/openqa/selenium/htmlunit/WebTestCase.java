// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.selenium.htmlunit;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.htmlunit.BrowserVersion;
import org.htmlunit.MockWebConnection;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;

/**
 * Common superclass for HtmlUnit tests.
 *
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author David D. Kilzer
 * @author Marc Guillemot
 * @author Chris Erskine
 * @author Michael Ottati
 * @author Daniel Gredler
 * @author Ahmed Ashour
 * @author Ronald Brill
 */
public abstract class WebTestCase {

    /** Logging support. */
    private static final Log LOG = LogFactory.getLog(WebTestCase.class);

    /** save the environment */
    private static final Locale SAVE_LOCALE = Locale.getDefault();

    private MockWebConnection mockWebConnection_;

    /** To be documented. */
    protected static final ThreadLocal<BrowserVersion> generateTest_browserVersion_ = new ThreadLocal<>();
    
    private String generateTest_content_;
    private List<String> generateTest_expectedAlerts_;
    private boolean generateTest_notYetImplemented_;
    private String generateTest_testName_;

    /**
     * Constructor.
     */
    protected WebTestCase() {
        generateTest_browserVersion_.remove();
    }

    /**
     * Assert that the specified object is null.
     *
     * @param object the object to check
     */
    public static void assertNull(final Object object) {
        Assert.assertNull("Expected null but found [" + object + "]", object);
    }

    /**
     * Assert that the specified object is null.
     *
     * @param message the message
     * @param object  the object to check
     */
    public static void assertNull(final String message, final Object object) {
        Assert.assertNull(message, object);
    }

    /**
     * Assert that the specified object is not null.
     *
     * @param object the object to check
     */
    public static void assertNotNull(final Object object) {
        Assert.assertNotNull(object);
    }

    /**
     * Assert that the specified object is not null.
     *
     * @param message the message
     * @param object  the object to check
     */
    public static void assertNotNull(final String message, final Object object) {
        Assert.assertNotNull(message, object);
    }

    /**
     * Asserts that two objects refer to the same object.
     *
     * @param expected the expected object
     * @param actual   the actual object
     */
    public static void assertSame(final Object expected, final Object actual) {
        Assert.assertSame(expected, actual);
    }

    /**
     * Asserts that two objects refer to the same object.
     *
     * @param message  the message
     * @param expected the expected object
     * @param actual   the actual object
     */
    public static void assertSame(final String message, final Object expected, final Object actual) {
        Assert.assertSame(message, expected, actual);
    }

    /**
     * Asserts that two objects do not refer to the same object.
     *
     * @param expected the expected object
     * @param actual   the actual object
     */
    public static void assertNotSame(final Object expected, final Object actual) {
        Assert.assertNotSame(expected, actual);
    }

    /**
     * Asserts that two objects do not refer to the same object.
     *
     * @param message  the message
     * @param expected the expected object
     * @param actual   the actual object
     */
    public static void assertNotSame(final String message, final Object expected, final Object actual) {
        Assert.assertNotSame(message, expected, actual);
    }

    /**
     * Facility to test external form of urls. Comparing external form of URLs is
     * really faster than URL.equals() as the host doesn't need to be resolved.
     *
     * @param expectedUrl the expected URL
     * @param actualUrl   the URL to test
     */
    protected static void assertEquals(final URL expectedUrl, final URL actualUrl) {
        Assert.assertEquals(expectedUrl.toExternalForm(), actualUrl.toExternalForm());
    }

    /**
     * Asserts the two objects are equal.
     *
     * @param expected the expected object
     * @param actual   the object to test
     */
    protected static void assertEquals(final Object expected, final Object actual) {
        Assert.assertEquals(expected, actual);
    }

    /**
     * Asserts the two objects are equal.
     *
     * @param message  the message
     * @param expected the expected object
     * @param actual   the object to test
     */
    protected static void assertEquals(final String message, final Object expected, final Object actual) {
        Assert.assertEquals(message, expected, actual);
    }

    /**
     * Asserts the two ints are equal.
     *
     * @param expected the expected int
     * @param actual   the int to test
     */
    protected static void assertEquals(final int expected, final int actual) {
        Assert.assertEquals(expected, actual);
    }

    /**
     * Asserts the two boolean are equal.
     *
     * @param expected the expected boolean
     * @param actual   the boolean to test
     */
    protected void assertEquals(final boolean expected, final boolean actual) {
        Assert.assertEquals(Boolean.valueOf(expected), Boolean.valueOf(actual));
    }

    /**
     * Facility to test external form of urls. Comparing external form of URLs is
     * really faster than URL.equals() as the host doesn't need to be resolved.
     *
     * @param message     the message to display if assertion fails
     * @param expectedUrl the string representation of the expected URL
     * @param actualUrl   the URL to test
     */
    protected void assertEquals(final String message, final URL expectedUrl, final URL actualUrl) {
        Assert.assertEquals(message, expectedUrl.toExternalForm(), actualUrl.toExternalForm());
    }

    /**
     * Facility to test external form of a URL.
     *
     * @param expectedUrl the string representation of the expected URL
     * @param actualUrl   the URL to test
     */
    protected void assertEquals(final String expectedUrl, final URL actualUrl) {
        Assert.assertEquals(expectedUrl, actualUrl.toExternalForm());
    }

    /**
     * Facility method to avoid having to create explicitly a list from a String[]
     * (for example when testing received alerts). Transforms the String[] to a List
     * before calling
     * {@link org.junit.Assert#assertEquals(java.lang.Object, java.lang.Object)}.
     *
     * @param expected the expected strings
     * @param actual   the collection of strings to test
     */
    protected void assertEquals(final String[] expected, final List<String> actual) {
        assertEquals(null, expected, actual);
    }

    /**
     * Facility method to avoid having to create explicitly a list from a String[]
     * (for example when testing received alerts). Transforms the String[] to a List
     * before calling
     * {@link org.junit.Assert#assertEquals(java.lang.String, java.lang.Object, java.lang.Object)}.
     *
     * @param message  the message to display if assertion fails
     * @param expected the expected strings
     * @param actual   the collection of strings to test
     */
    protected void assertEquals(final String message, final String[] expected, final List<String> actual) {
        Assert.assertEquals(message, Arrays.asList(expected).toString(), actual.toString());
    }

    /**
     * Facility to test external form of a URL.
     *
     * @param message     the message to display if assertion fails
     * @param expectedUrl the string representation of the expected URL
     * @param actualUrl   the URL to test
     */
    protected void assertEquals(final String message, final String expectedUrl, final URL actualUrl) {
        Assert.assertEquals(message, expectedUrl, actualUrl.toExternalForm());
    }

    /**
     * Assert the specified condition is true.
     *
     * @param condition condition to test
     */
    protected void assertTrue(final boolean condition) {
        Assert.assertTrue(condition);
    }

    /**
     * Assert the specified condition is true.
     *
     * @param message   message to show
     * @param condition condition to test
     */
    protected void assertTrue(final String message, final boolean condition) {
        Assert.assertTrue(message, condition);
    }

    /**
     * Assert the specified condition is false.
     *
     * @param condition condition to test
     */
    protected void assertFalse(final boolean condition) {
        Assert.assertFalse(condition);
    }

    /**
     * Assert the specified condition is false.
     *
     * @param message   message to show
     * @param condition condition to test
     */
    protected void assertFalse(final String message, final boolean condition) {
        Assert.assertFalse(message, condition);
    }

    /**
     * A generics-friendly version of
     * {@link SerializationUtils#clone(Serializable)}.
     *
     * @param <T>    the type of the object being cloned
     * @param object the object being cloned
     * @return a clone of the specified object
     */
    protected <T extends Serializable> T clone(final T object) {
        return SerializationUtils.clone(object);
    }

    /**
     * Prepare the environment. Rhino has localized error message... for instance
     * for French
     */
    @BeforeClass
    public static void beforeClass() {
        Locale.setDefault(Locale.US);
    }

    /**
     * Restore the environment.
     */
    @AfterClass
    public static void afterClass() {
        Locale.setDefault(SAVE_LOCALE);
    }

    /**
     * Generates an HTML file that can be loaded and understood as a test.
     *
     * @throws IOException in case of problem
     */
    @After
    public void generateTestForWebDriver() throws IOException {
        if (generateTest_content_ != null && !generateTest_notYetImplemented_) {
            final File targetDir = new File("target/generated_tests");
            targetDir.mkdirs();

            final File outFile = new File(targetDir, generateTest_testName_);

            FileUtils.writeStringToFile(outFile, generateTest_content_, ISO_8859_1);

            // write the expected alerts
            final String suffix;
            BrowserVersion browser = generateTest_browserVersion_.get();
            if (browser == null) {
                browser = BrowserVersion.BEST_SUPPORTED;
            }
            suffix = "." + browser.getNickname() + ".expected";

            final File expectedLog = new File(outFile.getParentFile(), outFile.getName() + suffix);

            try (FileOutputStream fos = new FileOutputStream(expectedLog)) {
                try (ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                    oos.writeObject(generateTest_expectedAlerts_);
                }
            }
        }
    }

    /**
     * To be documented.
     *
     * @param status the status
     */
    protected void setGenerateTest_notYetImplemented(final boolean status) {
        generateTest_notYetImplemented_ = status;
    }

    /**
     * Returns the mock WebConnection instance for the current test.
     *
     * @return the mock WebConnection instance for the current test
     */
    protected MockWebConnection getMockWebConnection() {
        if (mockWebConnection_ == null) {
            mockWebConnection_ = new MockWebConnection();
        }
        return mockWebConnection_;
    }

    /**
     * Sets the mock WebConnection instance for the current test.
     *
     * @param connection the connection to use
     */
    protected void setMockWebConnection(final MockWebConnection connection) {
        mockWebConnection_ = connection;
    }

    /**
     * Cleanup after a test.
     */
    @After
    public void releaseResources() {
        mockWebConnection_ = null;
    }

    /**
     * Read the content of the given file using our classloader.
     *
     * @param fileName the file name
     * @return the content as string
     * @throws IOException in case of error
     */
    protected String getFileContent(final String fileName) throws IOException {
        return getFileContent(getClass().getClassLoader(), fileName);
    }
    
    protected static String _getFileContent(final String fileName) {
        try {
            return getFileContent(Thread.currentThread().getContextClassLoader(), fileName);
        } catch (IOException e) {
            return "";
        }
    }
    
    protected static String getFileContent(final ClassLoader classLoader, final String fileName) throws IOException {
        final InputStream stream = classLoader.getResourceAsStream(fileName);
        assertNotNull(fileName, stream);
        return IOUtils.toString(stream, ISO_8859_1);
    }
}
