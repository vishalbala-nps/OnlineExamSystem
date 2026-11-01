package com.onlineexamsystem.onlineexamsystem.admin;

// One student's attempt at one exam, as shown to the admin.
// score is null while the attempt is still open; total is how many questions were picked for it.
public class Result {

    private Integer attemptId;
    private String username;
    private Integer examId;
    private String examTitle;
    private Integer score;
    private Integer total;
    private boolean open;

    public Result(Integer attemptId, String username, Integer examId, String examTitle, Integer score, Integer total, boolean open) {
        this.attemptId = attemptId;
        this.username = username;
        this.examId = examId;
        this.examTitle = examTitle;
        this.score = score;
        this.total = total;
        this.open = open;
    }

    public Integer getAttemptId() {
        return attemptId;
    }

    public String getUsername() {
        return username;
    }

    public Integer getExamId() {
        return examId;
    }

    public String getExamTitle() {
        return examTitle;
    }

    public Integer getScore() {
        return score;
    }

    public Integer getTotal() {
        return total;
    }

    public boolean getOpen() {
        return open;
    }
}
