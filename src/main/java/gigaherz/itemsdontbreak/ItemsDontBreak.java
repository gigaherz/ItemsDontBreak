package gigaherz.itemsdontbreak;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.List;

@Mod.EventBusSubscriber
@Mod(modid = ItemsDontBreak.MODID)
public class ItemsDontBreak
{
    public static final String MODID = "itemsdontbreak";

    @Mod.EventBusSubscriber(value = Side.CLIENT, modid = ItemsDontBreak.MODID)
    public static class ClientEvents
    {
        private static boolean isAboutToBreak(ItemStack stack)
        {
            return stack.isItemStackDamageable() && (stack.getItemDamage()+1) >= stack.getMaxDamage() && (!GuiScreen.isCtrlKeyDown());
        }

        private static ItemStack previousStack = ItemStack.EMPTY;

        public static int adjustedDurability(ItemStack stack, int remaining)
        {
            int unbreaking = EnchantmentHelper.getEnchantmentLevel(Enchantments.UNBREAKING, stack);

            // armor: 40%*[50%,33%,25%,...] chance to cancel each individual point of durability reduction
            // others: [50%,33%,25%,...] chance

            double chance = 1.0/(unbreaking+1);
            if (stack.getItem() instanceof ItemArmor)
            {
                chance *= 0.4;
            }

            double durability_coef = 1 / chance;

            return MathHelper.floor(remaining * durability_coef);
        }

        @SubscribeEvent
        public static void cientTick(TickEvent.ClientTickEvent event)
        {
            if(event.phase == TickEvent.Phase.END)
            {
                EntityPlayerSP player = Minecraft.getMinecraft().player;
                if (player == null || player.isCreative())
                    return;

                ItemStack stack = player.getHeldItemMainhand();

                if (!ItemStack.areItemStacksEqual(previousStack, stack))
                {
                    previousStack = stack;
                    if (stack.isItemStackDamageable())
                    {
                        int remaining = stack.getMaxDamage() - stack.getItemDamage();
                        int uses = adjustedDurability(stack, remaining);

                        if(remaining <= 10 && uses <= 20)
                        {
                            TextComponentTranslation tc;
                            if (isAboutToBreak(stack))
                            {
                                tc = new TextComponentTranslation("text.itemsdontbreak.item_info_disabled", remaining);
                            }
                            else if (EnchantmentHelper.getEnchantmentLevel(Enchantments.UNBREAKING, stack) > 0)
                            {
                                tc = new TextComponentTranslation("text.itemsdontbreak.item_info.unbreaking", remaining, uses);
                            }
                            else
                            {
                                tc = new TextComponentTranslation("text.itemsdontbreak.item_info.normal", remaining, uses);
                            }

                            Minecraft.getMinecraft().ingameGUI.setOverlayMessage(tc,false);
                        }
                    }
                }
            }
        }

        @SubscribeEvent
        public static void itemInteractEvent(AttackEntityEvent event)
        {
            if (event.getEntityPlayer().isCreative())
                return;
            ItemStack stack = event.getEntityPlayer().getHeldItemMainhand();
            if (isAboutToBreak(stack))
            {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void itemInteractEvent(PlayerInteractEvent.LeftClickBlock event)
        {
            if (event.getEntityPlayer().isCreative())
                return;
            ItemStack stack = event.getItemStack();
            if (isAboutToBreak(stack))
            {
                event.setUseItem(Event.Result.DENY);
            }
        }

        @SubscribeEvent
        public static void tooltipEvent(ItemTooltipEvent event)
        {
            ItemStack stack = event.getItemStack();
            if (stack.getItem().isDamageable())
            {
                List<String> tips = event.getToolTip();

                if (isAboutToBreak(stack))
                {
                    int insert = Math.min(tips.size(),1);

                    TextComponentTranslation br = new TextComponentTranslation("tooltip.itemsdontbreak.item_broken");
                    br.getStyle().setColor(TextFormatting.RED).setBold(true).setItalic(true);
                    event.getToolTip().add(insert, br.getFormattedText());
                }
                else //if (event.getFlags() == ITooltipFlag.TooltipFlags.ADVANCED)
                {
                    boolean indent = false;
                    int insert = tips.size();

                    if (stack.isItemDamaged())
                    {
                        String str = I18n.format("item.durability", stack.getMaxDamage() - stack.getItemDamage(), stack.getMaxDamage());
                        for(int i=0;i<tips.size();i++)
                        {
                            String t = tips.get(i);
                            if (str.equals(t))
                            {
                                insert = i+1; // insert after this
                                indent = true;
                                break;
                            }
                        }
                    }
                    if (!indent)
                    {
                        for (int i = 0; i < tips.size(); i++)
                        {
                            String t = tips.get(i);
                            for (EntityEquipmentSlot slot : EntityEquipmentSlot.values())
                            {
                                String str = I18n.format("item.modifiers." + slot.getName());
                                if (str.equals(t))
                                {
                                    insert = i; // insert before this
                                    break;
                                }
                            }
                        }
                    }

                    int remaining = stack.getMaxDamage() - stack.getItemDamage();

                    StringBuilder bld = new StringBuilder();
                    bld.append(TextFormatting.GRAY);
                    bld.append(TextFormatting.ITALIC);
                    if (indent) bld.append(" ");

                    bld.append(I18n.format("tooltip.itemsdontbreak.item_info", adjustedDurability(stack, remaining)));

                    event.getToolTip().add(insert, bld.toString());
                }
            }
        }
    }
}
