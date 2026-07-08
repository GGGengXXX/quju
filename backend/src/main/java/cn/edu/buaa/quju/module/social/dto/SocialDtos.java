package cn.edu.buaa.quju.module.social.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public final class SocialDtos {
    private SocialDtos() {}

    // ---- 好友申请 ----
    public record SendFriendRequestReq(
            @NotNull Long toUserId,
            String source,
            String message) {}

    public record FriendRequestVO(
            Long id, Long fromUserId, String fromNickname, String fromAvatar,
            Long toUserId, String status, String source, String message,
            LocalDateTime createdAt) {}

    // ---- 好友 ----
    public record UpdateFriendReq(String remark, String groupTag) {}

    public record FriendVO(
            Long userId, String nickname, String avatar, String userType,
            String remark, String groupTag, LocalDateTime friendSince) {}

    // ---- 关注 ----
    public record FollowVO(
            Long userId, String nickname, String avatar, LocalDateTime followedAt) {}

    // ---- 黑名单 ----
    public record BlockVO(
            Long userId, String nickname, String avatar, LocalDateTime blockedAt) {}

    // ---- IM 消息 ----
    public record SendMessageReq(
            @NotBlank String scope,
            @NotNull Long peerId,
            @NotBlank String contentType,
            @NotBlank String content) {}

    public record ForwardMessageReq(
            @NotBlank String scope,
            @NotNull Long peerId) {}

    public record MarkReadReq(
            @NotBlank String scope,
            @NotNull Long peerId) {}

    public record GenerateAiReplyReq(
            @NotBlank String scope,
            @NotNull Long peerId) {}

    public record AiReplySuggestionVO(
            String suggestion,
            Integer contextCount) {}

    public record MessageVO(
            Long id, String scope, Long senderId, Long receiverId, Long teamId,
            String contentType, String content, Boolean isRead, Boolean isRecalled,
            Long forwardedFromId, LocalDateTime createdAt, Integer readCount) {}
}
