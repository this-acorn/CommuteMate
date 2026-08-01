package project.group1.commutemate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import project.group1.commutemate.model.Notification;
import project.group1.commutemate.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository);
    }

    @Test
    void notify_savesWithLowercasedEmail() {
        service.notify("Rider@SFU.ca", "Test message", 5L);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notify_doesNothing_whenRecipientEmailIsBlank() {
        service.notify("", "Test message", 5L);
        service.notify(null, "Test message", 5L);

        verify(notificationRepository, times(0)).save(any());
    }

    @Test
    void findForUser_returnsEmptyList_whenEmailIsBlank() {
        assertTrue(service.findForUser("").isEmpty());
        assertTrue(service.findForUser(null).isEmpty());
    }

    @Test
    void unreadCountFor_delegatesToRepository() {
        when(notificationRepository.countByRecipientEmailIgnoreCaseAndReadFalse("rider@sfu.ca"))
                .thenReturn(4L);

        assertEquals(4L, service.unreadCountFor("rider@sfu.ca"));
    }

    @Test
    void unreadCountFor_returnsZero_whenEmailIsBlank() {
        assertEquals(0, service.unreadCountFor(""));
        assertEquals(0, service.unreadCountFor(null));
    }

    @Test
    void markAllRead_onlySavesPreviouslyUnreadNotifications() {
        Notification unread = new Notification("rider@sfu.ca", "Unread", 1L);
        Notification alreadyRead = new Notification("rider@sfu.ca", "Already read", 2L);
        alreadyRead.setRead(true);

        when(notificationRepository.findByRecipientEmailIgnoreCaseOrderByCreatedAtDesc("rider@sfu.ca"))
                .thenReturn(List.of(unread, alreadyRead));

        service.markAllRead("rider@sfu.ca");

        assertTrue(unread.isRead());
        verify(notificationRepository).saveAll(List.of(unread));
    }

    @Test
    void markRead_marksAndReturnsNotification_whenOwnedByCaller() {
        Notification notification = new Notification("rider@sfu.ca", "Test", 7L);
        when(notificationRepository.findById(1L)).thenReturn(java.util.Optional.of(notification));

        var result = service.markRead(1L, "rider@sfu.ca");

        assertTrue(result.isPresent());
        assertTrue(notification.isRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markRead_doesNothing_whenNotificationBelongsToSomeoneElse() {
        Notification notification = new Notification("someone-else@sfu.ca", "Test", 7L);
        when(notificationRepository.findById(1L)).thenReturn(java.util.Optional.of(notification));

        var result = service.markRead(1L, "rider@sfu.ca");

        assertTrue(result.isEmpty());
        assertTrue(!notification.isRead());
        verify(notificationRepository, times(0)).save(any());
    }

    @Test
    void markRead_returnsEmpty_whenNotificationDoesNotExist() {
        when(notificationRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        var result = service.markRead(99L, "rider@sfu.ca");

        assertTrue(result.isEmpty());
    }
}