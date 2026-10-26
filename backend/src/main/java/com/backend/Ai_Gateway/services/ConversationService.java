package com.backend.Ai_Gateway.services;


import com.backend.Ai_Gateway.exceptions.RessourceNotFoundException;
import com.backend.Ai_Gateway.exceptions.UnauthorizedException;
import com.backend.Ai_Gateway.entity.Conversation;
import com.backend.Ai_Gateway.entity.User;
import com.backend.Ai_Gateway.repositories.ConversationRepository;
import com.backend.Ai_Gateway.repositories.MessageRepository;
import com.backend.Ai_Gateway.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    public ConversationService(ConversationRepository conversationRepository, UserRepository userRepository, MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
    }

    public Conversation createConversation(Long userId){
        Optional<User> user= userRepository.findById(userId);
        if(user.isPresent()){
            Conversation conversation=new Conversation();
            conversation.setUser(user.get());
            conversation.setTitle("Nouvelle Conversation");
            conversation.setCreatedAt(Instant.now());
            conversation.setUpdatedAt(Instant.now());
            conversationRepository.save(conversation);
            return conversation;
        }
        throw new UnauthorizedException("Ce User est introuvable");
    }

    public List<Conversation> getConversationsByUserId(Long userId){
        userRepository.findById(userId)
                .orElseThrow(()-> new RessourceNotFoundException("User not found"));
        return conversationRepository.findByUser_IdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId);
    }


    public Conversation renameConversation(Long userConnected, Long conversationId, String title){
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title: must not be blank");
        }
        if(verifyProprty(userConnected, conversationId)){
            Conversation conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new RessourceNotFoundException("Conversation not found"));
            conversation.setTitle(title);
            conversation.setUpdatedAt(Instant.now());
            return conversationRepository.save(conversation);
        }
        throw new UnauthorizedException("Vous n'êtes pas propriétaire de cette conversation");
    }

    /**
     * User-facing behavior is a plain, immediate-looking delete — the conversation disappears
     * from their list right away. Under the hood it's soft-deleted first (kept for
     * {@link com.backend.Ai_Gateway.config.ConversationRetentionProperties#getRetentionDays()}
     * days as a safety net, purged by {@link ConversationRetentionJob}) with no restore path
     * exposed anywhere — this is deliberately NOT a user-facing trash/undo feature.
     */
    @Transactional
    public void deleteConversation(Long userConnected,Long idConversation){
        if(!verifyProprty(userConnected,idConversation)){
            throw new UnauthorizedException("Vous n'êtes pas propriétaire de cette conversation");
        }
        Conversation conversation = conversationRepository.findById(idConversation)
                .orElseThrow(() -> new RessourceNotFoundException("Conversation not found"));
        if (conversation.getDeletedAt() == null) {
            conversation.setDeletedAt(Instant.now());
            conversationRepository.save(conversation);
        } else {
            hardDelete(idConversation);
        }
    }

    /** Called by {@link ConversationRetentionJob}. @return number of conversations purged. */
    @Transactional
    public int purgeExpiredConversations(int retentionDays){
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        List<Conversation> expired = conversationRepository.findByDeletedAtIsNotNullAndDeletedAtBefore(cutoff);
        for (Conversation conversation : expired) {
            hardDelete(conversation.getId());
        }
        return expired.size();
    }

    private void hardDelete(Long conversationId){
        messageRepository.deleteByConversation_Id(conversationId);
        conversationRepository.deleteById(conversationId);
    }

    public boolean verifyProprty(Long userConnected, Long conversationId){
        Conversation conversation= conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RessourceNotFoundException("Conversation not found"));
        userRepository.findById(userConnected)
                .orElseThrow(()-> new RessourceNotFoundException("user not found"));
        return conversation.getUser() != null
                && conversation.getUser().getId().equals(userConnected);
    }
}
