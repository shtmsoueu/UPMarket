package net.mineup.mercado.expiration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.ToLongFunction;

/**
 * Regras puras de expiração, sem dependência do Bukkit, para poderem ser testadas isoladamente.
 */
public final class ExpirationPolicy
{
    private ExpirationPolicy() {
    }
    
    /** Converte horas da config em milissegundos sem estouro de {@code int}; valores negativos viram 0. */
    public static long hoursToMillis(long hours) {
        return TimeUnit.HOURS.toMillis(Math.max(0L, hours));
    }
    
    /** Um item vence quando {@code since + ttl <= now} (mesma regra usada antes no login do dono). */
    public static boolean isDue(long since, long ttlMillis, long now) {
        return now - since >= ttlMillis;
    }
    
    /** Devolve as chaves cujos valores já venceram, na ordem de iteração do mapa. */
    public static <T> List<String> dueKeys(Map<String, T> entries, ToLongFunction<T> since, long ttlMillis, long now) {
        List<String> due = new ArrayList<String>();
        for (Map.Entry<String, T> entry : entries.entrySet()) {
            if (isDue(since.applyAsLong(entry.getValue()), ttlMillis, now)) {
                due.add(entry.getKey());
            }
        }
        return due;
    }
}
