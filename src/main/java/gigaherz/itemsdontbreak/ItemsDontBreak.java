package gigaherz.itemsdontbreak;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fmllegacy.network.FMLNetworkConstants;

import java.util.EnumMap;
import java.util.List;

@Mod(ItemsDontBreak.MODID)
public class ItemsDontBreak
{
    public static final String MODID = "itemsdontbreak";

    public ItemsDontBreak()
    {
        //Make sure the mod being absent on the other network side does not cause the client to display the server as incompatible
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> "", (a, b) -> true));
    }

    @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientEvents
    {
        private static boolean isAboutToBreak(ItemStack stack)
        {
            return stack.isDamageableItem() && (stack.getDamageValue()+1) >= stack.getMaxDamage() && (!Screen.hasControlDown());
        }

        private static final EnumMap<InteractionHand, ItemStack> previousStacks = new EnumMap<>(InteractionHand.class);

        public static int adjustedDurability(ItemStack stack, int remaining)
        {
            int unbreaking = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, stack);

            // armor: 40%*[50%,33%,25%,...] chance to cancel each individual point of durability reduction
            // others: [50%,33%,25%,...] chance

            double chance = 1.0/(unbreaking+1);
            if (stack.getItem() instanceof ArmorItem)
            {
                chance *= 0.4;
            }

            double durability_coef = 1 / chance;

            return Mth.floor(remaining * durability_coef);
        }


        @SubscribeEvent
        public static void cientTick(TickEvent.ClientTickEvent event)
        {
            if(event.phase == TickEvent.Phase.END)
            {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player == null || player.isCreative())
                    return;

                boolean alreadySet = false;
                for(InteractionHand hand : InteractionHand.values())
                {
                    ItemStack stack = player.getItemInHand(hand);
                    ItemStack previousStack = previousStacks.computeIfAbsent(hand, (key) -> ItemStack.EMPTY);

                    if (!ItemStack.matches(previousStack, stack))
                    {
                        previousStacks.put(hand, stack);
                        if (stack.isDamageableItem())
                        {
                            int remaining = stack.getMaxDamage() - stack.getDamageValue();
                            int uses = adjustedDurability(stack, remaining);

                            if (remaining <= 10 && uses <= 20)
                            {
                                if (!alreadySet)
                                {
                                    alreadySet = true;

                                    TranslatableComponent tc;
                                    if (isAboutToBreak(stack))
                                    {
                                        tc = new TranslatableComponent("text.itemsdontbreak.item_info_disabled", remaining);
                                    }
                                    else if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, stack) > 0)
                                    {
                                        tc = new TranslatableComponent("text.itemsdontbreak.item_info.unbreaking", remaining, uses);
                                    }
                                    else
                                    {
                                        tc = new TranslatableComponent("text.itemsdontbreak.item_info.normal", remaining, uses);
                                    }

                                    Minecraft.getInstance().gui.setOverlayMessage(tc, false);
                                }
                            }
                        }
                    }
                }
            }
        }

        @SubscribeEvent
        public static void rightClick(InputEvent.ClickInputEvent event)
        {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || player.isCreative())
                return;
            ItemStack stack = player.getItemInHand(event.getHand());
            if (isAboutToBreak(stack))
            {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
        }

        @SubscribeEvent
        public static void tooltipEvent(ItemTooltipEvent event)
        {
            ItemStack stack = event.getItemStack();
            if (stack.getItem().canBeDepleted())
            {
                List<Component> tips = event.getToolTip();

                if (isAboutToBreak(stack))
                {
                    int insert = Math.min(tips.size(),1);

                    TranslatableComponent br = new TranslatableComponent("tooltip.itemsdontbreak.item_broken");
                    br.withStyle(ChatFormatting.RED, ChatFormatting.BOLD, ChatFormatting.ITALIC);
                    event.getToolTip().add(insert, br);
                }
                else //if (event.getFlags() == ITooltipFlag.TooltipFlags.ADVANCED)
                {
                    boolean indent = false;
                    int insert = tips.size();
                    for(int i=0;i<tips.size();i++)
                    {
                        Component t = tips.get(i);
                        if (t instanceof TranslatableComponent tt)
                        {
                            if ("item.durability".equals(tt.getKey()))
                            {
                                insert = i+1;
                                indent = true;
                                break;
                            }
                            else if ("item.modifiers.mainhand".equals(tt.getKey()))
                            {
                                insert = Math.min(insert, i);
                            }
                            else if ("item.modifiers.offhand".equals(tt.getKey()))
                            {
                                insert = Math.min(insert, i);
                            }
                        }
                    }

                    int remaining = stack.getMaxDamage() - stack.getDamageValue();

                    MutableComponent uses = new TranslatableComponent("tooltip.itemsdontbreak.item_info", adjustedDurability(stack, remaining));
                    uses.withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY);

                    if (indent)
                    {
                        TextComponent ts = new TextComponent(" ");
                        ts.append(uses);
                        uses = ts;
                    }
                    event.getToolTip().add(insert, uses);
                }
            }
        }
    }
}
