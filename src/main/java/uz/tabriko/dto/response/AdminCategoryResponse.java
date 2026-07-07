package uz.tabriko.dto.response;

import lombok.Data;

@Data
public class AdminCategoryResponse {
    private Long id;
    private String nameUz;
    private String nameRu;
    private String nameEn;
    private String iconUrl;
    private boolean archived;
}
