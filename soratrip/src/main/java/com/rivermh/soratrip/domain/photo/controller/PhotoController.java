package com.rivermh.soratrip.domain.photo.controller;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.rivermh.soratrip.domain.photo.service.PhotoService;
import com.rivermh.soratrip.global.file.FileStorageService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/schedules/{scheduleId}")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;
    private final FileStorageService fileStorageService;

    // 사진 업로드
    @PostMapping("/days/{dayId}/photos")
    public String uploadPhoto(@PathVariable("scheduleId") Long scheduleId,
                              @PathVariable("dayId") Long dayId,
                              @RequestParam("file") MultipartFile file,
                              @RequestParam(name = "caption", required = false) String caption,
                              Principal principal) {
        photoService.uploadPhoto(scheduleId, dayId, file, caption, principal.getName());
        return "redirect:/schedules/" + scheduleId;
    }

    // 사진 삭제 (DB 삭제 트랜잭션 커밋 후, 실제 파일을 디스크에서 삭제)
    @PostMapping("/days/{dayId}/photos/{photoId}/delete")
    public String deletePhoto(@PathVariable("scheduleId") Long scheduleId,
                              @PathVariable("dayId") Long dayId,
                              @PathVariable("photoId") Long photoId,
                              Principal principal) {
        String imageUrl = photoService.deletePhoto(photoId, principal.getName());
        fileStorageService.delete(imageUrl);
        return "redirect:/schedules/" + scheduleId;
    }

    // 일정 전체 사진 갤러리
    @GetMapping("/photos")
    public String scheduleGallery(@PathVariable("scheduleId") Long scheduleId, Model model) {
        model.addAttribute("scheduleId", scheduleId);
        model.addAttribute("photos", photoService.getPhotosForSchedule(scheduleId));
        return "photo/gallery";
    }
}
