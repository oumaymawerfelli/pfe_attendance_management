
package com.example.pfe.Service;

import com.example.pfe.dto.UserRequestDTO;
import com.example.pfe.dto.UserResponseDTO;
import java.util.List;

public interface IUserService {
    UserResponseDTO createUser(UserRequestDTO userDTO);
    List<UserResponseDTO> getAllUsers();
    UserResponseDTO getUserById(Long id);
    UserResponseDTO updateUser(Long id, UserRequestDTO userDTO);

}

