package net.mineup.mercado.expiration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class ExpirationPolicyTest
{
    private static final long HOUR = 60L * 60L * 1000L;
    
    @Test
    public void hoursToMillisDoesNotOverflowInt() {
        // 1000 h * 3.600.000 ms estoura int (o código antigo multiplicava em int).
        assertEquals(1000L * HOUR, ExpirationPolicy.hoursToMillis(1000L));
    }
    
    @Test
    public void negativeHoursAreTreatedAsZero() {
        assertEquals(0L, ExpirationPolicy.hoursToMillis(-5L));
    }
    
    @Test
    public void itemIsDueExactlyAtTtl() {
        long since = 1_000_000L;
        assertFalse(ExpirationPolicy.isDue(since, 12 * HOUR, since + 12 * HOUR - 1));
        assertTrue(ExpirationPolicy.isDue(since, 12 * HOUR, since + 12 * HOUR));
        assertTrue(ExpirationPolicy.isDue(since, 12 * HOUR, since + 13 * HOUR));
    }
    
    @Test
    public void dueKeysReturnsOnlyExpiredEntriesInOrder() {
        long now = 100 * HOUR;
        Map<String, Long> listedAt = new LinkedHashMap<String, Long>();
        listedAt.put("velho", now - 20 * HOUR);
        listedAt.put("novo", now - 1 * HOUR);
        listedAt.put("no-limite", now - 12 * HOUR);
        assertEquals(Arrays.asList("velho", "no-limite"), ExpirationPolicy.dueKeys(listedAt, Long::longValue, 12 * HOUR, now));
    }
    
    @Test
    public void dueKeysOnEmptyMapIsEmpty() {
        assertEquals(Collections.emptyList(), ExpirationPolicy.dueKeys(Collections.<String, Long>emptyMap(), Long::longValue, HOUR, 0L));
    }
}
