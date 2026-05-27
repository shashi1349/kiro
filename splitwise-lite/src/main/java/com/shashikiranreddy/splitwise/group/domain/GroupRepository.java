package com.shashikiranreddy.splitwise.group.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {

    /** Lists groups the given user belongs to, ordered by most recently created first. */
    @Query("""
           SELECT g FROM Group g
           WHERE g.id IN (
               SELECT m.groupId FROM GroupMember m WHERE m.userId = :userId
           )
           ORDER BY g.createdAt DESC
           """)
    List<Group> findGroupsForUser(Long userId);
}
