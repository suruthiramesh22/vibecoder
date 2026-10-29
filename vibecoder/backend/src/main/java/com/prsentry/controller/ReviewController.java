package com.prsentry.controller;

import com.prsentry.model.ReviewRequest;
import com.prsentry.model.ReviewResponse;
import com.prsentry.service.CodeReviewService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final CodeReviewService codeReviewService;

    public ReviewController(CodeReviewService codeReviewService) {
        this.codeReviewService = codeReviewService;
    }

    @PostMapping("/review")
    public ReviewResponse review(@RequestBody ReviewRequest request) {
        return codeReviewService.analyze(request.getFileName(), request.getCode());
    }
}
