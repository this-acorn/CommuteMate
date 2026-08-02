package project.group1.commutemate.controller;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;

import project.group1.commutemate.User.CurrentUserService;
import project.group1.commutemate.model.Profile;
import project.group1.commutemate.service.NotificationService;

/**
 * Base class for pages behind the login wall (Epic 1).
 *
 * <p>Exposes the signed-in member as a {@code @ModelAttribute} so the shared
 * navigation fragment can render their name, email, and role-appropriate tabs.
 * Points and eco-score stay at their starting values until Epic 4 lands.</p>
 */
public abstract class AuthenticatedController {

    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;

    protected AuthenticatedController(CurrentUserService currentUserService,
                                      NotificationService notificationService) {
        this.currentUserService = currentUserService;
        this.notificationService = notificationService;
    }

    @ModelAttribute("authenticated")
    public boolean authenticated() {
        return true;
    }

    @InitBinder("profile")
    public void disableProfileBinding(WebDataBinder binder) {
        binder.setAllowedFields();
    }

    protected final Profile requireCurrentProfile() {
        return currentUserService.currentProfile()
                .orElseThrow(() ->
                        new IllegalStateException("No signed-in member on a protected page"));
    }

    @ModelAttribute("profile")
    public Profile currentProfile() {
        return requireCurrentProfile();
    }

    // Epic 5: available on every authenticated page via the shared nav
    // fragment, so the bell icon's unread badge doesn't need every
    // controller to remember to add it individually.
    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount() {
        return notificationService.unreadCountFor(currentProfile().getEmail());
    }
}