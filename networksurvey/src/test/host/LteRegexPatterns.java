import com.craxiom.networksurvey.util.LteSinrUtils;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

public final class LteRegexPatterns
{
    public static void main(String[] args) throws Exception
    {
        for (Field field : LteSinrUtils.class.getDeclaredFields())
        {
            if (field.getType() != Pattern.class) continue;
            field.setAccessible(true);
            Pattern pattern = (Pattern) field.get(null);
            System.out.println(field.getName() + "\t" + pattern.flags() + "\t"
                    + Base64.getEncoder().encodeToString(pattern.pattern().getBytes(StandardCharsets.UTF_8)));
        }
    }
}
