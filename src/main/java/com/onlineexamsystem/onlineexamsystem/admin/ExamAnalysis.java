package com.onlineexamsystem.onlineexamsystem.admin;

import java.util.List;

// Summary of how students did on an exam. The score fields are null until someone submits.
public class ExamAnalysis {

    private Integer examId;
    private String title;
    private Integer numQuestions;
    private Integer submitted;
    private Integer inProgress;
    private Double averageScore;
    private Integer highestScore;
    private Integer lowestScore;
    private List<QuestionStat> questions;

    public ExamAnalysis(Integer examId, String title, Integer numQuestions, Integer submitted, Integer inProgress,
                        Double averageScore, Integer highestScore, Integer lowestScore, List<QuestionStat> questions) {
        this.examId = examId;
        this.title = title;
        this.numQuestions = numQuestions;
        this.submitted = submitted;
        this.inProgress = inProgress;
        this.averageScore = averageScore;
        this.highestScore = highestScore;
        this.lowestScore = lowestScore;
        this.questions = questions;
    }

    public Integer getExamId() {
        return examId;
    }

    public String getTitle() {
        return title;
    }

    public Integer getNumQuestions() {
        return numQuestions;
    }

    public Integer getSubmitted() {
        return submitted;
    }

    public Integer getInProgress() {
        return inProgress;
    }

    public Double getAverageScore() {
        return averageScore;
    }

    public Integer getHighestScore() {
        return highestScore;
    }

    public Integer getLowestScore() {
        return lowestScore;
    }

    public List<QuestionStat> getQuestions() {
        return questions;
    }
}
