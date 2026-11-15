package rhino;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static rhino.Token.FIRST_TOKEN;
import static rhino.Token.LAST_TOKEN;

import org.junit.jupiter.api.Test;

public class TokenTest {
    @Test
    public void allTokensHaveAName() {
        for (int token = FIRST_TOKEN; token < LAST_TOKEN; ++token) {
            assertNotNull(Token.typeToName(token));
        }
    }
}
