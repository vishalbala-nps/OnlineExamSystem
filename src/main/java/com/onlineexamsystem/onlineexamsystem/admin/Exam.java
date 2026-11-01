package com.onlineexamsystem.onlineexamsystem.admin;
public class Exam {
    private Integer id;
    private String title;
    private Integer numQuestions;

    public Exam(Integer id, String title, Integer numQuestions) {
            this.id = id;
            this.title = title;
            this.numQuestions = numQuestions;
    }

    public Integer getId() {
            return id;
    }

    public String getTitle() {
            return title;
    }

    public Integer getNumQuestions() {
            return numQuestions;
    }
}
