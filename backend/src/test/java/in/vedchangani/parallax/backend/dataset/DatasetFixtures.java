package in.vedchangani.parallax.backend.dataset;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test-only CSV and naming fixtures shared by the D-32 dataset test suite
 * (mirroring {@code strategy.StrategyFixtures}/{@code TestUsers}). Public,
 * like {@code TestUsers}, so it can also be used from {@code api}-package
 * integration tests.
 */
public final class DatasetFixtures {

    private static final AtomicLong COUNTER = new AtomicLong();

    private DatasetFixtures() {
    }

    public static byte[] simpleCsv() {
        return ("date,open,high,low,close,volume\n"
                + "2024-01-02,100,105,99,104,1000\n"
                + "2024-01-03,104,110,103,108,2000\n").getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] alternativeCsv() {
        return ("date,open,high,low,close,volume\n"
                + "2024-02-01,50,55,48,54,500\n"
                + "2024-02-02,54,60,53,58,600\n"
                + "2024-02-03,58,65,57,63,700\n").getBytes(StandardCharsets.US_ASCII);
    }

    public static String uniqueName() {
        return "ds-" + System.nanoTime() + "-" + COUNTER.incrementAndGet();
    }
}
