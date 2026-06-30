package cn.edu.buaa.quju.module.team.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import cn.edu.buaa.quju.module.team.dto.TeamDtos.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TeamService {
    private static final int CODE_TEAM_FULL = 5000;
    private static final int CODE_TEAM_INACTIVE = 5001;
    private static final int CODE_ALREADY_MEMBER = 5002;
    private static final int CODE_NO_PERMISSION = 5004;
    private static final int CODE_OWNER_CANNOT_LEAVE = 5005;
    private static final int POST_MOMENT_POINTS = 2;
    private static final int FEATURED_MOMENT_POINTS = 5;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final ObjectMapper objectMapper;

    public TeamService(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedJdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = namedJdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public PageResult<TeamSummary> searchTeams(String keyword, String tag, int page, int size, Long viewerId) {
        int safePage = Math.max(page, 1);
        int safeSize = clampSize(size);
        String where = " from team t join user u on u.id = t.owner_id where t.deleted_at is null and t.status = 'ACTIVE'" +
                (hasText(keyword) ? " and t.name like :keyword" : "") +
                (hasText(tag) ? " and exists (select 1 from team_tag tt where tt.team_id = t.id and tt.tag = :tag)" : "");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("keyword", hasText(keyword) ? "%" + keyword.trim() + "%" : null)
                .addValue("tag", hasText(tag) ? tag.trim() : null)
                .addValue("limit", safeSize)
                .addValue("offset", (safePage - 1) * safeSize);
        Long total = namedJdbcTemplate.queryForObject("select count(*)" + where, params, Long.class);
        List<Map<String, Object>> rows = namedJdbcTemplate.queryForList(
                "select t.*, u.nickname as owner_nickname, u.avatar as owner_avatar, u.user_type as owner_user_type, u.status as owner_status" +
                        where + " order by t.created_at desc limit :limit offset :offset",
                params
        );
        List<Long> teamIds = rows.stream().map(r -> ((Number) r.get("id")).longValue()).toList();
        Map<Long, List<String>> tags = loadTags(teamIds);
        Map<Long, String> myRoles = viewerId == null || teamIds.isEmpty() ? Collections.emptyMap() : loadRoles(teamIds, viewerId);
        List<TeamSummary> list = rows.stream().map(row -> toTeamSummary(row, tags.getOrDefault(((Number) row.get("id")).longValue(), List.of()), myRoles.get(((Number) row.get("id")).longValue()))).toList();
        return new PageResult<>(total == null ? 0 : total, safePage, safeSize, list);
    }

    @Transactional
    public TeamDetail createTeam(long userId, TeamUpsertRequest request) {
        validateJoinType(request.joinType());
        int capacity = normalizeCapacity(request.capacity());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update("insert into team(name, intro, avatar, join_type, capacity, member_count, status, owner_id) values (:name, :intro, :avatar, :joinType, :capacity, 1, 'ACTIVE', :ownerId)",
                new MapSqlParameterSource()
                        .addValue("name", request.name().trim())
                        .addValue("intro", blankToNull(request.intro()))
                        .addValue("avatar", blankToNull(request.avatar()))
                        .addValue("joinType", request.joinType())
                        .addValue("capacity", capacity)
                        .addValue("ownerId", userId),
                keyHolder,
                new String[]{"id"}
        );
        long teamId = requireGeneratedId(keyHolder);
        replaceTags(teamId, request.tags());
        namedJdbcTemplate.update("insert into team_member(team_id, user_id, role, points) values (:teamId, :userId, 'OWNER', 0)",
                new MapSqlParameterSource().addValue("teamId", teamId).addValue("userId", userId));
        return getTeamDetail(teamId, userId);
    }

    public TeamDetail getTeamDetail(long teamId, Long viewerId) {
        Map<String, Object> row = findTeamRow(teamId);
        List<String> tags = loadTags(List.of(teamId)).getOrDefault(teamId, List.of());
        String myRole = viewerId == null ? null : loadRoles(List.of(teamId), viewerId).get(teamId);
        return toTeamDetail(row, tags, myRole);
    }

    @Transactional
    public void dissolveTeam(long teamId, long userId) {
        TeamMembership membership = requireMembershipRequired(teamId, userId);
        requireOwner(membership.role());
        jdbcTemplate.update("update team set status = 'DISSOLVED', updated_at = current_timestamp where id = ?", teamId);
    }

    @Transactional
    public TeamJoinResult joinTeam(long teamId, long userId) {
        Map<String, Object> team = findActiveTeam(teamId);
        long ownerId = ((Number) team.get("owner_id")).longValue();
        if (ownerId == userId || isMember(teamId, userId)) {
            throw teamError(CODE_ALREADY_MEMBER, "already_team_member");
        }
        if (isBlocked(userId, ownerId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        int memberCount = ((Number) team.get("member_count")).intValue();
        int capacity = ((Number) team.get("capacity")).intValue();
        if (memberCount >= capacity) {
            throw teamError(CODE_TEAM_FULL, "team_full");
        }
        String joinType = String.valueOf(team.get("join_type"));
        if ("PUBLIC".equals(joinType)) {
            addMember(teamId, userId, "MEMBER");
            return new TeamJoinResult("JOINED", null);
        }
        Long existingPending = namedJdbcTemplate.query(
                "select id from team_join_request where team_id = :teamId and user_id = :userId and status = 'PENDING' limit 1",
                new MapSqlParameterSource().addValue("teamId", teamId).addValue("userId", userId),
                rs -> rs.next() ? rs.getLong("id") : null
        );
        if (existingPending != null) {
            return new TeamJoinResult("PENDING", existingPending);
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update("insert into team_join_request(team_id, user_id, status) values (:teamId, :userId, 'PENDING')",
                new MapSqlParameterSource().addValue("teamId", teamId).addValue("userId", userId), keyHolder, new String[]{"id"});
        return new TeamJoinResult("PENDING", requireGeneratedId(keyHolder));
    }

    @Transactional
    public void leaveTeam(long teamId, long userId) {
        TeamMembership membership = requireMembershipRequired(teamId, userId);
        if ("OWNER".equals(membership.role())) {
            throw teamError(CODE_OWNER_CANNOT_LEAVE, "owner_cannot_leave");
        }
        jdbcTemplate.update("delete from team_member where team_id = ? and user_id = ?", teamId, userId);
        decrementMemberCount(teamId);
    }

    public List<TeamJoinRequestItem> listJoinRequests(long teamId, long userId) {
        requireManager(teamId, userId);
        return jdbcTemplate.query(
                "select r.id, r.user_id, u.nickname, u.avatar, r.status, r.created_at, r.handled_at, r.handler_id from team_join_request r join user u on u.id = r.user_id where r.team_id = ? order by r.created_at desc",
                this::mapJoinRequest,
                teamId
        );
    }

    @Transactional
    public void handleJoinRequest(long teamId, long reqId, long userId, HandleJoinRequest request) {
        requireManager(teamId, userId);
        String action = request.action().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVE", "REJECT").contains(action)) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        Map<String, Object> req = jdbcTemplate.query("select * from team_join_request where id = ? and team_id = ?", rs -> rs.next() ? rowMap(rs) : null, reqId, teamId);
        if (req == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!"PENDING".equals(req.get("status"))) {
            throw new BizException(ErrorCode.CONFLICT);
        }
        if ("APPROVE".equals(action)) {
            long applicantId = ((Number) req.get("user_id")).longValue();
            if (isMember(teamId, applicantId)) {
                jdbcTemplate.update("update team_join_request set status = 'APPROVED', handler_id = ?, handled_at = current_timestamp where id = ?", userId, reqId);
                return;
            }
            ensureTeamHasCapacity(teamId);
            addMember(teamId, applicantId, "MEMBER");
        }
        jdbcTemplate.update("update team_join_request set status = ?, handler_id = ?, handled_at = current_timestamp where id = ?",
                "APPROVE".equals(action) ? "APPROVED" : "REJECTED", userId, reqId);
    }

    public List<TeamMemberItem> listMembers(long teamId) {
        findTeamRow(teamId);
        return jdbcTemplate.query(
                "select tm.user_id, u.nickname, u.avatar, u.user_type, u.status, tm.role, tm.points, tm.joined_at from team_member tm join user u on u.id = tm.user_id where tm.team_id = ? order by field(tm.role, 'OWNER', 'ADMIN', 'MEMBER'), tm.joined_at asc",
                this::mapMember,
                teamId
        );
    }

    @Transactional
    public void updateMemberRole(long teamId, long targetUserId, long userId, RoleUpdateRequest request) {
        TeamMembership actor = requireMembershipRequired(teamId, userId);
        requireOwner(actor.role());
        TeamMembership target = requireMembershipRequired(teamId, targetUserId);
        if ("OWNER".equals(target.role())) {
            throw new BizException(ErrorCode.CONFLICT);
        }
        String role = request.role().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ADMIN", "MEMBER").contains(role)) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        jdbcTemplate.update("update team_member set role = ? where team_id = ? and user_id = ?", role, teamId, targetUserId);
    }

    @Transactional
    public void removeMember(long teamId, long targetUserId, long userId) {
        TeamMembership actor = requireMembershipRequired(teamId, userId);
        TeamMembership target = requireMembershipRequired(teamId, targetUserId);
        if ("OWNER".equals(target.role())) {
            throw teamError(CODE_NO_PERMISSION, "no_team_permission");
        }
        if ("ADMIN".equals(actor.role()) && !"MEMBER".equals(target.role())) {
            throw teamError(CODE_NO_PERMISSION, "no_team_permission");
        }
        if (!Set.of("OWNER", "ADMIN").contains(actor.role())) {
            throw teamError(CODE_NO_PERMISSION, "no_team_permission");
        }
        jdbcTemplate.update("delete from team_member where team_id = ? and user_id = ?", teamId, targetUserId);
        decrementMemberCount(teamId);
    }

    public List<TeamAnnouncementItem> listAnnouncements(long teamId, long userId) {
        requireMembershipRequired(teamId, userId);
        return jdbcTemplate.query(
                "select a.id, a.author_id, u.nickname as author_name, a.content, a.created_at from team_announcement a join user u on u.id = a.author_id where a.team_id = ? order by a.created_at desc",
                (rs, rowNum) -> new TeamAnnouncementItem(rs.getLong("id"), rs.getLong("author_id"), rs.getString("author_name"), rs.getString("content"), toDateTime(rs, "created_at")),
                teamId
        );
    }

    @Transactional
    public TeamAnnouncementItem createAnnouncement(long teamId, long userId, AnnouncementCreateRequest request) {
        requireManager(teamId, userId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update("insert into team_announcement(team_id, author_id, content) values (:teamId, :authorId, :content)",
                new MapSqlParameterSource().addValue("teamId", teamId).addValue("authorId", userId).addValue("content", request.content().trim()),
                keyHolder,
                new String[]{"id"});
        long id = requireGeneratedId(keyHolder);
        return jdbcTemplate.queryForObject(
                "select a.id, a.author_id, u.nickname as author_name, a.content, a.created_at from team_announcement a join user u on u.id = a.author_id where a.id = ?",
                (rs, rowNum) -> new TeamAnnouncementItem(rs.getLong("id"), rs.getLong("author_id"), rs.getString("author_name"), rs.getString("content"), toDateTime(rs, "created_at")),
                id
        );
    }

    public List<TeamVoteItem> listVotes(long teamId, long userId) {
        requireMembershipRequired(teamId, userId);
        List<Map<String, Object>> votes = jdbcTemplate.query("select v.*, u.nickname as creator_name from team_vote v join user u on u.id = v.creator_id where v.team_id = ? order by v.created_at desc", (rs, rowNum) -> rowMap(rs), teamId);
        if (votes.isEmpty()) {
            return List.of();
        }
        List<Long> voteIds = votes.stream().map(v -> ((Number) v.get("id")).longValue()).toList();
        Map<Long, Map<Integer, Integer>> countMap = loadVoteCounts(voteIds);
        Map<Long, List<Integer>> myVotes = loadUserVotes(voteIds, userId);
        return votes.stream().map(v -> toVoteItem(v, countMap.getOrDefault(((Number) v.get("id")).longValue(), Map.of()), myVotes.getOrDefault(((Number) v.get("id")).longValue(), List.of()))).toList();
    }

    @Transactional
    public TeamVoteItem createVote(long teamId, long userId, VoteCreateRequest request) {
        requireManager(teamId, userId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update("insert into team_vote(team_id, creator_id, title, options, multi_choice, deadline) values (:teamId, :creatorId, :title, :options, :multiChoice, :deadline)",
                new MapSqlParameterSource()
                        .addValue("teamId", teamId)
                        .addValue("creatorId", userId)
                        .addValue("title", request.title().trim())
                        .addValue("options", writeJson(request.options()))
                        .addValue("multiChoice", Boolean.TRUE.equals(request.multiChoice()))
                        .addValue("deadline", request.deadline()),
                keyHolder,
                new String[]{"id"});
        long voteId = requireGeneratedId(keyHolder);
        Map<String, Object> vote = jdbcTemplate.query("select v.*, u.nickname as creator_name from team_vote v join user u on u.id = v.creator_id where v.id = ?", rs -> rs.next() ? rowMap(rs) : null, voteId);
        return toVoteItem(vote, Map.of(), List.of());
    }

    @Transactional
    public void castVote(long teamId, long voteId, long userId, VoteCastRequest request) {
        requireMembershipRequired(teamId, userId);
        Map<String, Object> vote = jdbcTemplate.query("select * from team_vote where id = ? and team_id = ?", rs -> rs.next() ? rowMap(rs) : null, voteId, teamId);
        if (vote == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        LocalDateTime deadline = asDateTime(vote.get("deadline"));
        if (deadline != null && deadline.isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.CONFLICT);
        }
        List<String> options = parseStringList(vote.get("options"));
        List<Integer> optionIndexes = request.optionIndexes().stream().distinct().toList();
        if (!asBoolean(vote.get("multi_choice")) && optionIndexes.size() > 1) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        for (Integer index : optionIndexes) {
            if (index == null || index < 0 || index >= options.size()) {
                throw new BizException(ErrorCode.BAD_REQUEST);
            }
        }
        Integer existing = jdbcTemplate.queryForObject("select count(*) from team_vote_record where vote_id = ? and user_id = ?", Integer.class, voteId, userId);
        if (existing != null && existing > 0) {
            throw new BizException(ErrorCode.CONFLICT);
        }
        for (Integer optionIndex : optionIndexes) {
            jdbcTemplate.update("insert into team_vote_record(vote_id, user_id, option_index) values (?, ?, ?)", voteId, userId, optionIndex);
        }
    }

    public List<TeamFileItem> listFiles(long teamId, long userId) {
        requireMembershipRequired(teamId, userId);
        return jdbcTemplate.query(
                "select f.id, f.uploader_id, u.nickname as uploader_name, f.file_name, f.file_url, f.file_size, f.created_at from team_file f join user u on u.id = f.uploader_id where f.team_id = ? order by f.created_at desc",
                (rs, rowNum) -> new TeamFileItem(rs.getLong("id"), rs.getLong("uploader_id"), rs.getString("uploader_name"), rs.getString("file_name"), rs.getString("file_url"), getLong(rs, "file_size"), toDateTime(rs, "created_at")),
                teamId
        );
    }

    @Transactional
    public TeamFileItem createFile(long teamId, long userId, FileCreateRequest request) {
        requireMembershipRequired(teamId, userId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update("insert into team_file(team_id, uploader_id, file_name, file_url, file_size) values (:teamId, :uploaderId, :fileName, :fileUrl, :fileSize)",
                new MapSqlParameterSource().addValue("teamId", teamId).addValue("uploaderId", userId).addValue("fileName", request.fileName().trim()).addValue("fileUrl", request.fileUrl().trim()).addValue("fileSize", request.fileSize()),
                keyHolder,
                new String[]{"id"});
        long fileId = requireGeneratedId(keyHolder);
        return jdbcTemplate.queryForObject(
                "select f.id, f.uploader_id, u.nickname as uploader_name, f.file_name, f.file_url, f.file_size, f.created_at from team_file f join user u on u.id = f.uploader_id where f.id = ?",
                (rs, rowNum) -> new TeamFileItem(rs.getLong("id"), rs.getLong("uploader_id"), rs.getString("uploader_name"), rs.getString("file_name"), rs.getString("file_url"), getLong(rs, "file_size"), toDateTime(rs, "created_at")),
                fileId
        );
    }

    @Transactional
    public void deleteFile(long teamId, long fileId, long userId) {
        requireManager(teamId, userId);
        jdbcTemplate.update("delete from team_file where id = ? and team_id = ?", fileId, teamId);
    }

    public List<TeamAlbumPhotoItem> listAlbum(long teamId, long userId) {
        requireMembershipRequired(teamId, userId);
        return jdbcTemplate.query(
                "select p.id, p.uploader_id, u.nickname as uploader_name, p.image_url, p.created_at from team_album_photo p join user u on u.id = p.uploader_id where p.team_id = ? order by p.created_at desc",
                (rs, rowNum) -> new TeamAlbumPhotoItem(rs.getLong("id"), rs.getLong("uploader_id"), rs.getString("uploader_name"), rs.getString("image_url"), toDateTime(rs, "created_at")),
                teamId
        );
    }

    @Transactional
    public List<TeamAlbumPhotoItem> createAlbumPhotos(long teamId, long userId, AlbumCreateRequest request) {
        requireMembershipRequired(teamId, userId);
        for (String imageUrl : request.imageUrls()) {
            jdbcTemplate.update("insert into team_album_photo(team_id, uploader_id, image_url) values (?, ?, ?)", teamId, userId, imageUrl.trim());
        }
        return listAlbum(teamId, userId).stream().limit(request.imageUrls().size()).toList();
    }

    @Transactional
    public void deleteAlbumPhoto(long teamId, long photoId, long userId) {
        requireManager(teamId, userId);
        jdbcTemplate.update("delete from team_album_photo where id = ? and team_id = ?", photoId, teamId);
    }

    public PageResult<TeamMomentItem> listMoments(long teamId, long userId, int page, int size) {
        requireMembershipRequired(teamId, userId);
        int safePage = Math.max(page, 1);
        int safeSize = clampSize(size);
        Long total = jdbcTemplate.queryForObject("select count(*) from team_moment where team_id = ?", Long.class, teamId);
        List<Map<String, Object>> rows = namedJdbcTemplate.queryForList(
                "select m.id, m.author_id, u.nickname as author_name, u.avatar as author_avatar, m.content, m.images, m.is_featured, m.created_at from team_moment m join user u on u.id = m.author_id where m.team_id = :teamId order by m.created_at desc limit :limit offset :offset",
                new MapSqlParameterSource().addValue("teamId", teamId).addValue("limit", safeSize).addValue("offset", (safePage - 1) * safeSize)
        );
        List<TeamMomentItem> list = rows.stream().map(this::toMomentItem).toList();
        return new PageResult<>(total == null ? 0 : total, safePage, safeSize, list);
    }

    @Transactional
    public TeamMomentItem createMoment(long teamId, long userId, MomentCreateRequest request) {
        requireMembershipRequired(teamId, userId);
        if (!hasText(request.content()) && (request.images() == null || request.images().isEmpty())) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        namedJdbcTemplate.update("insert into team_moment(team_id, author_id, content, images, is_featured) values (:teamId, :authorId, :content, :images, 0)",
                new MapSqlParameterSource()
                        .addValue("teamId", teamId)
                        .addValue("authorId", userId)
                        .addValue("content", blankToNull(request.content()))
                        .addValue("images", request.images() == null || request.images().isEmpty() ? null : writeJson(request.images())),
                keyHolder,
                new String[]{"id"});
        long momentId = requireGeneratedId(keyHolder);
        awardPoints(teamId, userId, POST_MOMENT_POINTS, "POST_MOMENT", momentId);
        Map<String, Object> row = jdbcTemplate.query("select m.id, m.author_id, u.nickname as author_name, u.avatar as author_avatar, m.content, m.images, m.is_featured, m.created_at from team_moment m join user u on u.id = m.author_id where m.id = ?", rs -> rs.next() ? rowMap(rs) : null, momentId);
        return toMomentItem(row);
    }

    @Transactional
    public void featureMoment(long teamId, long momentId, long userId) {
        requireManager(teamId, userId);
        Map<String, Object> row = jdbcTemplate.query("select * from team_moment where id = ? and team_id = ?", rs -> rs.next() ? rowMap(rs) : null, momentId, teamId);
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        boolean alreadyFeatured = asBoolean(row.get("is_featured"));
        if (!alreadyFeatured) {
            jdbcTemplate.update("update team_moment set is_featured = 1 where id = ?", momentId);
            awardPoints(teamId, ((Number) row.get("author_id")).longValue(), FEATURED_MOMENT_POINTS, "MOMENT_FEATURED", momentId);
        }
    }

    public List<TeamPointItem> listPoints(long teamId, long userId) {
        requireMembershipRequired(teamId, userId);
        return jdbcTemplate.query(
                "select tm.user_id, u.nickname, u.avatar, tm.points from team_member tm join user u on u.id = tm.user_id where tm.team_id = ? order by tm.points desc, tm.joined_at asc",
                (rs, rowNum) -> new TeamPointItem(rs.getLong("user_id"), rs.getString("nickname"), rs.getString("avatar"), rs.getInt("points"), rowNum + 1),
                teamId
        );
    }

    public PageResult<ActivityItem> listActivities(long teamId, long userId, int page, int size) {
        requireMembershipRequired(teamId, userId);
        int safePage = Math.max(page, 1);
        int safeSize = clampSize(size);
        Long total = jdbcTemplate.queryForObject("select count(*) from activity where team_id = ? and deleted_at is null", Long.class, teamId);
        List<Map<String, Object>> rows = namedJdbcTemplate.queryForList(
                "select a.*, u.nickname as creator_nickname, u.avatar as creator_avatar, u.user_type as creator_user_type, u.status as creator_status from activity a join user u on u.id = a.creator_id where a.team_id = :teamId and a.deleted_at is null order by a.created_at desc limit :limit offset :offset",
                new MapSqlParameterSource().addValue("teamId", teamId).addValue("limit", safeSize).addValue("offset", (safePage - 1) * safeSize)
        );
        List<ActivityItem> list = rows.stream().map(this::toActivityItem).toList();
        return new PageResult<>(total == null ? 0 : total, safePage, safeSize, list);
    }

    private TeamSummary toTeamSummary(Map<String, Object> row, List<String> tags, String myRole) {
        return new TeamSummary(
                ((Number) row.get("id")).longValue(),
                (String) row.get("name"),
                (String) row.get("intro"),
                (String) row.get("avatar"),
                tags,
                (String) row.get("join_type"),
                ((Number) row.get("capacity")).intValue(),
                ((Number) row.get("member_count")).intValue(),
                (String) row.get("status"),
                ownerFromRow(row),
                myRole,
                myRole != null,
                asDateTime(row.get("created_at"))
        );
    }

    private TeamDetail toTeamDetail(Map<String, Object> row, List<String> tags, String myRole) {
        return new TeamDetail(
                ((Number) row.get("id")).longValue(),
                (String) row.get("name"),
                (String) row.get("intro"),
                (String) row.get("avatar"),
                tags,
                (String) row.get("join_type"),
                ((Number) row.get("capacity")).intValue(),
                ((Number) row.get("member_count")).intValue(),
                (String) row.get("status"),
                ownerFromRow(row),
                myRole,
                myRole != null,
                asDateTime(row.get("created_at"))
        );
    }

    private TeamVoteItem toVoteItem(Map<String, Object> vote, Map<Integer, Integer> counts, List<Integer> myVotes) {
        List<String> options = parseStringList(vote.get("options"));
        List<Integer> orderedCounts = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            orderedCounts.add(counts.getOrDefault(i, 0));
        }
        return new TeamVoteItem(
                ((Number) vote.get("id")).longValue(),
                (String) vote.get("title"),
                options,
                orderedCounts,
                asBoolean(vote.get("multi_choice")),
                asDateTime(vote.get("deadline")),
                asDateTime(vote.get("created_at")),
                ((Number) vote.get("creator_id")).longValue(),
                (String) vote.get("creator_name"),
                myVotes
        );
    }

    private TeamMomentItem toMomentItem(Map<String, Object> row) {
        return new TeamMomentItem(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("author_id")).longValue(),
                (String) row.get("author_name"),
                (String) row.get("author_avatar"),
                (String) row.get("content"),
                parseStringList(row.get("images")),
                asBoolean(row.get("is_featured")),
                asDateTime(row.get("created_at"))
        );
    }

    private ActivityItem toActivityItem(Map<String, Object> row) {
        return new ActivityItem(
                ((Number) row.get("id")).longValue(),
                (String) row.get("name"),
                (String) row.get("intro"),
                (String) row.get("category"),
                (String) row.get("cover_image"),
                asDateTime(row.get("start_time")),
                asDateTime(row.get("end_time")),
                asDateTime(row.get("signup_deadline")),
                (String) row.get("city"),
                (String) row.get("address"),
                row.get("lng") == null ? null : ((Number) row.get("lng")).doubleValue(),
                row.get("lat") == null ? null : ((Number) row.get("lat")).doubleValue(),
                row.get("capacity") == null ? null : ((Number) row.get("capacity")).intValue(),
                row.get("fee") == null ? null : ((Number) row.get("fee")).doubleValue(),
                (String) row.get("status"),
                derivePhase(asDateTime(row.get("start_time")), asDateTime(row.get("end_time")), asDateTime(row.get("signup_deadline"))),
                0,
                new UserBrief(((Number) row.get("creator_id")).longValue(), (String) row.get("creator_nickname"), (String) row.get("creator_avatar"), (String) row.get("creator_user_type"), (String) row.get("creator_status")),
                row.get("team_id") == null ? null : ((Number) row.get("team_id")).longValue()
        );
    }

    private TeamMembership requireMembership(long teamId, long userId) {
        return namedJdbcTemplate.query(
                "select role, points from team_member where team_id = :teamId and user_id = :userId",
                new MapSqlParameterSource().addValue("teamId", teamId).addValue("userId", userId),
                rs -> rs.next() ? new TeamMembership(rs.getString("role"), rs.getInt("points")) : null
        );
    }

    private TeamMembership requireMembershipRequired(long teamId, long userId) {
        TeamMembership membership = requireMembership(teamId, userId);
        if (membership == null) {
            throw teamError(CODE_NO_PERMISSION, "no_team_permission");
        }
        return membership;
    }

    private void requireManager(long teamId, long userId) {
        TeamMembership membership = requireMembership(teamId, userId);
        if (membership == null || !Set.of("OWNER", "ADMIN").contains(membership.role())) {
            throw teamError(CODE_NO_PERMISSION, "no_team_permission");
        }
    }

    private void requireOwner(String role) {
        if (!"OWNER".equals(role)) {
            throw teamError(CODE_NO_PERMISSION, "no_team_permission");
        }
    }

    private Map<String, Object> findTeamRow(long teamId) {
        Map<String, Object> row = jdbcTemplate.query(
                "select t.*, u.nickname as owner_nickname, u.avatar as owner_avatar, u.user_type as owner_user_type, u.status as owner_status from team t join user u on u.id = t.owner_id where t.id = ? and t.deleted_at is null",
                rs -> rs.next() ? rowMap(rs) : null,
                teamId
        );
        if (row == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return row;
    }

    private Map<String, Object> findActiveTeam(long teamId) {
        Map<String, Object> row = findTeamRow(teamId);
        String status = String.valueOf(row.get("status"));
        if (!"ACTIVE".equals(status)) {
            throw teamError(CODE_TEAM_INACTIVE, "team_dissolved_or_suspended");
        }
        return row;
    }

    private boolean isMember(long teamId, long userId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from team_member where team_id = ? and user_id = ?", Integer.class, teamId, userId);
        return count != null && count > 0;
    }

    private boolean isBlocked(long userId, long ownerId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from user_block where (user_id = ? and blocked_user_id = ?) or (user_id = ? and blocked_user_id = ?)",
                Integer.class,
                userId,
                ownerId,
                ownerId,
                userId
        );
        return count != null && count > 0;
    }

    private void ensureTeamHasCapacity(long teamId) {
        Map<String, Object> team = findActiveTeam(teamId);
        int memberCount = ((Number) team.get("member_count")).intValue();
        int capacity = ((Number) team.get("capacity")).intValue();
        if (memberCount >= capacity) {
            throw teamError(CODE_TEAM_FULL, "team_full");
        }
    }

    private void addMember(long teamId, long userId, String role) {
        jdbcTemplate.update("insert into team_member(team_id, user_id, role, points) values (?, ?, ?, 0)", teamId, userId, role);
        jdbcTemplate.update("update team set member_count = member_count + 1, updated_at = current_timestamp where id = ?", teamId);
    }

    private void decrementMemberCount(long teamId) {
        jdbcTemplate.update("update team set member_count = case when member_count > 0 then member_count - 1 else 0 end, updated_at = current_timestamp where id = ?", teamId);
    }

    private void replaceTags(long teamId, List<String> tags) {
        jdbcTemplate.update("delete from team_tag where team_id = ?", teamId);
        if (tags == null) {
            return;
        }
        tags.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().limit(10)
                .forEach(tag -> jdbcTemplate.update("insert into team_tag(team_id, tag) values (?, ?)", teamId, tag));
    }

    private Map<Long, List<String>> loadTags(List<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<String>> result = new HashMap<>();
        namedJdbcTemplate.query("select team_id, tag from team_tag where team_id in (:ids) order by id asc",
                new MapSqlParameterSource().addValue("ids", teamIds),
                rs -> {
                    long teamId = rs.getLong("team_id");
                    result.computeIfAbsent(teamId, ignored -> new ArrayList<>()).add(rs.getString("tag"));
                });
        return result;
    }

    private Map<Long, String> loadRoles(List<Long> teamIds, long userId) {
        if (teamIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return namedJdbcTemplate.query("select team_id, role from team_member where user_id = :userId and team_id in (:ids)",
                new MapSqlParameterSource().addValue("userId", userId).addValue("ids", teamIds),
                rs -> {
                    Map<Long, String> roles = new HashMap<>();
                    while (rs.next()) {
                        roles.put(rs.getLong("team_id"), rs.getString("role"));
                    }
                    return roles;
                });
    }

    private Map<Long, Map<Integer, Integer>> loadVoteCounts(List<Long> voteIds) {
        Map<Long, Map<Integer, Integer>> result = new HashMap<>();
        namedJdbcTemplate.query("select vote_id, option_index, count(*) as cnt from team_vote_record where vote_id in (:voteIds) group by vote_id, option_index",
                new MapSqlParameterSource().addValue("voteIds", voteIds),
                rs -> {
                    long voteId = rs.getLong("vote_id");
                    result.computeIfAbsent(voteId, ignored -> new HashMap<>()).put(rs.getInt("option_index"), rs.getInt("cnt"));
                });
        return result;
    }

    private Map<Long, List<Integer>> loadUserVotes(List<Long> voteIds, long userId) {
        Map<Long, List<Integer>> result = new HashMap<>();
        namedJdbcTemplate.query("select vote_id, option_index from team_vote_record where vote_id in (:voteIds) and user_id = :userId order by option_index asc",
                new MapSqlParameterSource().addValue("voteIds", voteIds).addValue("userId", userId),
                rs -> {
                    long voteId = rs.getLong("vote_id");
                    result.computeIfAbsent(voteId, ignored -> new ArrayList<>()).add(rs.getInt("option_index"));
                });
        return result;
    }

    private void awardPoints(long teamId, long userId, int points, String reason, long refId) {
        jdbcTemplate.update("insert into team_points_log(team_id, user_id, points, reason, ref_id) values (?, ?, ?, ?, ?)", teamId, userId, points, reason, refId);
        jdbcTemplate.update("update team_member set points = points + ? where team_id = ? and user_id = ?", points, teamId, userId);
    }

    private TeamJoinRequestItem mapJoinRequest(ResultSet rs, int rowNum) throws SQLException {
        return new TeamJoinRequestItem(rs.getLong("id"), rs.getLong("user_id"), rs.getString("nickname"), rs.getString("avatar"), rs.getString("status"), toDateTime(rs, "created_at"), toDateTime(rs, "handled_at"), getLong(rs, "handler_id"));
    }

    private TeamMemberItem mapMember(ResultSet rs, int rowNum) throws SQLException {
        return new TeamMemberItem(rs.getLong("user_id"), rs.getString("nickname"), rs.getString("avatar"), rs.getString("user_type"), rs.getString("status"), rs.getString("role"), rs.getInt("points"), toDateTime(rs, "joined_at"));
    }

    private UserBrief ownerFromRow(Map<String, Object> row) {
        return new UserBrief(((Number) row.get("owner_id")).longValue(), (String) row.get("owner_nickname"), (String) row.get("owner_avatar"), (String) row.get("owner_user_type"), (String) row.get("owner_status"));
    }

    private LocalDateTime toDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private LocalDateTime asDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        return null;
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private Long getLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Map<String, Object> rowMap(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        int count = rs.getMetaData().getColumnCount();
        for (int i = 1; i <= count; i++) {
            row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
        }
        return row;
    }

    private int clampSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }

    private int normalizeCapacity(Integer capacity) {
        if (capacity == null) {
            return 100;
        }
        if (capacity < 1 || capacity > 500) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        return capacity;
    }

    private void validateJoinType(String joinType) {
        if (!Set.of("PUBLIC", "APPROVAL").contains(joinType)) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private long requireGeneratedId(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
        return key.longValue();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private List<String> parseStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(String.valueOf(value), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String derivePhase(LocalDateTime start, LocalDateTime end, LocalDateTime deadline) {
        LocalDateTime now = LocalDateTime.now();
        if (start != null && now.isBefore(start)) {
            if (deadline == null || !now.isAfter(deadline)) {
                return "SIGNUP_OPEN";
            }
            return "SIGNUP_CLOSED";
        }
        if (start != null && end != null && (now.isEqual(start) || now.isAfter(start)) && now.isBefore(end)) {
            return "ONGOING";
        }
        if (end != null && (now.isEqual(end) || now.isAfter(end))) {
            return "ENDED";
        }
        return "NOT_STARTED";
    }

    private BizException teamError(int code, String message) {
        return new BizException(code, message);
    }

    private record TeamMembership(String role, Integer points) {}
}
