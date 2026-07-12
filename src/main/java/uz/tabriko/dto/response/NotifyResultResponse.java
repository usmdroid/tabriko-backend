package uz.tabriko.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of an admin targeted push notification.
 *
 * <p>{@code targeted} is how many devices were selected; {@code delivered} is
 * how many pushes were handed to FCM without error; {@code failed} counts
 * devices whose token was dead/blank (these are pruned). The in-app
 * notification is always created regardless of device count, so an admin can
 * see a user was notified even when they have no live device.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotifyResultResponse {
    private int targeted;
    private int delivered;
    private int failed;
}
