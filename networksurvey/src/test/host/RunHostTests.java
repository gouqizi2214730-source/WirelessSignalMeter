import java.lang.reflect.InvocationTargetException;
import org.junit.Test;

public class RunHostTests {
    public static void main(String[] args) throws Exception {
        int count = 0;
        for (String name : args) {
            var type = Class.forName(name);
            var instance = type.getConstructor().newInstance();
            for (var method : type.getDeclaredMethods()) {
                var test = method.getAnnotation(Test.class);
                if (test == null) continue;
                Throwable error = null;
                try { method.invoke(instance); }
                catch (InvocationTargetException failure) { error = failure.getCause(); }
                if (test.expected() == Test.None.class ? error != null :
                        error == null || !test.expected().isInstance(error)) {
                    throw new AssertionError(type.getSimpleName() + "." + method.getName(), error);
                }
                System.out.println("PASS: " + method.getName());
                count++;
            }
        }
        if (count == 0) throw new AssertionError("No tests found");
        System.out.println("PASS: " + count + " tests");
    }
}
