package com.rivermh.soratrip.domain.photo.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.rivermh.soratrip.domain.photo.entity.Photo;
import com.rivermh.soratrip.domain.photo.repository.PhotoRepository;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleDay;
import com.rivermh.soratrip.domain.schedule.repository.ScheduleDayRepository;
import com.rivermh.soratrip.global.file.FileStorageService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final ScheduleDayRepository scheduleDayRepository;
    private final FileStorageService fileStorageService;

    // 사진 업로드
    @Transactional
    public Long uploadPhoto(Long scheduleId, Long dayId, MultipartFile file, String caption, String email) {
        ScheduleDay day = scheduleDayRepository.findById(dayId)
                .orElseThrow(() -> new IllegalArgumentException("해당 일자를 찾을 수 없습니다. id=" + dayId));

        if (!day.getTravelSchedule().getId().equals(scheduleId)) {
            throw new IllegalArgumentException("일정과 일자 정보가 일치하지 않습니다.");
        }
        if (!day.getTravelSchedule().isOwnedBy(email)) {
            throw new IllegalStateException("본인의 일정에만 사진을 등록할 수 있습니다.");
        }

        String imageUrl = fileStorageService.store(file);
        try {
            Photo photo = Photo.builder()
                    .travelSchedule(day.getTravelSchedule())
                    .scheduleDay(day)
                    .imageUrl(imageUrl)
                    .originalFileName(file.getOriginalFilename())
                    .caption(caption)
                    .build();
            return photoRepository.save(photo).getId();
        } catch (RuntimeException e) {
            // DB 저장 실패 시 이미 디스크에 쓰인 파일을 정리 (고아 파일 방지)
            fileStorageService.delete(imageUrl);
            throw e;
        }
    }

    // 특정 일자의 사진 목록
    public List<Photo> getPhotosForDay(Long dayId) {
        return photoRepository.findByScheduleDayIdOrderByIdAsc(dayId);
    }

    // 일정 전체 사진 목록 (일자순)
    public List<Photo> getPhotosForSchedule(Long scheduleId) {
        return photoRepository.findByTravelScheduleIdWithDay(scheduleId);
    }

    // 사진 삭제 (DB 행 삭제 후, 트랜잭션 커밋 뒤 별도로 실제 파일을 삭제해야 하므로
    // 삭제 대상 imageUrl을 반환 - 호출자가 커밋 후 fileStorageService.delete를 실행)
    @Transactional
    public String deletePhoto(Long photoId, String email) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new IllegalArgumentException("해당 사진을 찾을 수 없습니다. id=" + photoId));

        if (!photo.getTravelSchedule().isOwnedBy(email)) {
            throw new IllegalStateException("본인의 사진만 삭제할 수 있습니다.");
        }
        String imageUrl = photo.getImageUrl();
        photoRepository.delete(photo);
        return imageUrl;
    }
}
