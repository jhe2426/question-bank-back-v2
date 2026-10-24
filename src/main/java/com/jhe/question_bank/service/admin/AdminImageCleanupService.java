package com.jhe.question_bank.service.admin;

import com.jhe.question_bank.repository.DetailedExplanationRepository;
import com.jhe.question_bank.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminImageCleanupService {
    private final QuestionRepository questionRepository;
    private final DetailedExplanationRepository explanationRepository;
    private final AdminImageStorageService imageStorage;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void deleteIfUnreferenced(String url) {
        if (!questionRepository.existsByImageUrl(url) && !explanationRepository.existsByImageUrl(url)) {
            imageStorage.storageKeyFromUrl(url).ifPresent(imageStorage::delete);
        }
    }
}
