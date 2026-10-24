package com.jhe.question_bank.service.implement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import com.jhe.question_bank.service.FileService;

@Service
public class FileServiceImplement implements FileService {

    @Value("${file.path}")
    private String filePath;
    @Value("${mock-question.folder-name}")
    private String mockQuestionFolderName;
    @Value("${past-question.folder-name}")
    private String pastQuestionFolderName;

    @Value("${explanation.folder-name}")
    private String explanationFolderName;

    @Override
    public Resource getImageFile(String questionType, String fileName) {

        Resource resource = null;

        try {
            
            String questionFolderName;


            if (questionType.equals("모의고사")) questionFolderName = mockQuestionFolderName;
            else if (questionType.equals("기출문제")) questionFolderName = pastQuestionFolderName;
            else if (questionType.equals("explanation")) questionFolderName = explanationFolderName;
            else return null;

            java.nio.file.Path folder = java.nio.file.Path.of(filePath).resolve(questionFolderName).toAbsolutePath().normalize();
            java.nio.file.Path image = folder.resolve(fileName).normalize();
            if (!image.startsWith(folder) || image.equals(folder)) return null;
            resource = new UrlResource(image.toUri());

        } catch (Exception exception) {
            exception.printStackTrace();
            return null;
        }

        return resource;
    }
    
}
