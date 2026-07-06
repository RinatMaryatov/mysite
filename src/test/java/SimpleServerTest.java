import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleServerTest {

    @Test
    void htmlPageShouldContainCounterValue() {

        String html = SimpleServer.htmlPage(5);

        assertTrue(html.contains("Счётчик:"));
        assertTrue(html.contains(">5<"));
    }
}