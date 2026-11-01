package com.onlineexamsystem.onlineexamsystem.student;

// A row of the attempts table: one student's attempt at one exam
public class Attempt {

    private Integer id;
    private Integer user;
    private Integer exam;
    private Integer score;
    private boolean open;

    public Attempt(Integer id, Integer user, Integer exam, Integer score, boolean open) {
        this.id = id;
        this.user = user;
        this.exam = exam;
        this.score = score;
        this.open = open;
    }

    public Integer getId() {
        return id;
    }

    public Integer getUser() {
        return user;
    }

    public Integer getExam() {
        return exam;
    }

    public Integer getScore() {
        return score;
    }

    public boolean getOpen() {
        return open;
    }
}
