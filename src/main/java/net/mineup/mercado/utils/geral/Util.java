package net.mineup.mercado.utils.geral;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.model.user.User;
import org.bukkit.inventory.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;

public class Util {

	private static final String PREFIXO_PADRAO = "§7";

	/**
	 * Retorna o prefixo colorido do jogador no LuckPerms.
	 * Funciona com o vendedor offline (usa o cache do LuckPerms, se houver) e
	 * devolve a cor padrão quando não há prefixo, usuário carregado ou LuckPerms.
	 */
	public static String getColorPrefix(String nome) {
		if (nome == null) {
			return PREFIXO_PADRAO;
		}
		try {
			LuckPerms lp = LuckPermsProvider.get();
			Player player = Bukkit.getPlayerExact(nome);
			User user;
			QueryOptions opcoes;
			if (player != null) {
				user = lp.getUserManager().getUser(player.getUniqueId());
				opcoes = lp.getContextManager().getQueryOptions(player);
			} else {
				// Vendedor offline: só usa o que já está em memória, sem consulta bloqueante.
				user = lp.getUserManager().getUser(nome);
				opcoes = lp.getContextManager().getStaticQueryOptions();
			}
			if (user == null) {
				return PREFIXO_PADRAO;
			}
			String prefixo = user.getCachedData().getMetaData(opcoes).getPrefix();
			return prefixo == null || prefixo.isEmpty() ? PREFIXO_PADRAO : prefixo.replace("&", "§");
		} catch (IllegalStateException | NoClassDefFoundError e) {
			// LuckPerms ausente ou ainda não carregado.
			return PREFIXO_PADRAO;
		}
	}

	public static String onlyNumbers(String str) {
		if (str != null) {
			return str.replaceAll("[^0123456789]", "");
		}
		return "";
	}

	public static String onlyWords(String input) {
		if (input != null) {
			return input.replaceAll("\\d+", "");
		}
		return "";
	}

	public static boolean isNum(String s) {
		try {
			Integer.parseInt(s);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	public static boolean invCanHoldItem(Inventory inv, ItemStack is) {
		Iterator<?> iterator = inv.iterator();
		if (iterator.hasNext()) {
			ItemStack itemStack = (ItemStack) iterator.next();
			return itemStack == null || itemStack.getType() != is.getType()
					|| itemStack.getAmount() > is.getMaxStackSize() - is.getAmount() || true;
		}
		return false;
	}

	public static long calculate(long delay) {
		return System.currentTimeMillis() - delay;
	}

	public static boolean materialExists(String m) {
		return Material.getMaterial(m.toUpperCase()) != null;
	}
}
