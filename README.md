Java rendition of [node-mitmproxy](https://github.com/jvilk/mitmproxy-node)

Make sure to read the prerequisites section below.

## Why?

`mitmproxy` is great for capturing network traffic, but has no easy interface for Java users.
The software testing community, specifically Appium mobile testers, want to be able to capture network requests made by devices during their Java tests.

This library will run `mitmproxy` in the background as a separate process, and allow you to pass in a lambda function which gets called on every network request captured by `mitmproxy`. This allows you to check the captured requests within you Java code without having to stop the proxy or end capture.

See @jonahss's Appium Pro posts on this topic:
- https://appiumpro.com/editions/62
- https://appiumpro.com/editions/63
- https://appiumpro.com/editions/65 <- coming soon

## What can I use this for?

For transparently rewriting HTTP/HTTPS responses. The mitmproxy plugin lets every HTTP request go through to the server uninhibited, and then passes it to Java via a WebSocket for rewriting.

## How does it work?

`mitmproxy-java` starts a Websocket server and a Python plugin for `mitmproxy` connects to it and sends requests over. The two communicate via binary messages to reduce marshaling-related overhead.

## Your Java code is bad and you should feel bad

I'm no Java expert! You may see some bad patterns, like my terrible disregard for exception handling. Let's make it better. File your issues and land some PRs! I wrote this in just a couple days for our weekly newsletter.

## Your Python plugin is bad and you should feel bad

See [node-mitmproxy](https://github.com/jvilk/mitmproxy-node/blob/master/README.md#your-python-plugin-is-bad-and-you-should-feel-bad). Pull requests welcome!

## Pre-requisites

* [`mitmproxy` V4](https://mitmproxy.org/) must be installed and runnable from the terminal. The install method cannot be a prebuilt binary or homebrew, since those packages are missing the Python websockets module. Install via `pip` or from source.
* Python 3.6, since we use the new async/await syntax in the mitmproxy plugin
* `pip3 install websockets`

Maven:
```

```

Gradle:
```
testCompile group: 'io.appium', name: 'mitmproxy-java', version: '1.5'
```

## Usage

Coming soon.
See blog post.
Examples will be in AppiumPro repo.
