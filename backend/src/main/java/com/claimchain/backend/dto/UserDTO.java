package com.claimchain.backend.dto;

import com.claimchain.backend.model.Role;
import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String name;
    private String email;
    private Role role;
}
