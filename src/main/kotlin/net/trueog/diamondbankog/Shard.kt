package net.trueog.diamondbankog

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe
import org.bukkit.persistence.PersistentDataType

object Shard {
    val namespacedKey = NamespacedKey(DiamondBankOG.plugin, "diamondshard")

    /** amount defaults to 1 */
    fun createItemStack(amount: Int = 1): ItemStack {
        val shard = ItemStack(Material.PRISMARINE_SHARD, amount)
        val shardMeta = shard.itemMeta
        shardMeta.displayName(DiamondBankOG.mm.deserialize("<aqua>Diamond Shard"))
        shardMeta.persistentDataContainer.set(namespacedKey, PersistentDataType.STRING, "")
        shardMeta.addEnchant(Enchantment.DURABILITY, 1, false)
        shardMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        shard.itemMeta = shardMeta
        return shard
    }

    fun createCraftingRecipes() {
        val diamondToShardKey = NamespacedKey(DiamondBankOG.plugin, "diamond_to_shard")
        val diamondToShardRecipe = ShapelessRecipe(diamondToShardKey, createItemStack(9))
        diamondToShardRecipe.addIngredient(Material.DIAMOND)
        Bukkit.addRecipe(diamondToShardRecipe)

        val shardToDiamondKey = NamespacedKey(DiamondBankOG.plugin, "shard_to_diamond")
        val shardToDiamondRecipe = ShapedRecipe(shardToDiamondKey, ItemStack(Material.DIAMOND))
        shardToDiamondRecipe.shape("SSS", "SSS", "SSS")
        shardToDiamondRecipe.setIngredient('S', createItemStack())
        Bukkit.addRecipe(shardToDiamondRecipe)
    }
}
