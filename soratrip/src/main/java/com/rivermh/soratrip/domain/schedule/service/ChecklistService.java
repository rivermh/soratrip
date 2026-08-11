package com.rivermh.soratrip.domain.schedule.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rivermh.soratrip.domain.schedule.dto.ChecklistItemResponseDto;
import com.rivermh.soratrip.domain.schedule.entity.ChecklistItem;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.repository.ChecklistItemRepository;
import com.rivermh.soratrip.domain.schedule.repository.TravelScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChecklistService {

    private final ChecklistItemRepository checklistItemRepository;
    private final TravelScheduleRepository travelScheduleRepository;

    public List<ChecklistItemResponseDto> getItems(Long scheduleId) {
        return checklistItemRepository.findByTravelScheduleIdOrderByIdAsc(scheduleId).stream()
                .map(ChecklistItemResponseDto::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public Long addItem(Long scheduleId, String email, String content) {
        TravelSchedule schedule = travelScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다. id=" + scheduleId));
        if (!schedule.isOwnedBy(email)) {
            throw new IllegalStateException("본인의 일정에만 준비물을 추가할 수 있습니다.");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("항목 내용을 입력해주세요.");
        }

        ChecklistItem item = ChecklistItem.builder()
                .travelSchedule(schedule)
                .content(content.trim())
                .build();
        return checklistItemRepository.save(item).getId();
    }

    @Transactional
    public void toggleItem(Long itemId, String email) {
        ChecklistItem item = checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("항목을 찾을 수 없습니다. id=" + itemId));
        if (!item.getTravelSchedule().isOwnedBy(email)) {
            throw new IllegalStateException("본인의 준비물 항목만 체크할 수 있습니다.");
        }
        item.toggle();
    }

    @Transactional
    public void deleteItem(Long itemId, String email) {
        ChecklistItem item = checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("항목을 찾을 수 없습니다. id=" + itemId));
        if (!item.getTravelSchedule().isOwnedBy(email)) {
            throw new IllegalStateException("본인의 준비물 항목만 삭제할 수 있습니다.");
        }
        checklistItemRepository.delete(item);
    }
}
