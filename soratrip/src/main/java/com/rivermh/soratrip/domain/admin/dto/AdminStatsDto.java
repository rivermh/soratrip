package com.rivermh.soratrip.domain.admin.dto;

public record AdminStatsDto(
        long totalMembers,
        long newMembersThisWeek,
        long totalPosts,
        long newPostsThisWeek,
        long totalSchedules,
        long newSchedulesThisWeek,
        long pendingReports
) {
}
