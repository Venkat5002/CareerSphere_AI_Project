package com.careersphere.dto;

import com.careersphere.entity.Role;
import com.careersphere.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request/response shapes for registration and login. */
public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "Name is required") @Size(max = 100) String fullName,
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") String email,
            @NotBlank(message = "Password is required") @Size(min = 6, max = 72, message = "Password must be 6-72 characters") String password,
            @NotNull(message = "Choose Student or Recruiter") Role role) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required") String email,
            @NotBlank(message = "Password is required") String password) {}

    public record UserDto(Long id, String fullName, String email, Role role) {
        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getRole());
        }
    }

    public record AuthResponse(String token, UserDto user) {}
}
