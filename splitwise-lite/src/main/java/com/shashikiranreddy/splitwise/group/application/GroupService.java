package com.shashikiranreddy.splitwise.group.application;

import com.shashikiranreddy.splitwise.common.error.GlobalExceptionHandler.ConflictException;
import com.shashikiranreddy.splitwise.common.error.GlobalExceptionHandler.ForbiddenException;
import com.shashikiranreddy.splitwise.common.error.GlobalExceptionHandler.ResourceNotFoundException;
import com.shashikiranreddy.splitwise.group.domain.Group;
import com.shashikiranreddy.splitwise.group.domain.GroupMember;
import com.shashikiranreddy.splitwise.group.domain.GroupMemberRepository;
import com.shashikiranreddy.splitwise.group.domain.GroupRepository;
import com.shashikiranreddy.splitwise.user.domain.User;
import com.shashikiranreddy.splitwise.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Group lifecycle: create a group (creator is auto-enrolled), list a user's
 * groups, fetch a group's members, and add a new member by email.
 *
 * <p>Authorization rule: only existing members of a group can read it or
 * invite others. This service centralizes that check via {@link #requireMembership}.
 */
@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;

    public GroupService(GroupRepository groupRepository,
                        GroupMemberRepository memberRepository,
                        UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    public record GroupView(Long id, String name, String description,
                            Long createdBy, long memberCount) {}

    public record MemberView(Long userId, String email, String name) {}

    @Transactional
    public GroupView createGroup(String name, String description, Long currentUserId) {
        Group group = groupRepository.save(new Group(name.trim(),
                description == null ? null : description.trim(), currentUserId));
        memberRepository.save(new GroupMember(group.getId(), currentUserId));
        return new GroupView(group.getId(), group.getName(), group.getDescription(),
                group.getCreatedBy(), 1L);
    }

    @Transactional(readOnly = true)
    public List<GroupView> listMyGroups(Long currentUserId) {
        return groupRepository.findGroupsForUser(currentUserId).stream()
                .map(g -> new GroupView(g.getId(), g.getName(), g.getDescription(),
                        g.getCreatedBy(), memberRepository.countByGroupId(g.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupView getGroup(Long groupId, Long currentUserId) {
        Group group = requireGroup(groupId);
        requireMembership(groupId, currentUserId);
        return new GroupView(group.getId(), group.getName(), group.getDescription(),
                group.getCreatedBy(), memberRepository.countByGroupId(groupId));
    }

    @Transactional(readOnly = true)
    public List<MemberView> listMembers(Long groupId, Long currentUserId) {
        requireGroup(groupId);
        requireMembership(groupId, currentUserId);
        List<GroupMember> rows = memberRepository.findByGroupId(groupId);
        List<Long> userIds = rows.stream().map(GroupMember::getUserId).toList();
        return userRepository.findAllById(userIds).stream()
                .map(u -> new MemberView(u.getId(), u.getEmail(), u.getName()))
                .toList();
    }

    @Transactional
    public MemberView addMemberByEmail(Long groupId, String email, Long currentUserId) {
        requireGroup(groupId);
        requireMembership(groupId, currentUserId);
        User invitee = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No registered user with email " + email));
        if (memberRepository.existsByGroupIdAndUserId(groupId, invitee.getId())) {
            throw new ConflictException("User is already a member of this group.");
        }
        memberRepository.save(new GroupMember(groupId, invitee.getId()));
        return new MemberView(invitee.getId(), invitee.getEmail(), invitee.getName());
    }

    private Group requireGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group " + groupId + " not found."));
    }

    private void requireMembership(Long groupId, Long userId) {
        if (!memberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ForbiddenException("You are not a member of this group.");
        }
    }
}
