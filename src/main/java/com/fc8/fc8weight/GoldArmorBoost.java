package com.fc8.fc8weight;

import java.util.UUID;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 金防具の防御力引き上げ (2026-07-11 author指定: 「金装備を合わせたい→防御を引き上げ」)。
 *
 * バニラ金防具は 兜2/胸5/脚3/靴1 = 合計11 で実用外。鉄(2/6/5/2=15)と同等まで
 * ADDITION modifier を各部位へ加算し、高防御セット(合計36帯)に混ぜられる実用枠にする:
 *   兜 +1(→3) / 胸 +1(→6) / 脚 +2(→5) / 靴 +1(→2)  = 合計15(鉄パリティ)
 * エンチャント性の高さは金の既存長所のまま。靭性/ノックバックは変更しない。
 *
 * 実装: ItemAttributeModifierEvent(fc8weightが既にvanilla sword anchorで使用中の経路)。
 * 各部位ごとに固定UUID(vanillaの部位別modifierと衝突しない自前値)でADDITION。
 * クライアント側でも同じ計算が走るためツールチップ表示も一致する(fc8weightは両側配備)。
 */
@Mod.EventBusSubscriber(modid = Fc8Weight.MODID)
public final class GoldArmorBoost {

    private static final UUID HEAD_UUID = UUID.fromString("a1b2c3d4-0011-4000-8000-000000000011");
    private static final UUID CHEST_UUID = UUID.fromString("a1b2c3d4-0012-4000-8000-000000000012");
    private static final UUID LEGS_UUID = UUID.fromString("a1b2c3d4-0013-4000-8000-000000000013");
    private static final UUID FEET_UUID = UUID.fromString("a1b2c3d4-0014-4000-8000-000000000014");

    private GoldArmorBoost() {
    }

    @SubscribeEvent
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        if (!(event.getItemStack().getItem() instanceof ArmorItem armor)) {
            return;
        }
        if (armor.getMaterial() != ArmorMaterials.GOLD) {
            return;
        }
        EquipmentSlot slot = armor.getEquipmentSlot();
        if (event.getSlotType() != slot) {
            return;
        }
        double bonus;
        UUID uuid;
        switch (slot) {
            case HEAD -> { bonus = 1.0D; uuid = HEAD_UUID; }
            case CHEST -> { bonus = 1.0D; uuid = CHEST_UUID; }
            case LEGS -> { bonus = 2.0D; uuid = LEGS_UUID; }
            case FEET -> { bonus = 1.0D; uuid = FEET_UUID; }
            default -> { return; }
        }
        event.addModifier(Attributes.ARMOR, new AttributeModifier(
                uuid, "fc8weight gold armor boost", bonus, AttributeModifier.Operation.ADDITION));
    }
}
