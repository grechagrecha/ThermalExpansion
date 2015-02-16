package cofh.thermalexpansion.block.cache;

import cofh.api.inventory.IInventoryRetainer;
import cofh.api.tileentity.IReconfigurableFacing;
import cofh.api.tileentity.ISidedTexture;
import cofh.api.tileentity.ITileInfo;
import cofh.core.network.PacketCoFHBase;
import cofh.core.render.IconRegistry;
import cofh.lib.util.helpers.BlockHelper;
import cofh.lib.util.helpers.ItemHelper;
import cofh.lib.util.helpers.MathHelper;
import cofh.lib.util.helpers.StringHelper;
import cofh.thermalexpansion.ThermalExpansion;
import cofh.thermalexpansion.block.TileInventory;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;

import powercrystals.minefactoryreloaded.api.IDeepStorageUnit;

public class TileCache extends TileInventory implements IDeepStorageUnit, IReconfigurableFacing, ISidedInventory, IInventoryRetainer, ISidedTexture, ITileInfo {

	public static void initialize() {

		GameRegistry.registerTileEntity(TileCache.class, "thermalexpansion.Cache");
	}

	public static int[] CAPACITY = { Integer.MAX_VALUE, 10000, 40000, 160000, 640000 };
	public static final int[] SLOTS = { 0, 1 };

	static {
		String category = "Cache.";
		CAPACITY[4] = MathHelper.clampI(ThermalExpansion.config.get(category + StringHelper.titleCase(BlockCache.NAMES[4]), "Capacity", CAPACITY[4]),
				CAPACITY[4] / 8, 1000000 * 1000);
		CAPACITY[3] = MathHelper.clampI(ThermalExpansion.config.get(category + StringHelper.titleCase(BlockCache.NAMES[3]), "Capacity", CAPACITY[3]),
				CAPACITY[3] / 8, CAPACITY[4]);
		CAPACITY[2] = MathHelper.clampI(ThermalExpansion.config.get(category + StringHelper.titleCase(BlockCache.NAMES[2]), "Capacity", CAPACITY[2]),
				CAPACITY[2] / 8, CAPACITY[3]);
		CAPACITY[1] = MathHelper.clampI(ThermalExpansion.config.get(category + StringHelper.titleCase(BlockCache.NAMES[1]), "Capacity", CAPACITY[1]),
				CAPACITY[1] / 8, CAPACITY[2]);
	}

	int meterTracker;
	int compareTracker;

	public byte type = 1;
	public byte facing = 3;

	public boolean locked;
	public int maxCacheStackSize;
	public ItemStack storedStack;

	public TileCache() {

		inventory = new ItemStack[2];
	}

	public TileCache(int metadata) {

		type = (byte) metadata;
		inventory = new ItemStack[2];
		maxCacheStackSize = CAPACITY[type] - 64 * 2;
	}

	@Override
	public String getName() {

		return "tile.thermalexpansion.cache." + BlockCache.NAMES[getType()] + ".name";
	}

	@Override
	public int getType() {

		return type;
	}

	@Override
	public int getComparatorInput(int side) {

		return compareTracker;
	}

	public int getScaledItemsStored(int scale) {

		return getStoredCount() * scale / CAPACITY[type];
	}

	@Override
	public boolean canUpdate() {

		return false;
	}

	public boolean toggleLock() {

		locked = !locked;

		if (getStoredCount() <= 0 && !locked) {
			clearInventory();
		}
		sendUpdatePacket(Side.CLIENT);
		return locked;
	}

	protected void balanceStacks() {

		inventory[0] = null;
		inventory[1] = ItemHelper.cloneStack(storedStack, Math.min(storedStack.getMaxStackSize(), storedStack.stackSize));
		storedStack.stackSize -= inventory[1].stackSize;

		if (storedStack.stackSize > maxCacheStackSize) {
			inventory[0] = ItemHelper.cloneStack(storedStack, storedStack.stackSize - maxCacheStackSize);
			storedStack.stackSize = maxCacheStackSize;
		}
	}

	protected void clearInventory() {

		if (!locked) {
			storedStack = null;
			sendUpdatePacket(Side.CLIENT);
		} else {
			if (storedStack != null) {
				storedStack.stackSize = 0;
			}
		}
		inventory[0] = null;
		inventory[1] = null;
	}

	protected void updateTrackers() {

		int curScale = getScaledItemsStored(15);

		if (compareTracker != curScale) {
			compareTracker = curScale;
			callNeighborTileChange();
		}
		curScale = Math.min(8, getScaledItemsStored(9));

		if (meterTracker != curScale) {
			meterTracker = curScale;
			sendUpdatePacket(Side.CLIENT);
		}
	}

	/* GUI METHODS */
	@Override
	public boolean hasGui() {

		return false;
	}

	/* NBT METHODS */
	@Override
	public void readFromNBT(NBTTagCompound nbt) {

		type = nbt.getByte("Type");
		facing = nbt.getByte("Facing");
		locked = nbt.getBoolean("Lock");

		if (nbt.hasKey("Item")) {
			storedStack = ItemHelper.readItemStackFromNBT(nbt.getCompoundTag("Item"));
			maxCacheStackSize = CAPACITY[type] - storedStack.getMaxStackSize() * 2;
		} else {
			maxCacheStackSize = CAPACITY[type] - 64 * 2;
		}
		super.readFromNBT(nbt);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {

		super.writeToNBT(nbt);

		nbt.setByte("Type", type);
		nbt.setByte("Facing", facing);
		nbt.setBoolean("Lock", locked);

		if (storedStack != null) {
			nbt.setTag("Item", ItemHelper.writeItemStackToNBT(storedStack, new NBTTagCompound()));
		}
	}

	/* NETWORK METHODS */
	@Override
	public PacketCoFHBase getPacket() {

		PacketCoFHBase payload = super.getPacket();
		payload.addByte(type);
		payload.addByte(facing);
		payload.addBool(locked);
		payload.addItemStack(storedStack);

		if (storedStack != null) {
			payload.addInt(getStoredCount());
		}
		return payload;
	}

	/* ITilePacketHandler */
	@Override
	public void handleTilePacket(PacketCoFHBase payload, boolean isServer) {

		super.handleTilePacket(payload, isServer);

		type = payload.getByte();
		facing = payload.getByte();
		locked = payload.getBool();
		storedStack = payload.getItemStack();

		if (storedStack != null) {
			storedStack.stackSize = payload.getInt();
			inventory[1] = null;
			balanceStacks();
		} else {
			storedStack = null;
			inventory[0] = null;
			inventory[1] = null;
		}
	}

	/* IDeepStorageUnit */
	@Override
	public ItemStack getStoredItemType() {

		return ItemHelper.cloneStack(storedStack, getStoredCount());
	}

	@Override
	public void setStoredItemCount(int amount) {

		if (storedStack == null || amount < 0) {
			return;
		}
		storedStack.stackSize = Math.min(amount, getMaxStoredCount());

		if (amount > 0) {
			balanceStacks();
		} else {
			clearInventory();
		}
		updateTrackers();
		markDirty();
	}

	@Override
	public void setStoredItemType(ItemStack stack, int amount) {

		if (stack == null) {
			clearInventory();
		} else {
			storedStack = ItemHelper.cloneStack(stack, Math.min(amount, getMaxStoredCount()));
			maxCacheStackSize = CAPACITY[type] - storedStack.getMaxStackSize() * 2;
			balanceStacks();
		}
		updateTrackers();
		sendUpdatePacket(Side.CLIENT);
		markDirty();
	}

	@Override
	public int getMaxStoredCount() {

		return CAPACITY[type];
	}

	/* IInventory */
	@Override
	public void setInventorySlotContents(int slot, ItemStack stack) {

		inventory[slot] = stack;

		boolean stackCheck = storedStack == null;

		if (slot == 0) { // insertion!
			if (inventory[0] == null) {
				return;
			}
			if (storedStack == null) {
				storedStack = inventory[0].copy();
				inventory[0] = null;
				maxCacheStackSize = CAPACITY[type] - storedStack.getMaxStackSize() * 2;
			} else {
				storedStack.stackSize += inventory[0].stackSize + (inventory[1] == null ? 0 : inventory[1].stackSize);
			}
			balanceStacks();
		} else { // extraction!
			if (storedStack == null) {
				return;
			}
			storedStack.stackSize += (inventory[0] == null ? 0 : inventory[0].stackSize) + (inventory[1] == null ? 0 : inventory[1].stackSize);

			if (storedStack.stackSize > 0) {
				balanceStacks();
			} else {
				clearInventory();
			}
		}
		updateTrackers();
		if (stackCheck != (storedStack == null)) {
			sendUpdatePacket(Side.CLIENT);
		}
		markDirty();
	}

	/* IReconfigurableFacing */
	@Override
	public int getFacing() {

		return facing;
	}

	@Override
	public boolean allowYAxisFacing() {

		return false;
	}

	@Override
	public boolean rotateBlock() {

		facing = BlockHelper.SIDE_LEFT[facing];
		markDirty();
		sendUpdatePacket(Side.CLIENT);
		return true;
	}

	@Override
	public boolean setFacing(int side) {

		if (side < 2 || side > 5) {
			return false;
		}
		facing = (byte) side;
		markDirty();
		sendUpdatePacket(Side.CLIENT);
		return true;
	}

	/* ISidedInventory */
	@Override
	public int[] getAccessibleSlotsFromSide(int side) {

		return SLOTS;
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side) {

		return slot == 0 && (storedStack == null || ItemHelper.itemsEqualWithMetadata(stack, storedStack, true));
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side) {

		return slot == 1;
	}

	/* ISidedTexture */
	@Override
	public IIcon getTexture(int side, int pass) {

		if (pass == 1) {
			if (side != facing) {
				return IconRegistry.getIcon("CacheBlank");
			}
			return IconRegistry.getIcon("CacheMeter", Math.min(8, getScaledItemsStored(9)));
		}
		if (side == 0) {
			return IconRegistry.getIcon("CacheBottom", type);
		} else if (side == 1) {
			return IconRegistry.getIcon("CacheTop", type);
		}
		return side != facing ? IconRegistry.getIcon("CacheSide", type) : IconRegistry.getIcon("CacheFace", type);
	}

	/* ITileInfo */
	@Override
	public void getTileInfo(List<IChatComponent> info, ForgeDirection side, EntityPlayer player, boolean debug) {

		if (debug) {
			return;
		}
		if (storedStack != null) {
			info.add(new ChatComponentText(StringHelper.localize("info.cofh.item") + ": " + StringHelper.getItemName(storedStack)));
			info.add(new ChatComponentText(StringHelper.localize("info.cofh.amount") + ": " + getStoredCount() + " / " + CAPACITY[type]));
		} else {
			info.add(new ChatComponentText(StringHelper.localize("info.cofh.item") + ": " + StringHelper.localize("info.cofh.empty")));
		}
		info.add(new ChatComponentText(locked ? StringHelper.localize("info.cofh.locked") : StringHelper.localize("info.cofh.unlocked")));
	}

	/* Prototype Handler Stuff */
	public int getStoredCount() {

		return storedStack == null ? 0 : storedStack.stackSize + (inventory[0] == null ? 0 : inventory[0].stackSize)
				+ (inventory[1] == null ? 0 : inventory[1].stackSize);
	}

	public ItemStack insertItem(ForgeDirection from, ItemStack stack, boolean simulate) {

		if (stack == null) {
			return null;
		}
		if (storedStack == null) {
			if (!simulate) {
				setStoredItemType(stack, stack.stackSize);
			}
			return null;
		}
		if (getStoredCount() == CAPACITY[type]) {
			return stack;
		}
		if (ItemHelper.itemsIdentical(stack, storedStack)) {
			if (getStoredCount() + stack.stackSize > CAPACITY[type]) {
				ItemStack retStack = ItemHelper.cloneStack(stack, CAPACITY[type] - getStoredCount());
				if (!simulate) {
					setStoredItemCount(CAPACITY[type]);
				}
				return retStack;
			}
			if (!simulate) {
				setStoredItemCount(getStoredCount() + stack.stackSize);
			}
			return null;
		}
		return stack;
	}

	public ItemStack extractItem(ForgeDirection from, int maxExtract, boolean simulate) {

		if (storedStack == null) {
			return null;
		}
		ItemStack ret = ItemHelper.cloneStack(storedStack, Math.min(getStoredCount(), Math.min(maxExtract, storedStack.getMaxStackSize())));

		if (!simulate) {
			setStoredItemCount(getStoredCount() - ret.stackSize);
		}
		return ret;
	}

}