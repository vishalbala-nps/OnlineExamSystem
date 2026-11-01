package com.onlineexamsystem.onlineexamsystem.admin;

public class Question {

    private Integer id;
    private String text;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String correct;

    public Question(Integer id, String text, String optionA, String optionB, String optionC, String optionD, String correct) {
        this.id = id;
        this.text = text;
        this.optionA = optionA;
        this.optionB = optionB;
        this.optionC = optionC;
        this.optionD = optionD;
        this.correct = correct;
    }

    public Integer getId() {
        return id;
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

}
