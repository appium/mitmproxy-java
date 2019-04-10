import java.util.function.Function;

public class MitmproxyJava {



    public MitmproxyJava(Function<InterceptedMessage, Boolean> messageInterceptor) {
        InterceptedMessage message = new InterceptedMessage();
        message.message = "hi from message";
        messageInterceptor.apply(message);

        System.out.println("you started me");
    }
}
