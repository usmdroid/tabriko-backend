package uz.tabriko.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uz.tabriko.common.exception.ApiException;
import uz.tabriko.common.response.BaseResponse;
import uz.tabriko.dto.response.CreatorResponse;
import uz.tabriko.service.CatalogService;
import uz.tabriko.service.OccasionService;
import uz.tabriko.service.PromotionService;
import uz.tabriko.service.RequisiteService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogControllerTest {

    @Mock CatalogService catalogService;
    @Mock OccasionService occasionService;
    @Mock PromotionService promotionService;
    @Mock RequisiteService requisiteService;

    @InjectMocks CatalogController controller;

    @Test
    void getTrendingCreators_returns200WithList() {
        List<CreatorResponse> data = List.of(new CreatorResponse(), new CreatorResponse());
        when(catalogService.getTrendingCreators(10)).thenReturn(data);

        ResponseEntity<BaseResponse<?>> response = controller.getTrendingCreators(10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(data);
    }

    @Test
    void getSimilarCreators_returns200WithList() {
        UUID id = UUID.randomUUID();
        List<CreatorResponse> data = List.of(new CreatorResponse());
        when(catalogService.getSimilarCreators(id, 10)).thenReturn(data);

        ResponseEntity<BaseResponse<?>> response = controller.getSimilarCreators(id, 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(data);
    }

    @Test
    void getSimilarCreators_notFoundCreator_propagatesApiException() {
        UUID unknownId = UUID.randomUUID();
        when(catalogService.getSimilarCreators(unknownId, 10))
                .thenThrow(ApiException.notFound("Creator not found"));

        assertThatThrownBy(() -> controller.getSimilarCreators(unknownId, 10))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus().value()).isEqualTo(404));
    }
}
