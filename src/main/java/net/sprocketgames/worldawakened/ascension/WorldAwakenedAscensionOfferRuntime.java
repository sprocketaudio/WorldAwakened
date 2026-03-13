package net.sprocketgames.worldawakened.ascension;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record WorldAwakenedAscensionOfferRuntime(
        String instanceId,
        ResourceLocation offerId,
        String sourceKey,
        long grantedAtMillis,
        List<ResourceLocation> candidateRewards,
        Optional<ResourceLocation> chosenRewardId,
        OptionalLong resolvedAtMillis) {
    private static final String INSTANCE_PREFIX = "wao_";
    private static final int TOKEN_LENGTH = 8;
    private static final String KEY_INSTANCE_ID = "instance_id";
    private static final String KEY_OFFER_ID = "offer_id";
    private static final String KEY_SOURCE_KEY = "source_key";
    private static final String KEY_GRANTED_AT = "granted_at";
    private static final String KEY_CANDIDATE_REWARDS = "candidate_rewards";
    private static final String KEY_CHOSEN_REWARD = "chosen_reward";
    private static final String KEY_RESOLVED_AT = "resolved_at";

    public WorldAwakenedAscensionOfferRuntime {
        candidateRewards = List.copyOf(candidateRewards);
    }

    public WorldAwakenedAscensionOfferRuntime withInstanceId(String newInstanceId) {
        return new WorldAwakenedAscensionOfferRuntime(
                newInstanceId,
                offerId,
                sourceKey,
                grantedAtMillis,
                candidateRewards,
                chosenRewardId,
                resolvedAtMillis);
    }

    public static WorldAwakenedAscensionOfferRuntime pending(
            String instanceId,
            ResourceLocation offerId,
            String sourceKey,
            long grantedAtMillis,
            List<ResourceLocation> candidateRewards) {
        return new WorldAwakenedAscensionOfferRuntime(
                instanceId,
                offerId,
                sourceKey,
                grantedAtMillis,
                candidateRewards,
                Optional.empty(),
                OptionalLong.empty());
    }

    public WorldAwakenedAscensionOfferRuntime resolve(ResourceLocation chosenRewardId, long resolvedAtMillis) {
        return new WorldAwakenedAscensionOfferRuntime(
                instanceId,
                offerId,
                sourceKey,
                grantedAtMillis,
                candidateRewards,
                Optional.of(chosenRewardId),
                OptionalLong.of(resolvedAtMillis));
    }

    public boolean resolved() {
        return chosenRewardId.isPresent();
    }

    public static boolean isOpaqueInstanceId(String instanceId) {
        if (instanceId == null || !instanceId.startsWith(INSTANCE_PREFIX) || instanceId.length() != INSTANCE_PREFIX.length() + TOKEN_LENGTH) {
            return false;
        }
        for (int index = INSTANCE_PREFIX.length(); index < instanceId.length(); index++) {
            char character = instanceId.charAt(index);
            if ((character < '0' || character > '9') && (character < 'a' || character > 'z')) {
                return false;
            }
        }
        return true;
    }

    public static String randomOpaqueInstanceId() {
        UUID uuid = UUID.randomUUID();
        long value = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        return INSTANCE_PREFIX + compactToken(value);
    }

    public static String stableOpaqueInstanceId(String seed) {
        return stableOpaqueInstanceId(seed, 0);
    }

    public static String stableOpaqueInstanceId(String seed, int salt) {
        String saltedSeed = salt <= 0 ? seed : seed + "#" + salt;
        return INSTANCE_PREFIX + compactToken(fnv1a64(saltedSeed));
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_INSTANCE_ID, instanceId);
        tag.putString(KEY_OFFER_ID, offerId.toString());
        tag.putString(KEY_SOURCE_KEY, sourceKey);
        tag.putLong(KEY_GRANTED_AT, grantedAtMillis);
        ListTag candidates = new ListTag();
        candidateRewards.stream()
                .map(ResourceLocation::toString)
                .sorted()
                .forEach(value -> candidates.add(StringTag.valueOf(value)));
        tag.put(KEY_CANDIDATE_REWARDS, candidates);
        chosenRewardId.ifPresent(rewardId -> tag.putString(KEY_CHOSEN_REWARD, rewardId.toString()));
        resolvedAtMillis.ifPresent(value -> tag.putLong(KEY_RESOLVED_AT, value));
        return tag;
    }

    public static Optional<WorldAwakenedAscensionOfferRuntime> fromTag(CompoundTag tag) {
        String instanceId = tag.getString(KEY_INSTANCE_ID);
        if (instanceId.isBlank()) {
            return Optional.empty();
        }

        ResourceLocation offerId = ResourceLocation.tryParse(tag.getString(KEY_OFFER_ID));
        if (offerId == null) {
            return Optional.empty();
        }

        String sourceKey = tag.getString(KEY_SOURCE_KEY);
        if (sourceKey.isBlank()) {
            sourceKey = offerId.toString();
        }

        long grantedAt = tag.getLong(KEY_GRANTED_AT);
        List<ResourceLocation> candidateRewards = new ArrayList<>();
        ListTag candidates = tag.getList(KEY_CANDIDATE_REWARDS, Tag.TAG_STRING);
        for (Tag candidate : candidates) {
            ResourceLocation candidateId = ResourceLocation.tryParse(candidate.getAsString());
            if (candidateId != null) {
                candidateRewards.add(candidateId);
            }
        }

        Optional<ResourceLocation> chosenRewardId = Optional.empty();
        if (tag.contains(KEY_CHOSEN_REWARD, Tag.TAG_STRING)) {
            ResourceLocation chosen = ResourceLocation.tryParse(tag.getString(KEY_CHOSEN_REWARD));
            if (chosen != null) {
                chosenRewardId = Optional.of(chosen);
            }
        }

        OptionalLong resolvedAt = OptionalLong.empty();
        if (tag.contains(KEY_RESOLVED_AT, Tag.TAG_LONG)) {
            resolvedAt = OptionalLong.of(tag.getLong(KEY_RESOLVED_AT));
        }

        return Optional.of(new WorldAwakenedAscensionOfferRuntime(
                instanceId,
                offerId,
                sourceKey,
                grantedAt,
                candidateRewards,
                chosenRewardId,
                resolvedAt));
    }

    private static long fnv1a64(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static String compactToken(long value) {
        String token = Long.toUnsignedString(value, 36).toLowerCase(Locale.ROOT);
        if (token.length() > TOKEN_LENGTH) {
            token = token.substring(token.length() - TOKEN_LENGTH);
        }
        return "0".repeat(Math.max(0, TOKEN_LENGTH - token.length())) + token;
    }
}
