package net.mineup.mercado.utils.geral;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.inventory.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;

public class Util {

	public static String getColorPrefix(String prefix) {
		Player player = Bukkit.getPlayer(prefix);
		User user = LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId());
		prefix = user.getCachedData().getMetaData(LuckPermsProvider.get().getContextManager().getQueryOptions(player)).getPrefix();
		return prefix.isEmpty() ? "§7" : prefix.replace("&", "§");
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

	/**
	 * Verifica se o inventário tem espaço para receber a pilha inteira.
	 * Soma o espaço livre em slots vazios e em pilhas do mesmo item (isSimilar),
	 * respeitando o limite de pilha do item e do inventário.
	 */
	public static boolean invCanHoldItem(Inventory inv, ItemStack is) {
		if (inv == null || is == null || is.getType() == Material.AIR) {
			return false;
		}
		int maxStack = Math.min(is.getMaxStackSize(), inv.getMaxStackSize());
		if (maxStack <= 0) {
			maxStack = 1;
		}
		int remaining = is.getAmount();
		for (ItemStack slot : inv.getContents()) {
			if (slot == null || slot.getType() == Material.AIR) {
				remaining -= maxStack;
			} else if (slot.isSimilar(is)) {
				remaining -= Math.max(0, maxStack - slot.getAmount());
			}
			if (remaining <= 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Entrega uma cópia do item ao jogador. O que não couber no inventário é dropado
	 * aos pés dele em vez de sumir (addItem devolve o excedente).
	 */
	public static void giveItem(Player player, ItemStack is) {
		Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack[] { is.clone() });
		for (ItemStack rest : leftover.values()) {
			player.getWorld().dropItemNaturally(player.getLocation(), rest);
		}
		player.updateInventory();
	}

	public static long calculate(long delay) {
		return System.currentTimeMillis() - delay;
	}

	public static boolean materialExists(String m) {
		return Material.getMaterial(m.toUpperCase()) != null;
	}
}
