package com.donutbalance.mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class DonutBalanceClient implements ClientModInitializer {
	public static final String MOD_ID = "donutbalance";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// "Steve paid you $1,500" / "Steve paid you $2.5K" (prefixes like "» " or "[$] " are allowed)
	private static final Pattern RECEIVED = Pattern.compile(
			"^\\W*(?:\\[[^\\]]*\\]\\W*)?([\\w.]{1,17})\\s+paid\\s+you\\s+\\$\\s*([\\d,]+(?:\\.\\d+)?)([KMBTQ]?)",
			Pattern.CASE_INSENSITIVE);
	// "You paid Steve $1,500"
	private static final Pattern SENT = Pattern.compile(
			"^\\W*(?:\\[[^\\]]*\\]\\W*)?you\\s+paid\\s+([\\w.]{1,17})\\s+\\$\\s*([\\d,]+(?:\\.\\d+)?)([KMBTQ]?)",
			Pattern.CASE_INSENSITIVE);

	// small-caps letters a-z (servers often use this font), as unicode escapes
	private static final String SMALL_CAPS =
			"\u1D00\u0299\u1D04\u1D05\u1D07\uA730\u0262\u029C\u026A\u1D0A\u1D0B\u029F\u1D0D"
					+ "\u0274\u1D0F\u1D18\u01EB\u0280\uA731\u1D1B\u1D1C\u1D20\u1D21x\u028F\u1D22";

	private boolean debug = false;

	private final BalanceTracker tracker = new BalanceTracker();

	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register(this::registerCommands);
		ClientReceiveMessageEvents.GAME.register(this::onGameMessage);
		// Server-sent chat with no player sender (player chat is ignored so it can't be spoofed)
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) -> {
			if (sender == null) {
				handle(message.getString());
			}
		});
		LOGGER.info("Donut Balance Tracker loaded. Tracking payment messages. Use /moneycheck.");
	}

	private void onGameMessage(Text message, boolean overlay) {
		if (overlay) {
			return;
		}
		handle(message.getString());
	}

	private void handle(String raw) {
		String content = normalize(raw).trim();

		if (content.toLowerCase(Locale.ROOT).contains("paid")) {
			LOGGER.info("[donutbalance] saw message: {}", raw);
			if (debug) {
				say("[debug] raw: " + raw, Formatting.GRAY);
			}
		}

		Matcher m = RECEIVED.matcher(content);
		if (m.find()) {
			Double amount = parseAmount(m.group(2) + m.group(3));
			if (amount != null) {
				tracker.onPayment(amount);
				if (debug) {
					say("[debug] matched RECEIVED +" + formatExact(amount), Formatting.GREEN);
				}
			}
			return;
		}

		m = SENT.matcher(content);
		if (m.find()) {
			Double amount = parseAmount(m.group(2) + m.group(3));
			if (amount != null) {
				tracker.onPayment(-amount);
				if (debug) {
					say("[debug] matched SENT -" + formatExact(amount), Formatting.RED);
				}
			}
		}
	}

	private static String normalize(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (char c : s.toCharArray()) {
			int idx = SMALL_CAPS.indexOf(c);
			sb.append(idx >= 0 ? (char) ('a' + idx) : c);
		}
		return Normalizer.normalize(sb.toString(), Normalizer.Form.NFKC);
	}

	private static void say(String text, Formatting color) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player != null) {
			mc.player.sendMessage(Text.literal(text).formatted(color), false);
		}
	}

	private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher,
								  net.minecraft.command.CommandRegistryAccess registryAccess) {
		dispatcher.register(literal("moneycheck")
				.executes(context -> {
					report(context.getSource());
					return 1;
				})
				.then(literal("set")
						.then(argument("amount", StringArgumentType.word())
								.executes(context -> {
									String raw = StringArgumentType.getString(context, "amount").replace("$", "");
									Double value = parseAmount(raw);
									if (value == null) {
										context.getSource().sendError(Text.literal(
												"Couldn't read that amount. Try 1500000 or 1.5M"));
										return 0;
									}
									tracker.setBalance(value);
									context.getSource().sendFeedback(Text.literal(
											"Balance set to $" + formatExact(value)).formatted(Formatting.GREEN));
									return 1;
								})))
				.then(literal("debug")
						.executes(context -> {
							debug = !debug;
							context.getSource().sendFeedback(Text.literal(
									"Debug " + (debug ? "ON: any message containing 'paid' will be echoed" : "OFF"))
									.formatted(Formatting.YELLOW));
							return 1;
						}))
				.then(literal("reset")
						.executes(context -> {
							tracker.resetToday();
							context.getSource().sendFeedback(
									Text.literal("Today's change reset to $0.").formatted(Formatting.YELLOW));
							return 1;
						})));
	}

	private void report(FabricClientCommandSource source) {
		double delta = tracker.getDelta();
		Formatting deltaColor = delta > 0 ? Formatting.GREEN : delta < 0 ? Formatting.RED : Formatting.GRAY;
		String sign = delta > 0 ? "+" : delta < 0 ? "-" : "";
		String deltaStr = sign + "$" + formatAbbrev(Math.abs(delta));

		Text message;
		if (tracker.hasBalance()) {
			message = Text.literal("Balance: ").formatted(Formatting.GOLD)
					.append(Text.literal("$" + formatExact(tracker.getBalance())).formatted(Formatting.WHITE))
					.append(Text.literal("  |  Since midnight: ").formatted(Formatting.GOLD))
					.append(Text.literal(deltaStr).formatted(deltaColor));
		} else {
			message = Text.literal("Since midnight: ").formatted(Formatting.GOLD)
					.append(Text.literal(deltaStr).formatted(deltaColor));
		}
		source.sendFeedback(message);

		if (!tracker.hasBalance()) {
			source.sendFeedback(Text.literal("Tip: /moneycheck set <amount> to also track your total balance.")
					.formatted(Formatting.GRAY));
		}
	}

	/** Parses "1,500", "2.5K", "3M", "1.2B", "1T", "1Q" into a double. */
	static Double parseAmount(String s) {
		try {
			s = s.replace(",", "").trim().toUpperCase(Locale.ROOT);
			double mult = 1;
			if (!s.isEmpty() && Character.isLetter(s.charAt(s.length() - 1))) {
				switch (s.charAt(s.length() - 1)) {
					case 'K' -> mult = 1e3;
					case 'M' -> mult = 1e6;
					case 'B' -> mult = 1e9;
					case 'T' -> mult = 1e12;
					case 'Q' -> mult = 1e15;
					default -> {
						return null;
					}
				}
				s = s.substring(0, s.length() - 1);
			}
			return Double.parseDouble(s) * mult;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String formatExact(double value) {
		return String.format(Locale.US, "%,.0f", value);
	}

	private static String formatAbbrev(double value) {
		double[] thresholds = {1e15, 1e12, 1e9, 1e6, 1e3};
		String[] suffixes = {"Q", "T", "B", "M", "K"};

		for (int i = 0; i < thresholds.length; i++) {
			if (value >= thresholds[i]) {
				double scaled = value / thresholds[i];
				String formatted = String.format(Locale.US, "%.1f", scaled);
				if (formatted.endsWith(".0")) {
					formatted = formatted.substring(0, formatted.length() - 2);
				}
				return formatted + suffixes[i];
			}
		}
		return String.format(Locale.US, "%,.0f", value);
	}
}
