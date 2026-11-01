package com.onlineexamsystem.onlineexamsystem.admin;

// A student account in the users table (role = 'STUDENT').
// password is the plain password sent by the admin; it is hashed before being stored and never sent back.
public class Student {

    private Integer id;
    private String username;
    private String password;

    public Student(Integer id, String username, String password) {
        this.id = id;
        this.username = username;
        this.password = password;
    }

    public Integer getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
