package com.onlineexamsystem.onlineexamsystem.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

// Loads a user from the users table for the login check
@Service
public class DbUserDetailsService implements UserDetailsService {
    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public UserDetails loadUserByUsername(String username) {
        List<UserDetails> users = jdbc.query(
                "SELECT username, password, role FROM users WHERE username = ?",
                (rs, rowNum) -> User.withUsername(rs.getString("username"))
                        .password(rs.getString("password"))
                        .roles(rs.getString("role"))
                        .build(),
                username);

        if (users.isEmpty()) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        return users.get(0);
    }
}
