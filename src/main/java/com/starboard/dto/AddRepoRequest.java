package com.starboard.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddRepoRequest {
    @NotBlank(message = "Tên repo không được để trống")
    private String fullName; // format: "owner/repo"

    private String description; // mô tả tùy chọn, override description từ GitHub
}
