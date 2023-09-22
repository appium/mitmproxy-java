Java rendition of [node-mitmproxy](https://github.com/jvilk/mitmproxy-node)

Make sure to read the prerequisites section below.

Detailed information included in the [AppiumPro article which introduces this library](https://appiumpro.com/editions/65).

## Why?

`mitmproxy` is great for capturing network traffic, but has no easy interface for Java users.
The software testing community, specifically Appium mobile testers, want to be able to capture network requests made by devices during their Java tests.

This library will run `mitmproxy` in the background as a separate process, and allow you to pass in a lambda function which gets called on every network request captured by `mitmproxy`. This allows you to check the captured requests within you Java code without having to stop the proxy or end capture.

See @jonahss's Appium Pro posts on this topic:
- https://appiumpro.com/editions/62
- https://appiumpro.com/editions/63
- https://appiumpro.com/editions/65

## What can I use this for?

For transparently rewriting HTTP/HTTPS responses. The mitmproxy plugin lets every HTTP request go through to the server uninhibited, and then passes it to Java via a WebSocket for rewriting.

## How does it work?

`mitmproxy-java` starts a Websocket server and a Python plugin for `mitmproxy` connects to it and sends requests over. The two communicate via binary messages to reduce marshaling-related overhead.

## Pre-requisites

* [`mitmproxy` V9](https://mitmproxy.org/) must be installed and runnable from the terminal. The install method cannot be a prebuilt binary or homebrew, since those packages are missing the Python websockets module. Install via `pip` or from source.
* Python 3.6 and above, since we use the new async/await syntax in the mitmproxy plugin
* `pip3 install websockets`

Maven:
```
<dependency>
  <groupId>io.appium</groupId>
  <artifactId>mitmproxy-java</artifactId>
  <version>2.0.2</version>
</dependency>
```

Gradle:
```
testCompile group: 'io.appium', name: 'mitmproxy-java', version: '2.0.2'
```

## Usage

```java
List<InterceptedMessage> messages = new ArrayList<InterceptedMessage>();

//optional, default port is 8080
int mitmproxyPort = 8090;

//optional, you can pass null if no extra params
List<String> extraMitmproxyParams = Arrays.asList("param1", "value1", "param2", "value2");

// remember to set local OS proxy settings in the Network Preferences
proxy = new MitmproxyJava("/usr/local/bin/mitmdump", (InterceptedMessage m) -> {
    System.out.println("intercepted request for " + m.getRequest().getUrl());
    messages.add(m);
    return m;
}, mitmproxyPort, extraMitmproxyParams);

proxy.start();

// do stuff

proxy.stop();
```

If the above code doesn't work, and you are getting 
`Error=2, No such file or directory` please re-check your mitmdump path in new MitmproxyJava initialization.
You can get mitmdump path using below command:
```shell
whereis mitmdump
```

See AppiumPro article for more guidelines: https://appiumpro.com/editions/65
Example can be found here: https://github.com/cloudgrey-io/appiumpro/blob/master/java/src/test/java/Edition065_Capture_Network_Requests.java

## Your Java code is bad and you should feel bad

I'm no Java expert! You may see some bad patterns, like my terrible disregard for exception handling. Let's make it better. File your issues and land some PRs! I wrote this in just a couple days for our weekly newsletter.

## Your Python plugin is bad and you should feel bad

See [node-mitmproxy](https://github.com/jvilk/mitmproxy-node/blob/master/README.md#your-python-plugin-is-bad-and-you-should-feel-bad). Pull requests welcome!

## Develop

Upload to Central Repository with command `./gradlew uploadArchives`
Set username and password in `build.gradle` file