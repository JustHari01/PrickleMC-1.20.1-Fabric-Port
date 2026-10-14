package net.darkhax.pricklemc.common.impl.config.property;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.darkhax.pricklemc.common.api.annotations.Value;
import net.darkhax.pricklemc.common.api.config.PropertyResolver;
import net.darkhax.pricklemc.common.api.config.comment.IComment;
import net.darkhax.pricklemc.common.api.config.property.IPropertyAdapter;
import net.darkhax.pricklemc.common.api.config.property.ObjectProperty;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.nio.charset.StandardCharsets;

/**
 * A config property where the value is serialized using a Minecraft Codec.
 *
 * @param <T> The type of value serialized by the codec.
 */
public class CodecProperty<T> extends ObjectProperty<T> {

    /**
     * Builtin support for resource locations using Mojang's codec.
     */
    public static final Adapter<ResourceLocation> RESOURCE_LOCATION = of(ResourceLocation.class, ResourceLocation.CODEC);

    /**
     * Builtin support for block positions using Mojang's codec.
     */
    public static final Adapter<BlockPos> BLOCK_POS = of(BlockPos.class, BlockPos.CODEC);

    private static final Gson STYLE_GSON = new GsonBuilder()
            .registerTypeAdapter(Style.class, new Style.Serializer())
            .create();

    static final Codec<Component> COMPONENT_CODEC = jsonCodec(
            CodecProperty::readComponent,
            Component.Serializer::toJsonTree);
    static final Codec<Style> STYLE_CODEC = jsonCodec(
            json -> STYLE_GSON.fromJson(json, Style.class),
            style -> STYLE_GSON.toJsonTree(style, Style.class));
    static final Codec<MobEffectInstance> EFFECT_INSTANCE_CODEC = jsonCodec(
            CodecProperty::readEffectInstance,
            CodecProperty::writeEffectInstance);
    static final Codec<AttributeModifier> ATTRIBUTE_MODIFIER_CODEC = jsonCodec(
            CodecProperty::readAttributeModifier,
            CodecProperty::writeAttributeModifier);
    static final Codec<Ingredient> INGREDIENT_CODEC = jsonCodec(
            Ingredient::fromJson,
            Ingredient::toJson);

    /**
     * Builtin support for text components using Mojang's codec.
     */
    public static final Adapter<Component> TEXT_COMPONENT = of(Component.class, COMPONENT_CODEC);

    /**
     * Builtin support for text styles using Mojang's codec.
     */
    public static final Adapter<Style> TEXT_STYLE = of(Style.class, STYLE_CODEC);

    /**
     * Builtin support for status effects using Mojang's codec.
     */
    public static final Adapter<MobEffectInstance> EFFECT_INSTANCE = of(MobEffectInstance.class, EFFECT_INSTANCE_CODEC);

    /**
     * Builtin support for attribute modifiers using Mojang's codec.
     */
    public static final Adapter<AttributeModifier> ATTRIBUTE_MODIFIER = of(AttributeModifier.class, ATTRIBUTE_MODIFIER_CODEC);

    /**
     * Builtin support for item stacks using Mojang's codec.
     */
    public static final Adapter<ItemStack> ITEM_STACK = of(ItemStack.class, ItemStack.CODEC);

    /**
     * Builtin support for ingredients using Mojang's codec.
     */
    public static final Adapter<Ingredient> INGREDIENT = of(Ingredient.class, INGREDIENT_CODEC);

    /**
     * Minecraft 1.20.1 does not expose codecs for every Minecraft value that
     * newer versions support. This adapter keeps the same JSON-based API while
     * using each value's 1.20.1 serializer or stable data fields.
     */
    static <T> Codec<T> jsonCodec(Function<JsonElement, T> decoder, Function<T, JsonElement> encoder) {
        return Codec.PASSTHROUGH.comapFlatMap(dynamic -> {
            try {
                return DataResult.success(decoder.apply(dynamic.convert(JsonOps.INSTANCE).getValue()));
            }
            catch (RuntimeException exception) {
                return DataResult.error(exception::getMessage);
            }
        }, value -> new Dynamic<>(JsonOps.INSTANCE, encoder.apply(value)));
    }

    private static Component readComponent(JsonElement json) {
        final Component component = Component.Serializer.fromJson(json);
        if (component == null) {
            throw new IllegalArgumentException("Invalid text component JSON");
        }
        return component;
    }

    private static MobEffectInstance readEffectInstance(JsonElement json) {
        final JsonObject object = requireObject(json, "status effect");
        final ResourceLocation id = ResourceLocation.tryParse(stringValue(object, "id", "Id"));
        if (id == null) {
            throw new IllegalArgumentException("Status effect id is missing or invalid");
        }
        final var effect = BuiltInRegistries.MOB_EFFECT.getOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown status effect: " + id));
        final int duration = intValue(object, 0, "duration", "Duration");
        final int amplifier = intValue(object, 0, "amplifier", "Amplifier");
        final boolean ambient = booleanValue(object, false, "ambient", "Ambient");
        final boolean visible = booleanValue(object, true, "show_particles", "ShowParticles");
        final boolean showIcon = booleanValue(object, true, "show_icon", "ShowIcon");
        final MobEffectInstance hidden = object.has("hidden_effect")
                ? readEffectInstance(object.get("hidden_effect"))
                : null;
        return new MobEffectInstance(effect, duration, amplifier, ambient, visible, showIcon, hidden, Optional.empty());
    }

    private static JsonElement writeEffectInstance(MobEffectInstance instance) {
        final JsonObject object = new JsonObject();
        object.addProperty("id", BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect()).toString());
        object.addProperty("duration", instance.getDuration());
        object.addProperty("amplifier", instance.getAmplifier());
        object.addProperty("ambient", instance.isAmbient());
        object.addProperty("show_particles", instance.isVisible());
        object.addProperty("show_icon", instance.showIcon());
        final CompoundTag saved = instance.save(new CompoundTag());
        if (saved.contains("HiddenEffect", CompoundTag.TAG_COMPOUND)) {
            object.add("hidden_effect", writeEffectTag(saved.getCompound("HiddenEffect")));
        }
        return object;
    }

    private static JsonObject writeEffectTag(CompoundTag saved) {
        final int numericId = saved.getByte("Id") & 0xFF;
        final var effect = BuiltInRegistries.MOB_EFFECT.byId(numericId);
        if (effect == null) {
            throw new IllegalArgumentException("Unknown status effect registry id: " + numericId);
        }
        final JsonObject object = new JsonObject();
        object.addProperty("id", BuiltInRegistries.MOB_EFFECT.getKey(effect).toString());
        object.addProperty("duration", saved.getInt("Duration"));
        object.addProperty("amplifier", saved.getByte("Amplifier"));
        object.addProperty("ambient", saved.getBoolean("Ambient"));
        object.addProperty("show_particles", saved.getBoolean("ShowParticles"));
        object.addProperty("show_icon", saved.getBoolean("ShowIcon"));
        if (saved.contains("HiddenEffect", CompoundTag.TAG_COMPOUND)) {
            object.add("hidden_effect", writeEffectTag(saved.getCompound("HiddenEffect")));
        }
        return object;
    }

    private static AttributeModifier readAttributeModifier(JsonElement json) {
        final JsonObject object = requireObject(json, "attribute modifier");
        final String id = stringValue(object, "id", "uuid", "UUID");
        final String name = optionalStringValue(object, id, "name", "Name");
        final double amount = doubleValue(object, 0, "amount", "Amount");
        final String operation = stringValue(object, "operation", "Operation").toUpperCase(Locale.ROOT);
        final String legacyOperation = switch (operation) {
            case "ADD_VALUE" -> "ADDITION";
            case "ADD_MULTIPLIED_BASE" -> "MULTIPLY_BASE";
            case "ADD_MULTIPLIED_TOTAL" -> "MULTIPLY_TOTAL";
            default -> operation;
        };
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        }
        catch (IllegalArgumentException notUuid) {
            uuid = UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
        }
        return new AttributeModifier(uuid, name, amount, AttributeModifier.Operation.valueOf(legacyOperation));
    }

    private static JsonElement writeAttributeModifier(AttributeModifier modifier) {
        final JsonObject object = new JsonObject();
        object.addProperty("id", modifier.getId().toString());
        object.addProperty("name", modifier.getName());
        object.addProperty("amount", modifier.getAmount());
        object.addProperty("operation", modifier.getOperation().name().toLowerCase(Locale.ROOT));
        return object;
    }

    private static JsonObject requireObject(JsonElement json, String type) {
        if (json == null || !json.isJsonObject()) {
            throw new IllegalArgumentException("Expected an object for " + type);
        }
        return json.getAsJsonObject();
    }

    private static String stringValue(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && !object.get(key).isJsonNull()) {
                return object.get(key).getAsString();
            }
        }
        throw new IllegalArgumentException("Missing required property: " + String.join(" or ", keys));
    }

    private static String optionalStringValue(JsonObject object, String fallback, String... keys) {
        for (String key : keys) {
            if (object.has(key) && !object.get(key).isJsonNull()) {
                return object.get(key).getAsString();
            }
        }
        return fallback;
    }

    private static int intValue(JsonObject object, int fallback, String... keys) {
        for (String key : keys) {
            if (object.has(key) && !object.get(key).isJsonNull()) {
                return object.get(key).getAsInt();
            }
        }
        return fallback;
    }

    private static double doubleValue(JsonObject object, double fallback, String... keys) {
        for (String key : keys) {
            if (object.has(key) && !object.get(key).isJsonNull()) {
                return object.get(key).getAsDouble();
            }
        }
        return fallback;
    }

    private static boolean booleanValue(JsonObject object, boolean fallback, String... keys) {
        for (String key : keys) {
            if (object.has(key) && !object.get(key).isJsonNull()) {
                return object.get(key).getAsBoolean();
            }
        }
        return fallback;
    }

    private final Codec<T> codec;

    public CodecProperty(Field field, Object parent, T defaultValue, Value valueMeta, IComment comment, Codec<T> codec) {
        super(field, parent, defaultValue, valueMeta, comment);
        this.codec = codec;
    }

    @Override
    public void writeValue(T value, JsonWriter writer, PropertyResolver resolver, Logger logger) throws IOException {
        final JsonElement json = this.codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow(false, logger::error);
        Streams.write(json, writer);
    }

    @Override
    public T readValue(JsonReader reader, PropertyResolver resolver, Logger logger) throws IOException {
        final JsonElement json = Streams.parse(reader);
        return this.codec.decode(JsonOps.INSTANCE, json).getOrThrow(false, logger::error).getFirst();
    }

    /**
     * Creates an adapter that will process a given type using a codec.
     *
     * @param classType The type to serialize using the codec.
     * @param codec     The codec to serialize the value.
     * @param <T>       The type being adapted.
     * @return An adapter that uses a codec to serialize data.
     */
    public static <T> Adapter<T> of(Type classType, Codec<T> codec) {
        return new Adapter<>(classType, codec);
    }

    /**
     * A type adapter that will use a Codec to serialize a given type.
     *
     * @param <T> The type to adapt and serialize.
     */
    public static class Adapter<T> implements IPropertyAdapter<CodecProperty<T>> {

        private final Type type;
        private final Codec<T> codec;

        private Adapter(Type type, Codec<T> codec) {
            this.type = type;
            this.codec = codec;
        }

        @Override
        public @Nullable CodecProperty<T> toValue(PropertyResolver resolver, Field field, Object parent, @Nullable Object value, Value valueMeta) throws IOException {
            if (field.getGenericType().equals(this.type)) {
                return new CodecProperty<>(field, parent, (T) value, valueMeta, resolver.toComment(field, value, valueMeta), codec);
            }
            return null;
        }
    }
}
