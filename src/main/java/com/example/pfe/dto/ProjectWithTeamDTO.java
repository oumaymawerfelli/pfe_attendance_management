package com.example.pfe.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectWithTeamDTO extends ProjectResponseDTO {
    private List<TeamMemberDTO> teamMembers;
}
