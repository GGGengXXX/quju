package cn.edu.buaa.quju.module.user.controller;

import cn.edu.buaa.quju.common.R;
import cn.edu.buaa.quju.common.UserContext;
import cn.edu.buaa.quju.module.activity.dto.ActivityDtos.ActivityVO;
import cn.edu.buaa.quju.module.activity.service.ActivityService;
import cn.edu.buaa.quju.module.user.dto.UserDtos.UpdateProfileReq;
import cn.edu.buaa.quju.module.user.dto.UserDtos.UserBrief;
import cn.edu.buaa.quju.module.user.dto.UserDtos.UserVO;
import cn.edu.buaa.quju.module.user.service.UserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/users")
public class UserController {
    private final UserService userService;
    private final ActivityService activityService;
    private final JdbcTemplate jdbcTemplate;

    public UserController(UserService userService, ActivityService activityService, JdbcTemplate jdbcTemplate) {
        this.userService = userService;
        this.activityService = activityService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/me")
    public R<UserVO> me() { return R.ok(userService.getProfile(UserContext.require())); }

    @PutMapping("/me")
    public R<UserVO> update(@RequestBody UpdateProfileReq req) {
        return R.ok(userService.updateProfile(UserContext.require(), req));
    }

    @GetMapping("/me/qrcode")
    public R<String> qrcode() { return R.ok(userService.getQrCodeContent(UserContext.require())); }

    @GetMapping("/{id}")
    public R<UserVO> getById(@PathVariable Long id) {
        return R.ok(userService.getPublicProfile(id));
    }

    @GetMapping("/{id}/activities")
    public R<List<ActivityVO>> userActivities(@PathVariable Long id) {
        if (!userService.isPrivacyAllowed(id, "showActivities")) return R.ok(List.of());
        return R.ok(activityService.userJoinedActivities(id));
    }

    @GetMapping("/{id}/teams")
    public R<List<Map<String, Object>>> userTeams(@PathVariable Long id) {
        if (!userService.isPrivacyAllowed(id, "showTeams")) return R.ok(List.of());
        List<Map<String, Object>> teams = jdbcTemplate.queryForList(
            "SELECT t.id, t.name, t.avatar, t.status, t.member_count AS memberCount " +
            "FROM team_member tm JOIN team t ON t.id = tm.team_id " +
            "WHERE tm.user_id = ? AND t.status = 'ACTIVE' ORDER BY tm.joined_at DESC", id);
        return R.ok(teams);
    }

    @GetMapping("/search")
    public R<UserBrief> searchByAccountId(@RequestParam String accountId) {
        return R.ok(userService.searchByAccountId(accountId));
    }
}
