package com.jhe.question_bank.common.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity(name="universities")
@Table(name="universities")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UniversityEntity {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Integer universityId;
    private String universityName;
}
