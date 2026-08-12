package com.rivermh.soratrip.domain.photo.controller;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.rivermh.soratrip.domain.photo.entity.Photo;
import com.rivermh.soratrip.domain.photo.service.PhotoService;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.service.TravelScheduleService;
import com.rivermh.soratrip.global.file.FileStorageService;
import com.rivermh.soratrip.global.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/schedules/{scheduleId}")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;
    private final FileStorageService fileStorageService;
    private final TravelScheduleService travelScheduleService;

    // 사진 업로드
    @PostMapping("/days/{dayId}/photos")
    public String uploadPhoto(@PathVariable("scheduleId") Long scheduleId,
                              @PathVariable("dayId") Long dayId,
                              @RequestParam("file") MultipartFile file,
                              @RequestParam(name = "caption", required = false) String caption,
                              Principal principal) {
        photoService.uploadPhoto(scheduleId, dayId, file, caption, principal.getName());
        return "redirect:/schedules/" + scheduleId + "/photos";
    }

    // 사진 캡션 수정
    @PostMapping("/days/{dayId}/photos/{photoId}/caption")
    public String updateCaption(@PathVariable("scheduleId") Long scheduleId,
                                @PathVariable("dayId") Long dayId,
                                @PathVariable("photoId") Long photoId,
                                @RequestParam(name = "caption", required = false) String caption,
                                Principal principal) {
        photoService.updateCaption(photoId, caption, principal.getName());
        return "redirect:/schedules/" + scheduleId + "/photos";
    }

    // 사진 삭제 (DB 삭제 트랜잭션 커밋 후, 실제 파일을 디스크에서 삭제)
    @PostMapping("/days/{dayId}/photos/{photoId}/delete")
    public String deletePhoto(@PathVariable("scheduleId") Long scheduleId,
                              @PathVariable("dayId") Long dayId,
                              @PathVariable("photoId") Long photoId,
                              Principal principal) {
        String imageUrl = photoService.deletePhoto(photoId, principal.getName());
        fileStorageService.delete(imageUrl);
        return "redirect:/schedules/" + scheduleId + "/photos";
    }

    // 일정 전체 사진 갤러리 (일자별로 묶어서 표시, 소유자만 업로드/삭제 가능)
    @GetMapping("/photos")
    public String scheduleGallery(@PathVariable("scheduleId") Long scheduleId,
                                  @AuthenticationPrincipal Object principal,
                                  Model model) {
        TravelSchedule schedule = travelScheduleService.getScheduleDetail(scheduleId);

        String email = principal != null ? SecurityUtils.extractEmail(principal) : null;
        boolean owner = schedule.isOwnedBy(email);

        if (!schedule.isPublic() && !owner) {
            return "redirect:/schedules";
        }

        List<Photo> photos = photoService.getPhotosForSchedule(scheduleId);

        model.addAttribute("schedule", schedule);
        model.addAttribute("owner", owner);
        model.addAttribute("photosByDay", photos.stream()
                .collect(Collectors.groupingBy(p -> p.getScheduleDay().getId())));
        return "photo/gallery";
    }
}
