package io.appium.mitmproxy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Drop-in replacement for the pattern shown in the project README:
 *
 * <pre>{@code
 * List<InterceptedMessage> messages = new ArrayList<>();
 * proxy = new MitmproxyJava(path, m -> { messages.add(m); return m; }, port, params);
 * }</pre>
 *
 * That pattern is not thread-safe: {@link MitmproxyServer} can invoke the
 * interceptor concurrently from multiple connection threads whenever more
 * than one HTTP transaction is in flight at once, and a plain {@code
 * ArrayList} does not support concurrent modification safely (you'll see
 * {@code ConcurrentModificationException}, lost entries, or worse).
 *
 * This class wraps a {@link CopyOnWriteArrayList}, which is safe for exactly
 * this "many concurrent writers, occasional reader" access pattern (frequent
 * small appends, infrequent full reads of everything collected so far).
 *
 * Usage:
 * <pre>{@code
 * ThreadSafeMessageCollector collector = new ThreadSafeMessageCollector();
 * proxy = new MitmproxyJava(path, collector, port, params);
 * proxy.start();
 * // ... exercise the app under test ...
 * proxy.stop();
 * List<InterceptedMessage> messages = collector.getMessages(); // safe snapshot
 * }</pre>
 */
public class ThreadSafeMessageCollector implements Function<InterceptedMessage, InterceptedMessage> {

    private final List<InterceptedMessage> messages = new CopyOnWriteArrayList<>();

    @Override
    public InterceptedMessage apply(InterceptedMessage interceptedMessage) {
        messages.add(interceptedMessage);
        // Returning the message unmodified passes the request straight through.
        // If you need to rewrite requests/responses, do it here and return the
        // modified message instead — just make sure any additional state you
        // touch is also thread-safe.
        return interceptedMessage;
    }

    /**
     * @return a live, thread-safe view of everything collected so far. Safe to
     * iterate even while the proxy is still running and appending to it
     * concurrently (iteration snapshots the list at the time it starts).
     */
    public List<InterceptedMessage> getMessages() {
        return messages;
    }
}