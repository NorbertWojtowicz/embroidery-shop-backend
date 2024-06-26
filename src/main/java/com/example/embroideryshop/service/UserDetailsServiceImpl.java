package com.example.embroideryshop.service;

import com.example.embroideryshop.exception.EmailInUseException;
import com.example.embroideryshop.exception.NotValidEmailFormatException;
import com.example.embroideryshop.exception.UserNotLoggedInException;
import com.example.embroideryshop.model.Role;
import com.example.embroideryshop.model.User;
import com.example.embroideryshop.model.UserDetailsImpl;
import com.example.embroideryshop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    @Override
    @Cacheable(cacheNames = "UserByUsername", key = "#username")
    public UserDetails loadUserByUsername(String username) {
        User user = userRepository.findUserByEmail(username);

        if (user == null) {
            throw new UsernameNotFoundException("Użytkownik '" + username + "' nie istnieje");
        }
        return new UserDetailsImpl(user);
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public boolean userExists(String email) {
        return userRepository.findUserByEmail(email) != null;
    }

    @Transactional(rollbackFor = Exception.class)
    public String signup(User user) {
        if (!isEmailValid(user.getEmail())) {
            throw new NotValidEmailFormatException();
        }
        if (userExists(user.getEmail())) {
            throw new EmailInUseException(user.getEmail());
        }
        Role userRole = roleService.getRoleByName("USER");
        user.setRoles(Set.of(userRole));
        user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
        return String.valueOf(save(user).getId());
    }

    private boolean isEmailValid(String email) {
        return email.matches(".*[@].*[.].*");
    }

    @Cacheable(cacheNames = "LoggedUser", key = "#auth")
    public User loadLoggedUser(Authentication auth) {
        if (auth == null) {
            throw new UserNotLoggedInException();
        }
        User user = userRepository.findUserByEmail(auth.getPrincipal().toString());

        if (user == null) {
            throw new UsernameNotFoundException("Użytkownik '" + auth.getPrincipal().toString() + "' nie istnieje");
        }

        return user;
    }

}