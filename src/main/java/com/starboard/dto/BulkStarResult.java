package com.starboard.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BulkStarResult {
    private int success;   // số repo star/unstar thành công
    private int failed;    // số repo bị lỗi
    private int skipped;   // số repo bỏ qua (đã star rồi / chưa star)
    private int creditsChanged; // tổng credit thay đổi
}
