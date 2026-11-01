package com.onlineexamsystem.onlineexamsystem.student;

// A row of the attempt_question table: one question picked for an attempt, and the student's answer.
// id is the attempt's id.
public class AttemptQuestion {

    private Integer id;
    private Integer question;
    private String chosen;

    public AttemptQuestion(Integer id, Integer question, String chosen) {
        this.id = id;
        this.question = question;
        this.chosen = chosen;
    }

    public Integer getId() {
        return id;
    }

    public Integer getQuestion() {
        return question;
    }

    public String getChosen() {
        return chosen;
    }
}
