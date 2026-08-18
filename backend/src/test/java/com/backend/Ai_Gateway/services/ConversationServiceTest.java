package com.backend.Ai_Gateway.services;

import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
import com.backend.Ai_Gateway.repositories.ConversationRepository;
import com.backend.Ai_Gateway.repositories.MessageRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock ConversationRepository conversationRepository;
    @Mock UserRepository userRepository;
    @Mock MessageRepository messageRepository;

    private ConversationService service;
    private User owner;

    @BeforeEach
    void setUp() {
        service = new ConversationService(conversationRepository, userRepository, messageRepository);
        owner = new User();
        owner.setId(1L);
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
    }

    private Conversation activeConversation() {
        Conversation c = new Conversation();
        c.setId(10L);
        c.setUser(owner);
        return c;
    }

    private Conversation trashedConversation() {
        Conversation c = activeConversation();
        c.setDeletedAt(Instant.now().minusSeconds(3600));
        return c;
    }

    @Test
    void deleteConversation_firstCall_softDeletesOnly() {
        Conversation conversation = activeConversation();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteConversation(1L, 10L);

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
        verify(conversationRepository, never()).deleteById(anyLong());
        verify(messageRepository, never()).deleteByConversation_Id(anyLong());
    }

    @Test
    void deleteConversation_secondCallOnAlreadyTrashed_hardDeletes() {
        Conversation conversation = trashedConversation();
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));

        service.deleteConversation(1L, 10L);

        verify(messageRepository).deleteByConversation_Id(10L);
        verify(conversationRepository).deleteById(10L);
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void deleteConversation_nonOwner_throwsAndDeletesNothing() {
        Conversation conversation = activeConversation();
        User other = new User();
        other.setId(2L);
        conversation.setUser(other);
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));

        assertThrows(UnauthorizedException.class, () -> service.deleteConversation(1L, 10L));

        verify(conversationRepository, never()).save(any(Conversation.class));
        verify(conversationRepository, never()).deleteById(anyLong());
        verify(messageRepository, never()).deleteByConversation_Id(anyLong());
    }

    @Test
    void purgeExpiredConversations_hardDeletesEachExpiredOne() {
        Conversation a = trashedConversation();
        a.setId(10L);
        Conversation b = trashedConversation();
        b.setId(11L);
        when(conversationRepository.findByDeletedAtIsNotNullAndDeletedAtBefore(any()))
                .thenReturn(List.of(a, b));

        int purged = service.purgeExpiredConversations(30);

        assertThat(purged).isEqualTo(2);
        verify(messageRepository).deleteByConversation_Id(10L);
        verify(conversationRepository).deleteById(10L);
        verify(messageRepository).deleteByConversation_Id(11L);
        verify(conversationRepository).deleteById(11L);
    }

    @Test
    void getConversationsByUserId_excludesTrashed() {
        service.getConversationsByUserId(1L);
        verify(conversationRepository).findByUser_IdAndDeletedAtIsNullOrderByUpdatedAtDesc(1L);
    }
}
