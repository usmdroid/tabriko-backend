package uz.tabriko.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.tabriko.common.response.BaseResponse;
import uz.tabriko.dto.request.ContactsBirthdayRequest;
import uz.tabriko.dto.response.BirthdayMatchResponse;
import uz.tabriko.security.UserPrincipal;
import uz.tabriko.service.ContactsService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me/contacts")
@RequiredArgsConstructor
@Tag(name = "Contacts")
public class ContactsController {

    private final ContactsService contactsService;

    @PostMapping("/birthdays")
    @Operation(summary = "Find contacts whose birthdays are known (phone-hash matching)")
    public ResponseEntity<BaseResponse<List<BirthdayMatchResponse>>> findBirthdayMatches(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ContactsBirthdayRequest req
    ) {
        List<BirthdayMatchResponse> result = contactsService.findBirthdayMatches(
                principal.getUserId(), req.getHashes());
        return ResponseEntity.ok(BaseResponse.ok(result));
    }
}
