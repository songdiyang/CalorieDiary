package com.calorie.diary.data.supabase

import com.calorie.diary.data.db.CachedFriendEntity
import com.calorie.diary.data.db.CachedFriendMessageEntity
import com.calorie.diary.data.db.CachedGroupEntity
import com.calorie.diary.data.db.CachedGroupMessageEntity
import org.json.JSONArray
import org.json.JSONObject

internal fun sharedFoodsToJson(items: List<SharedFood>): String {
    val array = JSONArray()
    items.forEach { food ->
        array.put(
            JSONObject()
                .put("name", food.name)
                .put("emoji", food.emoji)
                .put("grams", food.grams)
                .put("calories", food.calories)
                .put("meal", food.meal)
        )
    }
    return array.toString()
}

internal fun sharedFoodsFromJson(raw: String): List<SharedFood> {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { food ->
                SharedFood(
                    name = food.optString("name"),
                    emoji = food.optString("emoji"),
                    grams = food.optInt("grams"),
                    calories = food.optInt("calories"),
                    meal = food.optInt("meal")
                )
            }
        }
    }.getOrDefault(emptyList())
}

internal fun FriendEntry.toCacheEntity(ownerId: String) = CachedFriendEntity(
    ownerId = ownerId,
    userId = userId,
    nickname = nickname,
    avatarEmoji = avatarEmoji,
    friendsSince = friendsSince,
    lastMessage = lastMessage,
    lastMessageAt = lastMessageAt,
    publicId = publicId,
    sparkCount = sparkCount
)

internal fun CachedFriendEntity.toFriendEntry() = FriendEntry(
    userId = userId,
    nickname = nickname,
    avatarEmoji = avatarEmoji,
    friendsSince = friendsSince,
    lastMessage = lastMessage,
    lastMessageAt = lastMessageAt,
    publicId = publicId,
    sparkCount = sparkCount
)

internal fun FriendMessage.toCacheEntity(ownerId: String, friendId: String) =
    CachedFriendMessageEntity(
        ownerId = ownerId,
        messageId = id,
        friendId = friendId,
        senderId = senderId,
        content = content,
        messageType = messageType,
        calories = calories,
        entryDate = entryDate,
        itemsJson = sharedFoodsToJson(items),
        createdAt = createdAt
    )

internal fun CachedFriendMessageEntity.toFriendMessage() = FriendMessage(
    id = messageId,
    senderId = senderId,
    content = content,
    createdAt = createdAt,
    messageType = messageType,
    calories = calories,
    entryDate = entryDate,
    items = sharedFoodsFromJson(itemsJson)
)

internal fun GroupConversation.toCacheEntity(ownerId: String) = CachedGroupEntity(
    ownerId = ownerId,
    groupId = groupId,
    name = name,
    groupOwnerId = this.ownerId,
    calorieGoal = calorieGoal,
    createdAt = createdAt,
    lastMessage = lastMessage,
    lastMessageAt = lastMessageAt,
    sparkDays = sparkDays
)

internal fun CachedGroupEntity.toGroupConversation() = GroupConversation(
    groupId = groupId,
    name = name,
    ownerId = groupOwnerId,
    calorieGoal = calorieGoal,
    createdAt = createdAt,
    lastMessage = lastMessage,
    lastMessageAt = lastMessageAt,
    sparkDays = sparkDays
)

internal fun GroupMessage.toCacheEntity(ownerId: String, groupId: String) =
    CachedGroupMessageEntity(
        ownerId = ownerId,
        messageId = id,
        groupId = groupId,
        senderId = senderId,
        content = content,
        messageType = messageType,
        calories = calories,
        entryDate = entryDate,
        itemsJson = sharedFoodsToJson(items),
        createdAt = createdAt
    )

internal fun CachedGroupMessageEntity.toGroupMessage() = GroupMessage(
    id = messageId,
    senderId = senderId,
    content = content,
    messageType = messageType,
    calories = calories,
    entryDate = entryDate,
    createdAt = createdAt,
    items = sharedFoodsFromJson(itemsJson)
)