package com.example.hubcore.bank;

import com.example.hubcore.HubCorePlugin;

import java.util.UUID;

public class BankService {

    private final HubCorePlugin plugin;
    private final BankManager bankManager;

    public BankService(HubCorePlugin plugin, BankManager bankManager) {
        this.plugin = plugin;
        this.bankManager = bankManager;
    }

    public BankAccount ensureAccount(UUID uuid, String name) {
        return bankManager.getOrCreate(uuid, name);
    }

    public synchronized long deposit(UUID uuid, String name, long amount) {
        BankAccount account = bankManager.getOrCreate(uuid, name);
        if (amount < 0) return account.getBalance();
        account.setBalance(account.getBalance() + amount);
        bankManager.save(account);
        plugin.getLogger().info("[Starfun/Bank] Deposit " + amount + " to " + uuid + " -> " + account.getBalance());
        return account.getBalance();
    }

    public synchronized long withdraw(UUID uuid, String name, long amount) {
        BankAccount account = bankManager.getOrCreate(uuid, name);
        if (amount < 0) return account.getBalance();
        long balance = account.getBalance();
        if (balance < amount) return balance;
        account.setBalance(balance - amount);
        bankManager.save(account);
        plugin.getLogger().info("[Starfun/Bank] Withdraw " + amount + " from " + uuid + " -> " + account.getBalance());
        return account.getBalance();
    }

    public long balance(UUID uuid, String name) {
        BankAccount account = bankManager.getOrCreate(uuid, name);
        return account.getBalance();
    }
}
