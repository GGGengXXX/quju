package cn.edu.buaa.quju.module.team.controller;

import cn.edu.buaa.quju.common.R;
import cn.edu.buaa.quju.common.UserContext;
import cn.edu.buaa.quju.module.team.dto.TeamDtos.*;
import cn.edu.buaa.quju.module.team.service.TeamService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/teams")
public class TeamController {
    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public R<PageResult<TeamSummary>> searchTeams(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return R.ok(teamService.searchTeams(keyword, tag, page, size, UserContext.get()));
    }

    @PostMapping
    public R<TeamDetail> createTeam(@RequestBody @Valid TeamUpsertRequest request) {
        return R.ok(teamService.createTeam(UserContext.require(), request));
    }

    @GetMapping("/{id}")
    public R<TeamDetail> getTeam(@PathVariable Long id) {
        return R.ok(teamService.getTeamDetail(id, UserContext.get()));
    }

    @DeleteMapping("/{id}")
    public R<Void> dissolveTeam(@PathVariable Long id) {
        teamService.dissolveTeam(id, UserContext.require());
        return R.ok(null);
    }

    @PostMapping("/{id}/join")
    public R<TeamJoinResult> joinTeam(@PathVariable Long id) {
        return R.ok(teamService.joinTeam(id, UserContext.require()));
    }

    @PostMapping("/{id}/leave")
    public R<Void> leaveTeam(@PathVariable Long id) {
        teamService.leaveTeam(id, UserContext.require());
        return R.ok(null);
    }

    @GetMapping("/{id}/join-requests")
    public R<java.util.List<TeamJoinRequestItem>> listJoinRequests(@PathVariable Long id) {
        return R.ok(teamService.listJoinRequests(id, UserContext.require()));
    }

    @PostMapping("/{id}/join-requests/{reqId}")
    public R<Void> handleJoinRequest(@PathVariable Long id, @PathVariable Long reqId, @RequestBody @Valid HandleJoinRequest request) {
        teamService.handleJoinRequest(id, reqId, UserContext.require(), request);
        return R.ok(null);
    }

    @GetMapping("/{id}/members")
    public R<java.util.List<TeamMemberItem>> listMembers(@PathVariable Long id) {
        return R.ok(teamService.listMembers(id));
    }

    @PutMapping("/{id}/members/{userId}")
    public R<Void> updateMemberRole(@PathVariable Long id, @PathVariable Long userId, @RequestBody @Valid RoleUpdateRequest request) {
        teamService.updateMemberRole(id, userId, UserContext.require(), request);
        return R.ok(null);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public R<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        teamService.removeMember(id, userId, UserContext.require());
        return R.ok(null);
    }

    @GetMapping("/{id}/announcements")
    public R<java.util.List<TeamAnnouncementItem>> listAnnouncements(@PathVariable Long id) {
        return R.ok(teamService.listAnnouncements(id, UserContext.require()));
    }

    @PostMapping("/{id}/announcements")
    public R<TeamAnnouncementItem> createAnnouncement(@PathVariable Long id, @RequestBody @Valid AnnouncementCreateRequest request) {
        return R.ok(teamService.createAnnouncement(id, UserContext.require(), request));
    }

    @GetMapping("/{id}/votes")
    public R<java.util.List<TeamVoteItem>> listVotes(@PathVariable Long id) {
        return R.ok(teamService.listVotes(id, UserContext.require()));
    }

    @PostMapping("/{id}/votes")
    public R<TeamVoteItem> createVote(@PathVariable Long id, @RequestBody @Valid VoteCreateRequest request) {
        return R.ok(teamService.createVote(id, UserContext.require(), request));
    }

    @PostMapping("/{id}/votes/{voteId}/cast")
    public R<Void> castVote(@PathVariable Long id, @PathVariable Long voteId, @RequestBody @Valid VoteCastRequest request) {
        teamService.castVote(id, voteId, UserContext.require(), request);
        return R.ok(null);
    }

    @GetMapping("/{id}/files")
    public R<java.util.List<TeamFileItem>> listFiles(@PathVariable Long id) {
        return R.ok(teamService.listFiles(id, UserContext.require()));
    }

    @PostMapping("/{id}/files")
    public R<TeamFileItem> createFile(@PathVariable Long id, @RequestBody @Valid FileCreateRequest request) {
        return R.ok(teamService.createFile(id, UserContext.require(), request));
    }

    @DeleteMapping("/{id}/files/{fileId}")
    public R<Void> deleteFile(@PathVariable Long id, @PathVariable Long fileId) {
        teamService.deleteFile(id, fileId, UserContext.require());
        return R.ok(null);
    }

    @GetMapping("/{id}/album")
    public R<java.util.List<TeamAlbumPhotoItem>> listAlbum(@PathVariable Long id) {
        return R.ok(teamService.listAlbum(id, UserContext.require()));
    }

    @PostMapping("/{id}/album")
    public R<java.util.List<TeamAlbumPhotoItem>> createAlbumPhotos(@PathVariable Long id, @RequestBody @Valid AlbumCreateRequest request) {
        return R.ok(teamService.createAlbumPhotos(id, UserContext.require(), request));
    }

    @DeleteMapping("/{id}/album/{photoId}")
    public R<Void> deleteAlbumPhoto(@PathVariable Long id, @PathVariable Long photoId) {
        teamService.deleteAlbumPhoto(id, photoId, UserContext.require());
        return R.ok(null);
    }

    @GetMapping("/{id}/moments")
    public R<PageResult<TeamMomentItem>> listMoments(@PathVariable Long id, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return R.ok(teamService.listMoments(id, UserContext.require(), page, size));
    }

    @PostMapping("/{id}/moments")
    public R<TeamMomentItem> createMoment(@PathVariable Long id, @RequestBody @Valid MomentCreateRequest request) {
        return R.ok(teamService.createMoment(id, UserContext.require(), request));
    }

    @PostMapping("/{id}/moments/{momentId}/feature")
    public R<Void> featureMoment(@PathVariable Long id, @PathVariable Long momentId) {
        teamService.featureMoment(id, momentId, UserContext.require());
        return R.ok(null);
    }

    @GetMapping("/{id}/points")
    public R<java.util.List<TeamPointItem>> listPoints(@PathVariable Long id) {
        return R.ok(teamService.listPoints(id, UserContext.require()));
    }

    @GetMapping("/{id}/activities")
    public R<PageResult<ActivityItem>> listActivities(@PathVariable Long id, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return R.ok(teamService.listActivities(id, UserContext.require(), page, size));
    }
}
