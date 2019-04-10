import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class MitmproxyJavaTest {

    @Test
    public void ConstructorTest() {
        MitmproxyJava proxy = new MitmproxyJava((InterceptedMessage m) -> {
            System.out.println(m.message);
            return new Boolean(true);
        });
    }
}