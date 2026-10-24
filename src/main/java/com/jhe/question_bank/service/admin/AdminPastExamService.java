package com.jhe.question_bank.service.admin;

import com.jhe.question_bank.common.entity.PastExamEntity;
import com.jhe.question_bank.repository.PastExamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPastExamService {
    private final PastExamRepository pastExamRepository;

    @Transactional
    public Integer getOrCreatePastExamId(Integer round) {
        return pastExamRepository.findForUpdateByRound(round)
                .map(PastExamEntity::getPastExamId)
                .orElseGet(() -> pastExamRepository.save(new PastExamEntity(round)).getPastExamId());
    }
}
