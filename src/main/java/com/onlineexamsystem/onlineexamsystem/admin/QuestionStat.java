package com.onlineexamsystem.onlineexamsystem.admin;

// How students did on one question across the submitted attempts at an exam
public class QuestionStat {

    private Integer questionId;
    private String text;
    private String correct;
    private Integer asked;
    private Integer correctCount;
    private Integer choseA;
    private Integer choseB;
    private Integer choseC;
    private Integer choseD;
    private Integer unanswered;

    public QuestionStat(Integer questionId, String text, String correct, Integer asked, Integer correctCount,
                        Integer choseA, Integer choseB, Integer choseC, Integer choseD, Integer unanswered) {
        this.questionId = questionId;
        this.text = text;
        this.correct = correct;
        this.asked = asked;
        this.correctCount = correctCount;
        this.choseA = choseA;
        this.choseB = choseB;
        this.choseC = choseC;
        this.choseD = choseD;
        this.unanswered = unanswered;
    }

    public Integer getQuestionId() {
        return questionId;
    }

    public String getText() {
        return text;
    }

    public String getCorrect() {
        return correct;
    }

    public Integer getAsked() {
        return asked;
    }

    public Integer getCorrectCount() {
        return correctCount;
    }

    public Integer getChoseA() {
        return choseA;
    }

    public Integer getChoseB() {
        return choseB;
    }

    public Integer getChoseC() {
        return choseC;
    }

    public Integer getChoseD() {
        return choseD;
    }

    public Integer getUnanswered() {
        return unanswered;
    }
}
