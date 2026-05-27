package com.shashikiranreddy.splitwise.group.api;

import com.shashikiranreddy.splitwise.group.application.GroupService;
import com.shashikiranreddy.splitwise.group.application.GroupService.GroupView;
import com.shashikiranreddy.splitwise.group.application.GroupService.MemberView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Group management endpoints. All paths under {@code /groups} require an
 * authenticated principal; the user id is extracted from the JWT subject.
 */
@RestController
@RequestMapping("/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    public record CreateGroupRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description) {}

    public record AddMemberRequest(@Email @NotBlank String email) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupView create(@Valid @RequestBody CreateGroupRequest request,
                            @AuthenticationPrincipal Long userId) {
        return groupService.createGroup(request.name(), request.description(), userId);
    }

    @GetMapping
    public List<GroupView> listMine(@AuthenticationPrincipal Long userId) {
        return groupService.listMyGroups(userId);
    }

    @GetMapping("/{groupId}")
    public GroupView getOne(@PathVariable Long groupId,
                            @AuthenticationPrincipal Long userId) {
        return groupService.getGroup(groupId, userId);
    }

    @GetMapping("/{groupId}/members")
    public List<MemberView> listMembers(@PathVariable Long groupId,
                                        @AuthenticationPrincipal Long userId) {
        return groupService.listMembers(groupId, userId);
    }

    @PostMapping("/{groupId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberView addMember(@PathVariable Long groupId,
                                @Valid @RequestBody AddMemberRequest request,
                                @AuthenticationPrincipal Long userId) {
        return groupService.addMemberByEmail(groupId, request.email(), userId);
    }
}
