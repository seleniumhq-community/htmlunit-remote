[![Maven Central](https://img.shields.io/maven-central/v/com.nordstrom.ui-tools/htmlunit-remote.svg)](https://central.sonatype.com/search?q=com.nordstrom.ui-tools+htmlunit-remote&core=gav)

# HtmlUnit Remote

**HtmlUnit Remote** is a driver service for [HtmlUnitDriver](https://github.com/SeleniumHQ/htmlunit-driver) that enables you to acquire driver sessions from [Selenium 4 Grid](https://www.selenium.dev/documentation/grid).

### Background

To eliminate behavioral differences between local and remote configurations, the [Selenium Foundation](https://github.com/sbabcoc/Selenium-Foundation) framework always acquires browser sessions from a **Grid** instance, managing its own local grid instance when not configured to use an existing instance. **Selenium 3 Grid** could be configured to supply **HtmlUnitDriver** sessions, supported by special-case handling within the Node server itself. This handling was not carried over into **Selenium 4 Grid**, which was completely re-engineered with new architecture and vastly expanded capabilities.

The lack of **HtmlUnitDriver** support in **Selenium 4 Grid** necessitated reconfiguring the **Selenium Foundation** project unit tests from using this Java-only managed artifact to using a standard browser like Chrome, an external dependency that requires additional resources and imposes additional risks of failure.

The driver service implemented by **HtmlUnit Remote** enables **Selenium 4 Grid** to supply **HtmlUnitDriver** sessions.

### Implementation Details

**HtmlUnit Remote** provides the following elements:
* **HtmlUnitDriverInfo** - This class informs **Selenium 4 Grid** that **HtmlUnitDriver** is available and provides a method to create new driver instances.
* **HtmlUnitDriverService** - This class manages a server that hosts instances of **HtmlUnitDriver**.
* **HtmlUnitDriverServer** - This is the server class that hosts **HtmlUnitDriver** instances. It implements the [W3C WebDriver protocol](https://www.w3.org/TR/webdriver2):
  * Create new driver sessions
  * Route driver commands to specified driver sessions
  * Package driver method results into HTTP responses

In operation, **HtmlUnitDriverService** is instantiated by **Selenium 4 Grid** node servers that are configured to support **HtmlUnitDriver**. Unlike other driver services, which launch a new process for each created driver session, **HtmlUnitDriverService** starts a single in-process server that hosts all of the driver sessions it creates.

### Grid Configuration

```
[node]
detect-drivers = false
[[node.driver-configuration]]
display-name = "HtmlUnit"
stereotype = "{\"browserName\": \"htmlunit\"}"

[distributor]
slot-matcher = "org.openqa.selenium.htmlunit.remote.HtmlUnitSlotMatcher"
```

The `selenium-server` JAR doesn't include the **HtmlUnitDriver** artifacts; these need to be specified as extensions to the grid class path via the `--ext` option:

```
java -jar selenium-server-<version>.jar --ext htmlunit-remote-<version>-grid-extension.jar standalone --config htmlunit.toml
```
The `grid-extension` artifact provides all of the specifications and service providers required to enable **Selenium 4 Grid** to supply remote sessions of **HtmlUnitDriver**. This artifact combines `htmlunit-remote` with `htmlunit3-driver`, `htmlunit`, and all of their unique dependencies.

### Specification of [browserVersion]

In **HtmlUnit Driver**, you can use the standard `browserVersion` capability to select a specific browser emulation profile. This works as expected in local operation but will cause driver acquisition to fail in remote configurations. The reason for this is that `browserVersion` is an _identity_ value that the Selenium Grid distributor expects to match against the stereotype of a compatible node. In **HtmlUnit Driver**, `browserVersion` is a _configuration_ value, so this capability should not be considered for slot matching.

**HtmlUnit Remote** adds a vendor-specific capability for selecting a specific browser emulation profile: `garg:browserVersion`.  If you use the standard **HtmlUnitDriverOptions** class to build your driver specification, this issue is handled for you automatically. If you specify driver capabilities directly, be sure to use this new vendor-specific capability name.

```
{"browserName": "htmlunit", "garg:browserVersion": "firefox-115"}
```

> Written with [StackEdit](https://stackedit.io/).
