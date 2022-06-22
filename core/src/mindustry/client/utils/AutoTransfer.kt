package mindustry.client.utils

import arc.*
import arc.struct.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.ClientVars.*
import mindustry.gen.*
import mindustry.type.*
import mindustry.world.consumers.*

/** An auto transfer setup based on Ferlern/extended-ui */
class AutoTransfer {
    companion object Settings {
        @JvmField var enabled = Core.settings.getBool("autotransfer", false)
        var fromCores = true
        var minCoreItems = 20
            set(_) = TODO("Min core items not yet implemented")
        var delay = 30F
    }

    private val dest = Seq<Building>()
    private var item: Item? = null
    private var timer = 0F

    fun update() {
        if (!enabled) return
        if (ratelimitRemaining <= 1) return
        player.unit().item() ?: return
        timer += Time.delta
        if (timer < delay) return
        timer = 0F
        val buildings = player.team().data().buildings ?: return
        val core = if (fromCores) player.closestCore() else null

        buildings.intersect(player.x - itemTransferRange, player.y - itemTransferRange, itemTransferRange * 2, itemTransferRange * 2, dest.clear())
        dest.filter { it.block.consumes.has(ConsumeType.item) }.sort { b -> b.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit()).toFloat() }
        .forEach {
            if (ratelimitRemaining <= 1) return@forEach

            if (it.acceptStack(player.unit().item(), player.unit().stack.amount, player.unit()) > 0) {
                Call.transferInventory(player, it)
                ratelimitRemaining--
            }

            if (item == null && core != null) { // Automatically take needed item from core, only request once FINISHME: int[content.items().size)] that keeps track of number of each item needed so that this can be more efficient
                item = run<Item?> { // FINISHME: I should really just make this its own function
                    when (val cons = it.block.consumes.get<Consume>(ConsumeType.item)) { // Cursed af
                        is ConsumeItems -> {
                            cons.items.forEach { i ->
                                if (it.acceptStack(i.item, it.getMaximumAccepted(i.item), player.unit()) >= 7) { // FINISHME: Do not hardcode the minumum required number (7) here, this is awful
                                    return@run i.item
                                }
                            }
                        }
                        is ConsumeItemFilter -> {
                            content.items().forEach { i ->
                                if (it.block.consumes.consumesItem(i) && it.acceptStack(i, Int.MAX_VALUE, player.unit()) >= 7) {
                                    return@run item
                                }
                            }
                        }
                        is ConsumeItemDynamic -> {
                            cons.items.get(it).forEach { i -> // Get the current requirements
                                if (it.acceptStack(i.item, i.amount, player.unit()) >= 7) {
                                    return@run i.item
                                }
                            }
                        }
                        else -> throw IllegalArgumentException("This should never happen. Report this.")
                    }
                    return@run null
                }
            }
        }

        if (item != null && core != null && player.within(core, itemTransferRange)) {
            if (player.unit().hasItem()) Call.transferInventory(player, core)
            else Call.requestItem(player, core, item, Int.MAX_VALUE)
            item = null
            ratelimitRemaining--
        }
    }
}