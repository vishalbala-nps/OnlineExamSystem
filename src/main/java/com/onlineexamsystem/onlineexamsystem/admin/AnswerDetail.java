package com.onlineexamsystem.onlineexamsystem.admin;

// One question in a student's attempt: the question, the correct answer and what the student chose (null if skipped)
public class AnswerDetail {

    private Integer questionId;
    private String text;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String correct;
    private String chosen;

    public AnswerDetail(Integer questionId, String text, String optionA, String optionB, String optionC, String optionD,
                        String correct, String chosen) {
        this.questionId = questionId;
        this.text = text;
        this.optionA = optionA;
        this.optionB = optionB;
        this.optionC = optionC;
        this.optionD = optionD;
        this.correct = correct;
        this.chosen = chosen;
    }

    public Integer getQuestionId() {
        return questionId;
    }

    public String getText() {
        return text;
    }

    public String getOptionA() {
        return optionA;
    }

    public String getOptionB() {
        return optionB;
    }

    public String getOptionC() {
        return optionC;
    }

    public String getOptionD() {
        return optionD;
    }

    public String getCorrect() {
        return correct;
    }

    public String getChosen() {
        return chosen;
    }
}
