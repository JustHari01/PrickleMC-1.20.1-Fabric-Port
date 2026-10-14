package net.darkhax.pricklemc.common.impl.config.property;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CodecPropertyTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void passthroughAdapterPreservesStructuredJson() {
        final JsonElement expected = JsonParser.parseString("{\"nested\":[1,true,{\"text\":\"kept\"}]}");
        final Codec<JsonElement> codec = CodecProperty.jsonCodec(Function.identity(), Function.identity());

        assertEquals(expected, roundTrip(codec, expected));
    }

    @Test
    void textComponentCodecPreservesComponentJson() {
        final Component expected = Component.Serializer.fromJson("{\"text\":\"Prickle\",\"bold\":true}");

        assertNotNull(expected);
        assertEquals(expected, roundTrip(CodecProperty.COMPONENT_CODEC, expected));
    }

    @Test
    void textStyleCodecPreservesCommonFormatting() {
        final Style expected = Style.EMPTY.withColor(ChatFormatting.RED).withBold(true);
        final Style decoded = roundTrip(CodecProperty.STYLE_CODEC, expected);

        assertEquals(ChatFormatting.RED.getColor(), decoded.getColor().getValue());
        assertEquals(Boolean.TRUE, decoded.isBold());
    }

    @Test
    void attributeModifierCodecReadsRenamedMinecraftOperations() {
        final UUID id = UUID.fromString("d6a21677-8a9d-4fc4-9ea4-3af6c7f44f10");
        final JsonObject input = JsonParser.parseString("""
                {"id":"d6a21677-8a9d-4fc4-9ea4-3af6c7f44f10","name":"port-test","amount":2.5,"operation":"add_multiplied_base"}
                """).getAsJsonObject();
        final AttributeModifier decoded = decode(CodecProperty.ATTRIBUTE_MODIFIER_CODEC, input);

        assertEquals(id, decoded.getId());
        assertEquals("port-test", decoded.getName());
        assertEquals(2.5, decoded.getAmount());
        assertEquals(AttributeModifier.Operation.MULTIPLY_BASE, decoded.getOperation());
        assertEquals(decoded, roundTrip(CodecProperty.ATTRIBUTE_MODIFIER_CODEC, decoded));
    }

    @Test
    void effectCodecPreservesHiddenEffectChain() {
        final MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation("minecraft", "speed"));
        final MobEffectInstance hidden = new MobEffectInstance(effect, 120, 1, true, false, false);
        final MobEffectInstance expected = new MobEffectInstance(effect, 240, 2, false, true, true, hidden, java.util.Optional.empty());

        final JsonElement encoded = encode(CodecProperty.EFFECT_INSTANCE_CODEC, expected);
        assertEquals("minecraft:speed", encoded.getAsJsonObject().getAsJsonObject("hidden_effect").get("id").getAsString());
        final MobEffectInstance decoded = decode(CodecProperty.EFFECT_INSTANCE_CODEC, encoded);
        assertEquals(expected.getDuration(), decoded.getDuration());
        assertEquals(expected.getAmplifier(), decoded.getAmplifier());
        assertEquals(expected.isAmbient(), decoded.isAmbient());
        assertEquals(expected.isVisible(), decoded.isVisible());
        assertEquals(expected.showIcon(), decoded.showIcon());
        assertEquals(120, decoded.save(new net.minecraft.nbt.CompoundTag()).getCompound("HiddenEffect").getInt("Duration"));
    }

    @Test
    void itemStackAndIngredientAdaptersWorkOnMinecraft1201() {
        final ItemStack expected = new ItemStack(Items.STONE, 3);
        final ItemStack decodedStack = roundTrip(ItemStack.CODEC, expected);
        assertEquals(expected.getItem(), decodedStack.getItem());
        assertEquals(expected.getCount(), decodedStack.getCount());

        final Ingredient ingredient = decode(CodecProperty.INGREDIENT_CODEC,
                JsonParser.parseString("{\"item\":\"minecraft:stone\"}"));
        assertEquals(Items.STONE, ingredient.getItems()[0].getItem());
        assertEquals(ingredient.getItems()[0].getItem(), roundTrip(CodecProperty.INGREDIENT_CODEC, ingredient).getItems()[0].getItem());
    }

    private static <T> T roundTrip(Codec<T> codec, T value) {
        return decode(codec, encode(codec, value));
    }

    private static <T> JsonElement encode(Codec<T> codec, T value) {
        return codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });
    }

    private static <T> T decode(Codec<T> codec, JsonElement value) {
        return codec.parse(JsonOps.INSTANCE, value).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });
    }
}
