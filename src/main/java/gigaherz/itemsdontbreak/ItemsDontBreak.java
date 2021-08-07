package gigaherz.itemsdontbreak;

import com.google.common.collect.EnumHashBiMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import org.apache.commons.lang3.tuple.Pair;

import java.util.EnumMap;
import java.util.List;

@Mod(ItemsDontBreak.MODID)
public class ItemsDontBreak
{
    public static final String MODID = "itemsdontbreak";

    public ItemsDontBreak()
    {
        //Make sure the mod being absent on the other network side does not cause the client to display the server as incompatible
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
    }

    @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientEvents
    {
        private static boolean isAboutToBreak(ItemStack stack)
        {
            return stack.isDamageable() && (stack.getDamage()+1) >= stack.getMaxDamage() && (!Screen.hasControlDown());
        }

        private static EnumMap<Hand, ItemStack> previousStacks = new EnumMap<>(Hand.class);

        public static int adjustedDurability(ItemStack stack, int remaining)
        {
            int unbreaking = EnchantmentHelper.getEnchantmentLevel(Enchantments.UNBREAKING, stack);

            // armor: 40%*[50%,33%,25%,...] chance to cancel each individual point of durability reduction
            // others: [50%,33%,25%,...] chance

            double chance = 1.0/(unbreaking+1);
            if (stack.getItem() instanceof ArmorItem)
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
                ClientPlayerEntity player = Minecraft.getInstance().player;
                if (player == null || player.isCreative())
                    return;

                boolean alreadySet = false;
                for(Hand hand : Hand.values())
                {
                    ItemStack stack = player.getHeldItem(hand);
                    ItemStack previousStack = previousStacks.computeIfAbsent(hand, (key) -> ItemStack.EMPTY);

                    if (!ItemStack.areItemStacksEqual(previousStack, stack))
                    {
                        previousStacks.put(hand, stack);
                        if (stack.isDamageable())
                        {
                            int remaining = stack.getMaxDamage() - stack.getDamage();
                            int uses = adjustedDurability(stack, remaining);

                            if (remaining <= 10 && uses <= 20)
                            {
                                if (!alreadySet)
                                {
                                    alreadySet = true;

                                    TranslationTextComponent tc;
                                    if (isAboutToBreak(stack))
                                    {
                                        tc = new TranslationTextComponent("text.itemsdontbreak.item_info_disabled", remaining);
                                    }
                                    else if (EnchantmentHelper.getEnchantmentLevel(Enchantments.UNBREAKING, stack) > 0)
                                    {
                                        tc = new TranslationTextComponent("text.itemsdontbreak.item_info.unbreaking", remaining, uses);
                                    }
                                    else
                                    {
                                        tc = new TranslationTextComponent("text.itemsdontbreak.item_info.normal", remaining, uses);
                                    }

                                    Minecraft.getInstance().ingameGUI.setOverlayMessage(tc, false);
                                }
                            }
                        }
                    }
                }
            }
        }

        @SubscribeEvent
        public static void itemInteractEvent(AttackEntityEvent event)
        {
            if (event.getPlayer().isCreative())
                return;
            ItemStack stack = event.getPlayer().getHeldItemMainhand();
            if (isAboutToBreak(stack))
            {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void itemInteractEvent(PlayerInteractEvent.RightClickItem event)
        {
            if (event.getPlayer().isCreative())
                return;
            ItemStack stack = event.getItemStack();
            if (isAboutToBreak(stack) && isRightClickItem(stack))
            {
                event.setCanceled(true);
                event.setCancellationResult(ActionResultType.PASS);
            }
        }

        @SubscribeEvent
        public static void itemInteractEvent(PlayerInteractEvent.LeftClickBlock event)
        {
            if (event.getPlayer().isCreative())
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
                List<ITextComponent> tips = event.getToolTip();

                if (isAboutToBreak(stack))
                {
                    int insert = Math.min(tips.size(),1);

                    TranslationTextComponent br = new TranslationTextComponent("tooltip.itemsdontbreak.item_broken");
                    br.applyTextStyles(TextFormatting.RED, TextFormatting.BOLD, TextFormatting.ITALIC);
                    event.getToolTip().add(insert, br);
                }
                else //if (event.getFlags() == ITooltipFlag.TooltipFlags.ADVANCED)
                {
                    boolean indent = false;
                    int insert = tips.size();
                    for(int i=0;i<tips.size();i++)
                    {
                        ITextComponent t = tips.get(i);
                        if (t instanceof TranslationTextComponent)
                        {
                            TranslationTextComponent tt = (TranslationTextComponent)t;
                            if ("item.durability".equals(tt.getKey()))
                            {
                                insert = i+1;
                                indent = true;
                                break;
                            }
                            else if ("item.modifiers.mainhand".equals(tt.getKey()))
                            {
                                insert = Math.min(insert, i);
                                indent = false;
                            }
                            else if ("item.modifiers.offhand".equals(tt.getKey()))
                            {
                                insert = Math.min(insert, i);
                                indent = false;
                            }
                        }
                    }

                    int remaining = stack.getMaxDamage() - stack.getDamage();

                    ITextComponent uses = new TranslationTextComponent("tooltip.itemsdontbreak.item_info", adjustedDurability(stack, remaining));
                    uses.applyTextStyles(TextFormatting.ITALIC, TextFormatting.GRAY);

                    if (indent)
                    {
                        StringTextComponent ts = new StringTextComponent(" ");
                        ts.appendSibling(uses);
                        uses = ts;
                    }
                    event.getToolTip().add(insert, uses);
                }
            }
        }
    }
}
