package net.mineup.mercado.expiration;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Level;

import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import net.mineup.mercado.MarketItem;
import net.mineup.mercado.UPMarket;
import net.mineup.mercado.manager.ItensManager;

/**
 * Expira anúncios periodicamente, esteja o dono online ou não.
 *
 * <p>Modelo de concorrência: tudo roda no thread principal do servidor. Os anúncios e
 * expirados vivem em {@code HashMap}s acessados pelos listeners e comandos (thread
 * principal), e o banco usa uma única {@code Connection} compartilhada. Rodar aqui
 * elimina condições de corrida sem precisar de locks. A varredura em si não faz I/O:
 * compara os horários já carregados em memória. As escritas no banco são feitas em
 * lotes pequenos, um lote por tick, para não derrubar o TPS quando muitos anúncios
 * vencem de uma vez (por exemplo, logo após o servidor ficar desligado por horas).</p>
 */
public final class ExpirationTask
{
    /** Escritas no banco por tick. Cada anúncio expirado custa uma transação (INSERT + DELETE). */
    static final int MAX_OPERATIONS_PER_TICK = 20;
    private static final long TICKS_PER_SECOND = 20L;
    private static final long INITIAL_DELAY_TICKS = 10L * TICKS_PER_SECOND;
    
    private final UPMarket plugin;
    private final Deque<String> pendingListings = new ArrayDeque<String>();
    private final Deque<String> pendingPurges = new ArrayDeque<String>();
    private long listingTtlMillis;
    private long expiredTtlMillis;
    private BukkitTask sweepTask;
    private BukkitTask drainTask;
    
    public ExpirationTask(UPMarket plugin) {
        this.plugin = plugin;
    }
    
    /** Agenda a verificação periódica. Deve ser chamado no thread principal (onEnable). */
    public void start() {
        stop();
        this.listingTtlMillis = ExpirationPolicy.hoursToMillis(this.plugin.getConfig().getLong("Timers.Tempo_Loja", 12L));
        this.expiredTtlMillis = ExpirationPolicy.hoursToMillis(this.plugin.getConfig().getLong("Timers.Tempo_Expirado", 48L));
        long intervalMinutes = Math.max(1L, this.plugin.getConfig().getLong("Timers.Intervalo_Verificacao", 5L));
        long periodTicks = intervalMinutes * 60L * TICKS_PER_SECOND;
        this.sweepTask = new BukkitRunnable() {
            @Override
            public void run() {
                ExpirationTask.this.sweep();
            }
        }.runTaskTimer(this.plugin, INITIAL_DELAY_TICKS, periodTicks);
    }
    
    /** Cancela as tarefas. O que ficou pendente será reencontrado na próxima inicialização. */
    public void stop() {
        if (this.sweepTask != null) {
            this.sweepTask.cancel();
            this.sweepTask = null;
        }
        if (this.drainTask != null) {
            this.drainTask.cancel();
            this.drainTask = null;
        }
        this.pendingListings.clear();
        this.pendingPurges.clear();
    }
    
    private void sweep() {
        if (this.drainTask != null) {
            // Ainda processando a rodada anterior; a próxima varredura pega o que faltar.
            return;
        }
        ItensManager itens = this.plugin.getItensManager();
        long now = System.currentTimeMillis();
        this.pendingListings.addAll(ExpirationPolicy.dueKeys(itens.getItems(), MarketItem::getTime, this.listingTtlMillis, now));
        this.pendingPurges.addAll(ExpirationPolicy.dueKeys(itens.getExpireds(), MarketItem::getTime, this.expiredTtlMillis, now));
        if (this.pendingListings.isEmpty() && this.pendingPurges.isEmpty()) {
            return;
        }
        this.drainTask = new BukkitRunnable() {
            private int expired;
            private int purged;
            
            @Override
            public void run() {
                try {
                    int budget = MAX_OPERATIONS_PER_TICK;
                    while (budget > 0 && !ExpirationTask.this.pendingListings.isEmpty()) {
                        budget--;
                        if (ExpirationTask.this.expireListing(ExpirationTask.this.pendingListings.poll())) {
                            this.expired++;
                        }
                    }
                    while (budget > 0 && !ExpirationTask.this.pendingPurges.isEmpty()) {
                        budget--;
                        if (ExpirationTask.this.purgeExpired(ExpirationTask.this.pendingPurges.poll())) {
                            this.purged++;
                        }
                    }
                }
                catch (RuntimeException ex) {
                    ExpirationTask.this.plugin.getLogger().log(Level.WARNING, "Erro ao processar expiracoes; o restante fica para a proxima verificacao.", ex);
                    ExpirationTask.this.pendingListings.clear();
                    ExpirationTask.this.pendingPurges.clear();
                }
                if (ExpirationTask.this.pendingListings.isEmpty() && ExpirationTask.this.pendingPurges.isEmpty()) {
                    this.cancel();
                    ExpirationTask.this.drainTask = null;
                    if (this.expired > 0 || this.purged > 0) {
                        ExpirationTask.this.plugin.getLogger().info("Expiracao: " + this.expired + " anuncio(s) movido(s) para Expirados, " + this.purged + " expirado(s) removido(s).");
                    }
                }
            }
        }.runTaskTimer(this.plugin, 1L, 1L);
    }
    
    /** Revalida no momento da escrita: o item pode ter sido comprado ou recolhido desde a varredura. */
    private boolean expireListing(String uuid) {
        ItensManager itens = this.plugin.getItensManager();
        MarketItem listing = itens.getItem(uuid);
        if (listing == null || !ExpirationPolicy.isDue(listing.getTime(), this.listingTtlMillis, System.currentTimeMillis())) {
            return false;
        }
        return itens.expireListing(uuid);
    }
    
    private boolean purgeExpired(String uuid) {
        ItensManager itens = this.plugin.getItensManager();
        MarketItem expired = itens.getExpired(uuid);
        if (expired == null || !ExpirationPolicy.isDue(expired.getTime(), this.expiredTtlMillis, System.currentTimeMillis())) {
            return false;
        }
        itens.removeExpired(uuid);
        return true;
    }
}
