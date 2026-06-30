package cn.edu.buaa.quju.module.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class TeamDtos {
    private TeamDtos() {}

    public record PageResult<T>(long total, int page, int size, List<T> list) {}

    public record UserBrief(Long id, String nickname, String avatar, String userType, String status) {}

    public record TeamSummary(
            Long id,
            String name,
            String intro,
            String avatar,
            List<String> tags,
            String joinType,
            Integer capacity,
            Integer memberCount,
            String status,
            UserBrief owner,
            String myRole,
            Boolean joined,
            LocalDateTime createdAt
    ) {}

    public record TeamDetail(
            Long id,
            String name,
            String intro,
            String avatar,
            List<String> tags,
            String joinType,
            Integer capacity,
            Integer memberCount,
            String status,
            UserBrief owner,
            String myRole,
            Boolean joined,
            LocalDateTime createdAt
    ) {}

    public record TeamJoinResult(String status, Long requestId) {}

    public record TeamMemberItem(
            Long userId,
            String nickname,
            String avatar,
            String userType,
            String status,
            String role,
            Integer points,
            LocalDateTime joinedAt
    ) {}

    public record TeamJoinRequestItem(
            Long id,
            Long userId,
            String nickname,
            String avatar,
            String status,
            LocalDateTime createdAt,
            LocalDateTime handledAt,
            Long handlerId
    ) {}

    public record TeamAnnouncementItem(Long id, Long authorId, String authorName, String content, LocalDateTime createdAt) {}

    public record TeamVoteItem(
            Long id,
            String title,
            List<String> options,
            List<Integer> counts,
            Boolean multiChoice,
            LocalDateTime deadline,
            LocalDateTime createdAt,
            Long creatorId,
            String creatorName,
            List<Integer> myOptionIndexes
    ) {}

    public record TeamFileItem(Long id, Long uploaderId, String uploaderName, String fileName, String fileUrl, Long fileSize, LocalDateTime createdAt) {}

    public record TeamAlbumPhotoItem(Long id, Long uploaderId, String uploaderName, String imageUrl, LocalDateTime createdAt) {}

    public record TeamImageUploadItem(String url, String fileName, Long fileSize) {}

    public record TeamFileUploadItem(String url, String fileName, Long fileSize) {}

    public record TeamMomentItem(
            Long id,
            Long authorId,
            String authorName,
            String authorAvatar,
            String content,
            List<String> images,
            Boolean featured,
            LocalDateTime createdAt
    ) {}

    public record TeamPointItem(Long userId, String nickname, String avatar, Integer points, Integer rank) {}

    public record ActivityItem(
            Long id,
            String name,
            String intro,
            String category,
            String coverImage,
            LocalDateTime startTime,
            LocalDateTime endTime,
            LocalDateTime signupDeadline,
            String city,
            String address,
            Double lng,
            Double lat,
            Integer capacity,
            Double fee,
            String status,
            String phase,
            Integer signupCount,
            UserBrief creator,
            Long teamId
    ) {}

    public record TeamUpsertRequest(
            @NotBlank @Size(max = 64) String name,
            @Size(max = 500) String intro,
            String avatar,
            List<@NotBlank @Size(max = 32) String> tags,
            @NotNull String joinType,
            Integer capacity
    ) {}

    public record HandleJoinRequest(@NotBlank String action) {}
    public record RoleUpdateRequest(@NotBlank String role) {}
    public record AnnouncementCreateRequest(@NotBlank String content) {}

    public record VoteCreateRequest(
            @NotBlank @Size(max = 140) String title,
            @NotEmpty List<@NotBlank String> options,
            Boolean multiChoice,
            LocalDateTime deadline
    ) {}

    public record VoteCastRequest(@NotEmpty List<Integer> optionIndexes) {}

    public record FileCreateRequest(
            @NotBlank String fileName,
            @NotBlank String fileUrl,
            Long fileSize
    ) {}

    public record AlbumCreateRequest(@NotEmpty List<@NotBlank String> imageUrls) {}

    public record MomentCreateRequest(
            String content,
            List<@NotBlank String> images
    ) {}
}
