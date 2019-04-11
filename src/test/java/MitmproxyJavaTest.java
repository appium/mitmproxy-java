import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class MitmproxyJavaTest {

    @Test
    public void ConstructorTest() throws URISyntaxException, IOException, InterruptedException {
        MitmproxyJava proxy = new MitmproxyJava((InterceptedMessage m) -> {
            System.out.println(m.requestURL.toString());
            return m;
        });
        System.out.println("advanced in test");
        proxy.stop();
    }

    //todo test not modifying (by not returning)
    
    //todo test modifying a response
}