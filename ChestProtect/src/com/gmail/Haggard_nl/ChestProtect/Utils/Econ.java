package com.gmail.Haggard_nl.ChestProtect.Utils;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.gmail.Haggard_nl.ChestProtect.Managers.ChestLockMessages;
import com.gmail.Haggard_nl.ServerCore.Economy.Economy;

/**
 * Manages payment of owning/locking Chests
 * 
 * @author Codisimus
 */
public class Econ {
    public static Economy economy;

    /**
     * Charges a Player a given amount of money, which goes to a Player/Bank
     * 
     * @param player The name of the Player to be charged
     * @param source The Player/Bank that will receive the money
     * @param amount The amount that will be charged
     * @return True if the transaction was successful
     */
    public static boolean charge(Player player, double amount, Material type) {
        String name = player.getName();
        
        //Cancel if the Player cannot afford the transaction
        if (!economy.has(name, amount)) {
            player.sendMessage(ChestLockMessages.getInsufficientFundsMsg(amount, type));
            return false;
        }
        
        economy.withdrawPlayer(name, amount);
        return true;
    }
    
    /**
     * Formats the money amount by adding the unit
     * 
     * @param amount The amount of money to be formatted
     * @return The String of the amount + currency name
     */
    public static String format(double amount) {
        return economy.format(amount).replace(".00", "");
    }
}
