package cn.edu.buaa.quju.module.admin.service;

import cn.edu.buaa.quju.common.BizException;
import cn.edu.buaa.quju.common.ErrorCode;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.PageResult;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.ReasonReq;
import cn.edu.buaa.quju.module.admin.dto.AdminDtos.TeamListVO;
import cn.edu.buaa.quju.module.admin.entity.ModerationAction;
import cn.edu.buaa.quju.module.admin.entity.Team;
import cn.edu.buaa.quju.module.admin.mapper.ModerationActionMapper;
import cn.edu.buaa.quju.module.admin.mapper.TeamMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminTeamService {
    private final TeamMapper teamMapper;
    private final ModerationActionMapper moderationMapper;

    public AdminTeamService(TeamMapper teamMapper, ModerationActionMapper moderationMapper) {
        this.teamMapper = teamMapper;
        this.moderationMapper = moderationMapper;
    }

    public PageResult<TeamListVO> listTeams(String keyword, String status, int page, int size) {
        LambdaQueryWrapper<Team> q = Wrappers.<Team>lambdaQuery().isNull(Team::getDeletedAt);
        if (keyword != null && !keyword.isBlank()) q.like(Team::getName, keyword);
        if (status != null && !status.isBlank()) q.eq(Team::getStatus, status);
        IPage<Team> p = teamMapper.selectPage(new Page<>(page, size), q);
        List<TeamListVO> list = p.getRecords().stream()
                .map(t -> new TeamListVO(t.getId(), t.getOwnerId(), t.getName(),
                        t.getStatus(), t.getMemberCount(), t.getCreatedAt()))
                .collect(Collectors.toList());
        return new PageResult<>(p.getTotal(), page, size, list);
    }

    @Transactional
    public void suspendTeam(long adminId, long teamId, ReasonReq req) {
        if (req.reason() == null || req.reason().isBlank())
            throw new BizException(ErrorCode.TAKEDOWN_REASON_REQUIRED);
        Team t = requireTeam(teamId);
        t.setStatus("SUSPENDED");
        teamMapper.updateById(t);
        logModeration(adminId, teamId, "SUSPEND", req.reason());
    }

    @Transactional
    public void restoreTeam(long adminId, long teamId) {
        Team t = requireTeam(teamId);
        if (!"SUSPENDED".equals(t.getStatus())) throw new BizException(ErrorCode.CONFLICT);
        t.setStatus("ACTIVE");
        teamMapper.updateById(t);
        logModeration(adminId, teamId, "RESTORE", "管理员恢复");
    }

    private Team requireTeam(long id) {
        Team t = teamMapper.selectById(id);
        if (t == null || t.getDeletedAt() != null) throw new BizException(ErrorCode.NOT_FOUND);
        return t;
    }

    private void logModeration(long adminId, long teamId, String action, String reason) {
        ModerationAction m = new ModerationAction();
        m.setAdminId(adminId);
        m.setTargetType("TEAM");
        m.setTargetId(teamId);
        m.setAction(action);
        m.setReason(reason);
        moderationMapper.insert(m);
    }
}
