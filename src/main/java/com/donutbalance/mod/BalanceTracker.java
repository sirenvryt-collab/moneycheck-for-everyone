package com.donutbalance.mod;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * DonutSMP removed its API, so instead of polling a balance, this tracks
 * payment chat messages: money paid TO you goes up, money you paid goes down.
 * "Today's change" resets at local midnight. If you set a starting balance
 * with /moneycheck set <amount>, the total balance is tracked too.
 */
public class BalanceTracker {
	private static final Path STATE_PATH = FabricLoader.getInstance()
			.getConfigDir()
			.resolve("donutbalance_state.json");
	private static final Gson GSON = new Gson();

	private State state;

	public BalanceTracker() {
		this.state = loadState();
		rollDayIfNeeded();
	}

	/** Positive = you received money, negative = you paid money. */
	public synchronized void onPayment(double delta) {
		rollDayIfNeeded();
		state.todayDelta += delta;
		if (state.hasBalance) {
			state.balance += delta;
		}
		saveState();
	}

	public synchronized void setBalance(double balance) {
		rollDayIfNeeded();
		state.hasBalance = true;
		state.balance = balance;
		saveState();
	}

	public synchronized void resetToday() {
		state.date = LocalDate.now().toString();
		state.todayDelta = 0;
		saveState();
	}

	public synchronized boolean hasBalance() {
		return state.hasBalance;
	}

	public synchronized double getBalance() {
		return state.balance;
	}

	public synchronized double getDelta() {
		rollDayIfNeeded();
		return state.todayDelta;
	}

	private void rollDayIfNeeded() {
		String today = LocalDate.now().toString();
		if (state.date == null || !state.date.equals(today)) {
			state.date = today;
			state.todayDelta = 0;
			saveState();
		}
	}

	private State loadState() {
		if (Files.exists(STATE_PATH)) {
			try {
				String json = Files.readString(STATE_PATH, StandardCharsets.UTF_8);
				State loaded = GSON.fromJson(json, State.class);
				if (loaded != null) {
					return loaded;
				}
			} catch (IOException e) {
				DonutBalanceClient.LOGGER.error("Failed to read donutbalance_state.json", e);
			}
		}
		return new State();
	}

	private void saveState() {
		try {
			Files.createDirectories(STATE_PATH.getParent());
			Files.writeString(STATE_PATH, GSON.toJson(state), StandardCharsets.UTF_8);
		} catch (IOException e) {
			DonutBalanceClient.LOGGER.error("Failed to save donutbalance_state.json", e);
		}
	}

	private static class State {
		String date;
		double todayDelta;
		boolean hasBalance;
		double balance;
	}
}
