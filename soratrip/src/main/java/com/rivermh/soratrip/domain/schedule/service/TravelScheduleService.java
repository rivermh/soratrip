package com.rivermh.soratrip.domain.schedule.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.entity.Role;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.post.entity.Region;
import com.rivermh.soratrip.domain.schedule.dto.ScheduleDayForm;
import com.rivermh.soratrip.domain.schedule.dto.ScheduleItemForm;
import com.rivermh.soratrip.domain.schedule.dto.ScheduleOrderUpdateRequest;
import com.rivermh.soratrip.domain.schedule.dto.TravelScheduleForm;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleDay;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleItem;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.repository.ScheduleItemRepository;
import com.rivermh.soratrip.domain.schedule.repository.TravelScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelScheduleService {

    private final TravelScheduleRepository travelScheduleRepository;
    private final MemberRepository memberRepository;
    private final ScheduleItemRepository scheduleItemRepository;

    // 회원이 없으면 자동으로 DB에 가입시키는 도우미 메서드 (소셜 로그인 방어 로직)
    private Member getOrRegisterMember(String email) {
        return memberRepository.findByEmail(email)
                .orElseGet(() -> {
                    String defaultNickname = email.contains("@") ? email.split("@")[0] : email;
                    
                    // 구글 소셜 로그인 등 첫 진입 시 DB에 회원이 없으면 자동 생성
                    Member socialMember = Member.builder()
                            .email(email)
                            .nickname(defaultNickname)
                            .password("") // 소셜 로그인은 비밀번호 사용 안 함
                            .role(Role.USER) // 필수 값: USER 권한 부여
                            .provider("google") // 소셜 제공자
                            .providerId(email)
                            .build();
                            
                    return memberRepository.save(socialMember);
                });
    }

    // 일정 등록 (이동 태그 저장 포함)
    @Transactional
    public Long createSchedule(TravelScheduleForm form, String email) {
        Member member = getOrRegisterMember(email);

        TravelSchedule schedule = TravelSchedule.builder()
                .member(member)
                .title(form.getTitle())
                .region(form.getRegion())
                .startDate(form.getStartDate())
                .endDate(form.getEndDate())
                .budgetKrw(form.getBudgetKrw())
                .tags(form.getTags() != null ? new HashSet<>(form.getTags()) : new HashSet<>()) // 태그 세팅 추가
                .build();

        // 날짜 계산 (시작일 ~ 종료일 차이 기반으로 N일차 생성)
        long totalDays = ChronoUnit.DAYS.between(form.getStartDate(), form.getEndDate()) + 1;

        for (int i = 0; i < totalDays; i++) {
            ScheduleDay day = ScheduleDay.builder()
                    .travelSchedule(schedule)
                    .dayNumber(i + 1)
                    .visitDate(form.getStartDate().plusDays(i))
                    .build();

            // 만약 폼에 등록된 장소 아이템이 있다면 매핑
            if (form.getDays() != null && i < form.getDays().size()) {
                ScheduleDayForm dayForm = form.getDays().get(i);
                if (dayForm.getItems() != null) {
                    for (int j = 0; j < dayForm.getItems().size(); j++) {
                        ScheduleItemForm itemForm = dayForm.getItems().get(j);
                        ScheduleItem item = ScheduleItem.builder()
                                .scheduleDay(day)
                                .placeName(itemForm.getPlaceName())
                                .placeAddress(itemForm.getPlaceAddress())
                                .latitude(itemForm.getLatitude())
                                .longitude(itemForm.getLongitude())
                                .visitTime(itemForm.getVisitTime())
                                .visitOrder(j + 1)
                                .memo(itemForm.getMemo())
                                .placeType(itemForm.getPlaceType())
                                .build();
                        day.addItem(item);
                    }
                }
            }
            schedule.getDays().add(day);
        }

        TravelSchedule savedSchedule = travelScheduleRepository.save(schedule);
        return savedSchedule.getId();
    }

    // 내 일정 전체 조회
    public List<TravelSchedule> getMySchedules(Long memberId) {
        return travelScheduleRepository.findByMemberIdOrderByIdDesc(memberId);
    }
    
    // 이메일로 일정 조회 (소셜 로그인 예외 방지)
    @Transactional // readOnly = true 오버라이드
    public List<TravelSchedule> getMySchedulesByEmail(String email) {
        Member member = getOrRegisterMember(email);
        return travelScheduleRepository.findByMemberIdOrderByIdDesc(member.getId());
    }
    
    // 일정 상세 조회 (Cascade/Fetch Join 활용)
    public TravelSchedule getScheduleDetail(Long id) {
        return travelScheduleRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 일정을 찾을 수 없습니다. id=" + id));
    }

    // 일정 삭제
    @Transactional
    public void deleteSchedule(Long id, Long memberId) {
        TravelSchedule schedule = getScheduleDetail(id);
        if (!schedule.getMember().getId().equals(memberId)) {
            throw new IllegalStateException("삭제 권한이 없습니다.");
        }
        travelScheduleRepository.delete(schedule);
    }

    // 공개 일정 둘러보기 (지역/태그 필터 + 페이징)
    public Page<TravelSchedule> searchPublic(Region region, ScheduleTag tag, Pageable pageable) {
        return travelScheduleRepository.searchPublic(region, tag, pageable);
    }

    // 일정 공개 여부 / 태그 / 예산 설정 변경
    @Transactional
    public void updateSettings(Long id, String email, boolean isPublic, Set<ScheduleTag> tags, BigDecimal budgetKrw) {
        TravelSchedule schedule = getScheduleDetail(id);
        if (!schedule.isOwnedBy(email)) {
            throw new IllegalStateException("수정 권한이 없습니다.");
        }
        schedule.updateVisibility(isPublic);
        schedule.updateTags(tags);
        schedule.updateBudget(budgetKrw);
    }

    // 다른 회원의 일정을 내 일정으로 복사 (템플릿으로 가져오기)
    @Transactional
    public Long copySchedule(Long sourceScheduleId, LocalDate newStartDate, String email) {
        Member copier = getOrRegisterMember(email);

        TravelSchedule source = travelScheduleRepository.findByIdWithDetails(sourceScheduleId)
                .orElseThrow(() -> new IllegalArgumentException("해당 일정을 찾을 수 없습니다. id=" + sourceScheduleId));

        if (!source.isPublic() && !source.isOwnedBy(email)) {
            throw new IllegalStateException("비공개 일정은 복사할 수 없습니다.");
        }

        long tripLength = ChronoUnit.DAYS.between(source.getStartDate(), source.getEndDate());

        TravelSchedule copy = TravelSchedule.builder()
                .member(copier)
                .title(source.getTitle() + " (복사본)")
                .region(source.getRegion())
                .startDate(newStartDate)
                .endDate(newStartDate.plusDays(tripLength))
                .isPublic(false) // 복사본은 항상 비공개로 시작
                .tags(new HashSet<>(source.getTags()))
                .build();

        for (ScheduleDay day : source.getDays()) {
            long relativeOffset = ChronoUnit.DAYS.between(source.getStartDate(), day.getVisitDate());

            ScheduleDay dayCopy = ScheduleDay.builder()
                    .travelSchedule(copy)
                    .dayNumber(day.getDayNumber())
                    .visitDate(newStartDate.plusDays(relativeOffset))
                    .build();

            for (ScheduleItem item : day.getItems()) {
                dayCopy.getItems().add(ScheduleItem.builder()
                        .scheduleDay(dayCopy)
                        .placeName(item.getPlaceName())
                        .placeAddress(item.getPlaceAddress())
                        .latitude(item.getLatitude())
                        .longitude(item.getLongitude())
                        .visitTime(item.getVisitTime())
                        .visitOrder(item.getVisitOrder())
                        .memo(item.getMemo())
                        .placeType(item.getPlaceType())
                        .build());
            }
            copy.getDays().add(dayCopy);
        }

        return travelScheduleRepository.save(copy).getId();
    }
    
 // 1. 순서 변경 일괄 업데이트
    @Transactional
    public void updateItemOrder(Long scheduleId, ScheduleOrderUpdateRequest request, String email) {
        TravelSchedule schedule = travelScheduleRepository.findByIdWithDetails(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 일정입니다."));

        // 소유자 검증
        if (!schedule.isOwnedBy(email)) {
            throw new IllegalStateException("해당 일정을 수정할 권한이 없습니다.");
        }

        // 빠르게 요소를 찾기 위한 Map 생성
        Map<Long, ScheduleItem> itemMap = schedule.getDays().stream()
                .flatMap(day -> day.getItems().stream())
                .collect(Collectors.toMap(ScheduleItem::getId, item -> item));

        if (request.getItems() != null) {
            for (ScheduleOrderUpdateRequest.ItemOrderDto itemOrder : request.getItems()) {
                ScheduleItem item = itemMap.get(itemOrder.getItemId());
                if (item != null) {
                    item.setVisitOrder(itemOrder.getVisitOrder());
                }
            }
        }
    }

    // 공유 링크 발급 (소유자 전용, 이미 있으면 기존 토큰 재사용)
    @Transactional
    public String createShareLink(Long id, String email) {
        TravelSchedule schedule = getScheduleDetail(id);
        if (!schedule.isOwnedBy(email)) {
            throw new IllegalStateException("본인의 일정만 공유 링크를 만들 수 있습니다.");
        }
        return schedule.issueShareToken();
    }

    // 공유 링크 폐기 (소유자 전용)
    @Transactional
    public void revokeShareLink(Long id, String email) {
        TravelSchedule schedule = getScheduleDetail(id);
        if (!schedule.isOwnedBy(email)) {
            throw new IllegalStateException("본인의 일정만 공유 링크를 삭제할 수 있습니다.");
        }
        schedule.revokeShareToken();
    }

    // 공유 링크 토큰으로 일정 조회 (로그인 불필요, 유효하지 않은 토큰이면 예외)
    public TravelSchedule getScheduleByShareToken(String token) {
        return travelScheduleRepository.findByShareTokenWithDetails(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 공유 링크입니다."));
    }

    // 2. 장소 단건 삭제
    @Transactional
    public void deleteScheduleItem(Long itemId, String email) {
        ScheduleItem targetItem = scheduleItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 장소입니다. id=" + itemId));

        // 소유자 검증 (다른 사람의 일정에 속한 장소는 삭제 불가)
        if (!targetItem.getScheduleDay().getTravelSchedule().isOwnedBy(email)) {
            throw new IllegalStateException("해당 장소를 삭제할 권한이 없습니다.");
        }

        // 부모 컬렉션(orphanRemoval)과 영속성 컨텍스트 정합성을 위해 컬렉션에서도 제거
        targetItem.getScheduleDay().getItems().remove(targetItem);
        scheduleItemRepository.delete(targetItem);
    }
}