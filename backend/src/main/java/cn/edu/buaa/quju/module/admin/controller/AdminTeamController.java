package cn.edu.buaa.quju.module.admin.controller;

import cn.edu.buaa.quju.common.AdminContext;
import cn.edu.buaa.quju.common.R;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.PageResult;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.ReasonReq;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.TeamListVO;
import cn.edu.buaa.quju.module.admin.service.AdminTeamService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin")
public class AdminTeamController {
    private final AdminTeamService teamService;
    public AdminTeamController(AdminTeamService teamService) { this.teamService = teamService; }

    @GetMapping("/teams")
    public R<PageResult<TeamListVO>> listTeams(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        AdminContext.require();
        return R.ok(teamService.listTeams(keyword, status, page, size));
    }

    @PostMapping("/teams/{id}/suspend")
    public R<Void> suspend(@PathVariable Long id, @RequestBody @Valid ReasonReq req) {
        teamService.suspendTeam(AdminContext.require(), id, req);
        return R.<Void>ok(null);
    }

    @PostMapping("/teams/{id}/restore")
    public R<Void> restoreTeam(@PathVariable Long id) {
        teamService.restoreTeam(AdminContext.require(), id);
        return R.<Void>ok(null);
    }
}
