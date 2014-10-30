package com.gmail.Haggard_nl.ChestProtect.Listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.material.Door;

import com.gmail.Haggard_nl.ChestProtect.ChestProtect;
import com.gmail.Haggard_nl.ChestProtect.Managers.ChestLockMessages;
import com.gmail.Haggard_nl.ChestProtect.Utils.Econ;
import com.gmail.Haggard_nl.ChestProtect.Utils.LockedDoor;
import com.gmail.Haggard_nl.ChestProtect.Utils.Safe;

/**
 * Checks for interactions with Safes and locked doors
 *
 * @author Codisimus
 */
public class ChestLockListener implements Listener {
    public static boolean unlockToOpen;
    public static double ownPrice;
    public static double lockPrice;

    /**
     * Listens for Block interaction for owning Blocks or opening owned Blocks
     * 
     * @param event The PlayerInteractEvent that occurred
     */
    @EventHandler (priority = EventPriority.HIGH)
    public void onPlayerInteract (PlayerInteractEvent event) {
        //Return if the Action was not clicking a Block
        Action action = event.getAction();
        switch (action) {
            case LEFT_CLICK_BLOCK: break;
            case RIGHT_CLICK_BLOCK: break;
            default: return;
        }
        
        if (event.isCancelled())
            return;
        
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        
        //Check if the Block is a LockedDoor
        LockedDoor lockedDoor = ChestProtect.findDoor(block);
        if (lockedDoor != null) {
            //Cancel if the Player does not have permission to open Locked Doors
            if (!ChestProtect.hasPermission(player, "usekey")) {
                player.sendMessage(ChestLockMessages.getPermissionMsg());
                return;
            }
            
            //Open/shut the Door only if the Player is holding the key
            if (lockedDoor.hasKey(player)) {
                switch (block.getType()) {
                    case IRON_DOOR: //Fall through
                    case IRON_DOOR_BLOCK:
                        //Convert the Block to a Door
                        BlockState state = block.getState();
                        Door door = (Door)state.getData();
                        
                        //Toggle the open status of the Door
                        door.setOpen(!door.isOpen());
                        state.update();
                        
                        //Get the other half of the Door
                        state = door.isTopHalf() ? block.getRelative(BlockFace.DOWN).getState()
                                : block.getRelative(BlockFace.UP).getState();
                        door = (Door)state.getData();
                        
                        //Toggle the open status of the Door
                        door.setOpen(!door.isOpen());
                        state.update();
                        
                        break;
                        
                    default: break;
                }
            }
            else {
                event.setCancelled(true);
                player.sendMessage(ChestLockMessages.getInvalidKeyMsg(player.getItemInHand().getType()));
            }
            
            return;
        }
        
        //Cancel if the Block is not a Safe type
        Material material = block.getType();
        switch (material) {
            case DISPENSER: break;
            case CHEST: break;
            case FURNACE: break;
            case BURNING_FURNACE: break;
            default: return;
        }
        
        //Get the Type of the Block
        String type = material.toString().toLowerCase();
        if (type.startsWith("b"))
            type = "furnace";
        
        int holding = player.getItemInHand().getTypeId();
        
        Safe safe = ChestProtect.findSafe(block);
        if (safe == null) { //Safe is unowned
            //Return if the Player right clicked the Safe
            if (action.equals(Action.RIGHT_CLICK_BLOCK))
                return;
            
            //Return if the Player is not holding the 'own' tool
            if (ChestProtect.own != -1 && ChestProtect.own != holding)
                return;
            
            //Cancel if the Player does not have permission to use the command
            if (!ChestProtect.hasPermission(player, "lock")) {
                player.sendMessage(ChestLockMessages.getPermissionMsg());
                return;
            }
            
            int limit = ChestProtect.getOwnLimit(player, type); 
            int owned = -2;
            
            //Retrieve number of owned Safes if there is a limit
            if (limit > -1)
                switch (material) {
                    case DISPENSER: owned = ChestProtect.getOwnedDispensers(player.getName()).size(); break;
                    case CHEST: owned = ChestProtect.getOwnedChests(player.getName()).size(); break;
                    case FURNACE: owned = ChestProtect.getOwnedFurnaces(player.getName()).size(); break;
                    case BURNING_FURNACE: owned = ChestProtect.getOwnedFurnaces(player.getName()).size(); break;
                    default: return;
                }
            
            //Return if the Player has reached their limit
            if (owned >= limit) {
                player.sendMessage(ChestLockMessages.getLimitMsg(material));
                return;
            }
            
            //Charge the Player if they do not have the 'ChestProtect.free' node
            if (ownPrice > 0 && !ChestProtect.hasPermission(player, "free"))
                //Return if the Player had insufficient funds
                if (!Econ.charge(player, ownPrice, material))
                    return;

            ChestProtect.addSafe(new Safe(player.getName(), block));

            String msg = ChestLockMessages.getOwnMsg(material);
            
            if (Econ.economy != null)
                msg = msg.replaceAll("<price>", ChestProtect.hasPermission(player, "free")
                        ? Econ.format(0) : Econ.format(lockPrice));

            player.sendMessage(msg);
            ChestProtect.save(block.getWorld());
            return;
        }

        //Check if the Action was opening a Safe
        if (action.equals(Action.RIGHT_CLICK_BLOCK)) {
            //Cancel the Event if the Safe is locked
            if (safe.locked && safe.lockable && (unlockToOpen || !(safe.owner.equalsIgnoreCase(player.getName())
                    || safe.isCoOwner(player)))) {
                event.setCancelled(true);
                player.sendMessage(ChestLockMessages.getLockedMsg(material));
            }

            return;
        }
        
        //Check if the Player is the Owner/CoOwner
        if (safe.owner.equalsIgnoreCase(player.getName()) || safe.isCoOwner(player)) {
            //Remove the Safe if the Owner is attempting to disown it
            if (safe.owner.equalsIgnoreCase(player.getName()) && ChestProtect.disown == -1 || ChestProtect.disown == holding) {
                ChestProtect.removeSafe(safe);
                player.sendMessage(ChestLockMessages.getDisownMsg(material));
                ChestProtect.save(safe.block.getWorld());
                return;
            }

            //Check if the Player is attempting to lock the safe
            if (ChestProtect.lock == -1 || ChestProtect.lock == holding) {
                //Return if the Safe is not lockable
                if (!safe.lockable)
                    return;
                
                //Return if the Player does not have permission to lock
                if (!ChestProtect.hasPermission(player, "lock")) {
                    player.sendMessage(ChestLockMessages.getPermissionMsg());
                    return;
                }

                //Charge the Player if they do not have the 'ChestProtect.free' node
                if (!safe.locked && lockPrice > 0 && !ChestProtect.hasPermission(player, "free"))
                    //Return if the Player had insufficient funds
                    if (!Econ.charge(player, lockPrice, material))
                        return;

                safe.locked = !safe.locked;

                if (!safe.locked)
                    player.sendMessage(ChestLockMessages.getUnlockMsg(material));
                else {
                    String msg = ChestLockMessages.getLockMsg(material);
            
                    if (Econ.economy != null)
                        msg = msg.replaceAll("<price>", ChestProtect.hasPermission(player, "free")
                                ? Econ.format(0) : Econ.format(lockPrice));

                    player.sendMessage(msg);
                }
            }
        }
        else {
            //Check if the Player is an Admin
            if (ChestProtect.hasPermission(player, "admin")) {
                //Check if the Player is requesting info on the safe
                if (ChestProtect.info == holding) {
                    player.sendMessage(type+" owned by: "+safe.owner);
                    return;
                }

                //Check if the Player is attempting to disown the safe
                if (ChestProtect.adminDisown == -1 || ChestProtect.adminDisown == holding) {
                    ChestProtect.removeSafe(safe);
                    player.sendMessage(ChestLockMessages.getDisownMsg(material));
                    ChestProtect.save(safe.block.getWorld());
                    return;
                }

                //Check if the Player is attempting to lock the safe
                if (ChestProtect.admin == -1 || ChestProtect.admin == holding) {
                    //Return if the Safe is not lockable
                    if (!safe.lockable)
                        return;

                    safe.locked = !safe.locked;

                    if (safe.locked)
                        player.sendMessage(ChestLockMessages.getLockMsg(material));
                    else
                        player.sendMessage(ChestLockMessages.getUnlockMsg(material));

                    return;
                }
            }

            player.sendMessage(ChestLockMessages.getDoNotOwnMsg(material));
        }
    }
    
    /**
     * Blocks Players from opening locked doors with redstone
     * 
     * @param event The BlockRedstoneEvent that occurred
     */
    @EventHandler
    public void onBlockRedstoneChange(BlockRedstoneEvent event) {
        //Return if the Block is not a LockedDoor
        LockedDoor lockedDoor = ChestProtect.findDoor(event.getBlock());
        if (lockedDoor == null)
            return;
        
        //Return if the key is unlockable
        if (lockedDoor.key == 0)
            return;
        
        switch (lockedDoor.block.getType()) {
            case TRAP_DOOR: event.setNewCurrent(event.getOldCurrent()); break;
            case FENCE_GATE: break;
            default:
                //Allow Redstone to close a Door but not open it
                if (!((Door)lockedDoor.block.getState().getData()).isOpen())
                    event.setNewCurrent(event.getOldCurrent());
        }
    }

    /**
     * Prevents Players from breaking owned Blocks
     * 
     * @param event The BlockBreakEvent that occurred
     */
    @EventHandler
    public void onBlockBreak (BlockBreakEvent event) {
        if (event.isCancelled())
            return;
        
        Block block = event.getBlock();
        Player player = event.getPlayer();
        
        //Check if the Block is a LockedDoor
        LockedDoor door = ChestProtect.findDoor(block);
        if (door != null) {
            //Cancel the event if the Player is not the Owner of the LockedDoor and does not have the admin node
            if (!player.getName().equals(door.owner) && !ChestProtect.hasPermission(player, "admin")) {
                event.setCancelled(true);
                return;
            }
            
            //Delete the LockedDoor from the saved data
            ChestProtect.doors.remove(door);
            return;
        }
        
        //Return if the Block is not a Safe
        Safe safe = ChestProtect.findSafe(block);
        if (safe == null)
            return;

        //Cancel the event if the Player is not the Owner of the Safe
        if (!safe.isOwner(player)) {
            event.setCancelled(true);
            return;
        }
        
        //Delete the Safe from the saved data
        ChestProtect.removeSafe(safe);
    }
    
    
    
    /**
     * Updates the last time that Players that own Chunks were seen
     * 
     * @param event The PlayerJoinEvent that occurred
     */
    @EventHandler (priority = EventPriority.MONITOR)
    public void onPlayerJoin (PlayerJoinEvent event) {
        String name = event.getPlayer().getName();
        ChestProtect.lastDaySeen.setProperty(name, String.valueOf(ChestProtect.getDayAD()));
        ChestProtect.saveLastSeen();
    }
    
    /**
     * Updates the last time that Players that own Chunks were seen
     * 
     * @param event The PlayerQuitEvent that occurred
     */
    @EventHandler (priority = EventPriority.MONITOR)
    public void onPlayerQuit (PlayerQuitEvent event) {
        String name = event.getPlayer().getName();
        ChestProtect.lastDaySeen.setProperty(name, String.valueOf(ChestProtect.getDayAD()));
        ChestProtect.saveLastSeen();
    }
}